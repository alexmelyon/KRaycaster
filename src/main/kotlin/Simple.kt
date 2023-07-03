import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.Image
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.math.pow
import kotlin.random.Random

val CIRCLE = kotlin.math.PI * 2;
val MOBILE = window.navigator.userAgent.matches("Android|webOS|iPhone|iPad|iPod|BlackBerry".toRegex())
val PI = kotlin.math.PI

fun main() {
    println("Hello, world")

    val display = document.getElementById("display") as HTMLCanvasElement
    val player = Player(15.3, -1.2, PI * 0.3)
    println("PLAYER $player")
    val map = MapField(32)
    val controls = Controls()
    val camera = Camera(display, if (MOBILE) 160 else 320, 0.8)
    val loop = GameLoop()

    map.randomize()

    loop.start { seconds ->
//        println("LOOP $seconds")
        map.update(seconds)
        player.update(controls.states, map, seconds)
        camera.render(player, map)
    }
    println("END")
}

class Controls {
    val codes = mapOf(
        37 to "left",
        39 to "right",
        38 to "forward",
        40 to "backward"
    )
    val states = mutableMapOf(
        "left" to false,
        "right" to false,
        "forward" to false,
        "backward" to false
    )

    init {
        document.addEventListener("keydown", { e -> onKey(true, e) }, false)
        document.addEventListener("keyup", { e -> onKey(false, e) }, false)
    }

    fun onKey(isDown: Boolean, e: Event) {
        e as KeyboardEvent
        val state = codes[e.keyCode]
        state ?: return
        states[state] = isDown
        e.preventDefault()
        e.stopPropagation()
    }
}

class Bitmap(src: String, val width: Int, val height: Int) {

    val image = Image()

    init {
        image.src = src
    }
}

data class Player(var x: Double, var y: Double, var direction: Double) {

    val weapon = Bitmap("knife_hand.png", 319, 320)
    var paces: Double = 0.0

    fun rotate(angle: Double) {
        direction = (direction + angle + CIRCLE) % CIRCLE
    }

    fun walk(distance: Double, map: MapField) {
        val dx = kotlin.math.cos(direction) * distance
        val dy = kotlin.math.sin(direction) * distance
        if (map.get(x + dx, y) <= 0) {
            x += dx
        }
        if (map.get(x, y + dy) <= 0) {
            y += dy
        }
        paces += distance
    }

    fun update(controls: Map<String, Boolean>, map: MapField, seconds: Double) {
        if (controls["left"]!!) {
            rotate(-PI * seconds)
        }
        if (controls["right"]!!) {
            rotate(PI * seconds)
        }
        if (controls["forward"]!!) {
            walk(3 * seconds, map)
        }
        if (controls["backward"]!!) {
            walk(-3 * seconds, map)
        }
//        controls.entries.filter { it.value }.map { it.key }
//            .let { println(it) }
    }
}

//data class Point(val x: Double, val y: Double)
data class Origin(val x: Double, val y: Double, var height: Double, var distance: Double) {
    var length2: Double = 0.0
    var shading: Double = 0.0
    var offset: Double = 0.0
}

//data class Step(val x: Double, val y: Double, val length2: Double)
data class TopHeight(val top: Double, val height: Double)

data class MapField(val size: Int) {

    val wallGrid = Array(size * size) { 0 }
    val skybox = Bitmap("deathvalley_panorama.jpg", 2000, 750)
    val wallTexture = Bitmap("wall_texture.jpg", 1024, 1024)
    var light: Double = 0.0

    //    private val noWall = Step(0.0, 0.0, Double.MAX_VALUE)
    private val noWall = Origin(0.0, 0.0, 0.0, 0.0).apply {
        length2 = Double.MAX_VALUE
    }

    fun get(x: Double, y: Double): Int {
        val x = x.toInt()
        val y = y.toInt()
        if (x < 0 || x > size - 1 || y < 0 || y > size - 1) {
            return -1 // FIXME
        }
        return wallGrid[y * size + x]
    }

    fun randomize() {
        val r = Random.Default
        for (i in 0..(size * size)) {
            wallGrid[i] = if (r.nextDouble() < 0.3) 1 else 0
        }
    }

    fun cast(point: Player, angle: Double, range: Double): List<Origin> {
        val sin = kotlin.math.sin(angle)
        val cos = kotlin.math.cos(angle)
//        val noWall =
        val origin = Origin(point.x, point.y, 0.0, 0.0)
        return ray(sin, cos, origin, range)
    }

    private fun ray(sin: Double, cos: Double, origin: Origin, range: Double): List<Origin> {
        val stepX = step(sin, cos, origin.x, origin.y, false)
        val stepY = step(cos, sin, origin.y, origin.x, true)
        val nextStep = if (stepX.length2 < stepY.length2) {
            inspect(sin, cos, stepX, 1.0, 0.0, origin.distance, stepX.y)
        } else {
            inspect(sin, cos, stepY, 0.0, 1.0, origin.distance, stepY.x)
        }
        if (nextStep.distance > range) {
            return listOf(origin)
        }
        return listOf(origin) + ray(sin, cos, nextStep, range)
    }

    private fun step(rise: Double, run: Double, x: Double, y: Double, isInverted: Boolean): Origin {
        if (run == 0.0) {
            return noWall
        }
        val dx = if (run > 0) {
            kotlin.math.floor(x + 1) - x
        } else kotlin.math.ceil(x - 1) - x

        val dy = dx * (rise / run)
        val l2 = dx * dx + dy * dy
        if (isInverted) {
            return Origin(y + dy, x + dx, 0.0, 0.0).apply {
                length2 = l2
            }
        } else {
            return Origin(x + dx, y + dy, 0.0, 0.0).apply {
                length2 = l2
            }
        }
    }

    private fun inspect(sin: Double, cos: Double, step: Origin, shiftX: Double, shiftY: Double, distance: Double, offset: Double): Origin {
        val dx = if (cos < 0) {
            shiftX
        } else 0.0
        val dy = if (sin < 0) {
            shiftY
        } else 0.0
        step.height = get(step.x - dx, step.y - dy).toDouble() // FIXME
        step.distance = distance + kotlin.math.sqrt(step.length2)
        if (shiftX > 0) {
            step.shading = if (cos < 0) 2.0 else 0.0
        } else {
            step.shading = if (sin < 0) 2.0 else 1.0
        }
        step.offset = offset - kotlin.math.floor(offset)
        return step
    }

    fun update(seconds: Double) {
        val r = Random
        if (light > 0) {
            light = kotlin.math.max(light - 10 * seconds, 0.0)
        } else if (r.nextDouble() * 5 < seconds) {
            light = 2.0
        }
    }
}

class Camera(canvas: HTMLCanvasElement, resolution: Int, val focalLength: Double = 0.8) {

    val ctx: CanvasRenderingContext2D = canvas.getContext("2d") as CanvasRenderingContext2D
    val width = (window.innerWidth * 0.5)
    val height = (window.innerHeight * 0.5)
    val resolution = resolution
    val spacing = width / resolution
    val range = if (MOBILE) 8.0 else 14.0
    val lightRange = 5.0
    val scale = (width + height) / 1200

    init {
        canvas.width = width.toInt()
        canvas.height = height.toInt()
    }

    fun render(player: Player, map: MapField) {
        drawSky(player.direction, map.skybox, map.light)
        drawColumns(player, map)
        drawWeapon(player.weapon, player.paces)
    }

    fun drawSky(direction: Double, sky: Bitmap, ambient: Double) {
        val width = sky.width * (height / sky.height) * 2
        val left = (direction / CIRCLE) * -width

        ctx.save()
        ctx.drawImage(sky.image, left, 0.0, width, height)
        if (left < (width - this.width)) {
            ctx.drawImage(sky.image, left + width, 0.0, width, height)
        }
        if (ambient > 0) {
            ctx.fillStyle = "#ffffff"
            ctx.globalAlpha = ambient * 0.1
            ctx.fillRect(0.0, height * 0.5, this.width, this.height * 0.5)
        }
        ctx.restore()
    }

    fun drawColumns(player: Player, map: MapField) {
        ctx.save()
        for (column in 0 until resolution) {
            val x = column / resolution.toDouble() - 0.5
            val angle = kotlin.math.atan2(x, focalLength)
            val ray = map.cast(player, player.direction + angle, range)
//            println("COLUMN=$column X=$x ANGLE=$angle RAY=${ray.map { it.distance }}")
            drawColumn(column, ray, angle, map)
        }
        ctx.restore()
    }

    fun drawWeapon(weapon: Bitmap, paces: Double) {
        val bobX = kotlin.math.cos(paces * 2) * scale * 6
        val bobY = kotlin.math.sin(paces * 4) * scale * 6
        val left = width * 0.66 + bobX
        val top = height * 0.6 + bobY
        ctx.drawImage(weapon.image, left, top, weapon.width * scale, weapon.height * scale)
    }

    fun drawColumn(column: Int, ray: List<Origin>, angle: Double, map: MapField) {
        val texture = map.wallTexture
        val left = kotlin.math.floor(column * spacing)
        val width = kotlin.math.ceil(spacing)
        var hit = -1

        while (++hit < ray.size && ray[hit].height <= 0) {
            // Nothing
        }
        val r = Random
        for (s in (ray.size - 1)downTo 0) {
            val step = ray[s]
            var rainDrops = r.nextDouble().pow(3.0) * s
            val rain = project(0.1, angle, step.distance)

            if (s == hit) {
                val textureX = kotlin.math.floor(texture.width * step.offset)
                val wall = project(step.height, angle, step.distance)

                ctx.globalAlpha = 1.0
                ctx.drawImage(texture.image, textureX, 0.0, 1.0, texture.height.toDouble(), left, wall.top, width, wall.height)

                ctx.fillStyle = "#000000"
                ctx.globalAlpha = kotlin.math.max((step.distance + step.shading) / lightRange - map.light, 0.0)
                ctx.fillRect(left, wall.top, width, wall.height)
            }

            ctx.fillStyle = "#ffffff"
            ctx.globalAlpha = 0.15
            while (--rainDrops > 0) {
                ctx.fillRect(left, r.nextDouble() * rain.top, 1.0, rain.height)
            }
        }
    }

    fun project(height: Double, angle: Double, distance: Double): TopHeight {
        val z = distance * kotlin.math.cos(angle)
        val wallHeight = this.height * height / z
        val bottom = this.height / 2 * (1 + 1 / z)
        return TopHeight(bottom - wallHeight, wallHeight)
    }
}

class GameLoop {

    var lastTime = 0.0
    var callback: ((Double) -> Unit)? = null

    fun start(function: (Double) -> Unit) {
        callback = function
        window.requestAnimationFrame(::frame)
    }

    fun frame(time: Double) {
        val seconds = (time - lastTime) / 1000
        lastTime = time
        if (seconds < 0.2) {
            callback?.invoke(seconds)
        }
        window.requestAnimationFrame(::frame)
    }
}
