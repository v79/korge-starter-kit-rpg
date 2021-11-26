import com.soywiz.klock.milliseconds
import com.soywiz.klock.seconds
import com.soywiz.korev.Key
import com.soywiz.korge.Korge
import com.soywiz.korge.scene.*
import com.soywiz.korge.tiled.*
import com.soywiz.korge.ui.UIText
import com.soywiz.korge.view.*
import com.soywiz.korge.view.animation.ImageDataView
import com.soywiz.korge.view.filter.TransitionFilter
import com.soywiz.korinject.AsyncInjector
import com.soywiz.korio.async.launchImmediately
import com.soywiz.korma.geom.Point
import com.soywiz.korma.geom.Rectangle
import com.soywiz.korma.geom.SizeInt
import kotlin.math.roundToInt
import kotlin.reflect.KClass

suspend fun main() = Korge(Korge.Config(module = MyModule))

/* suspend fun main() =
    Korge(width = 800, height = 600, virtualWidth = 512, virtualHeight = 512, bgcolor = Colors["#2b2b2b"]) {



    injector.mapPrototype { RpgIngameScene() }
        val rootSceneContainer = sceneContainer()

        rootSceneContainer.changeTo<RpgIngameScene>(
            transition = MaskTransition(
                transition = TransitionFilter.Transition.CIRCULAR,
                reversed = false,
                smooth = true,
                filtering = true
            ),
            //transition = AlphaTransition,
            time = 1.0.seconds
        )
    }
*/

object MyModule : Module() {
//    override val mainScene: KClass<out Scene> = MyScene1::class
    override val size: SizeInt = SizeInt(800,600)
    override val mainScene: KClass<out Scene> = RpgIngameScene::class
    override suspend fun AsyncInjector.configure() {
        mapInstance(SceneMap("BasicTilemap/untitled.tmx"))
        mapPrototype { RpgIngameScene(SceneMap("BasicTilemap/untitled.tmx")) }
        mapPrototype { DungeonScene(SceneMap("MiniTileset-Dungeon/dungeon.tmx")) }
    }
}

class SceneMap(val value: String)

data class NPC(val imageDataView: ImageDataView, val obj: TiledMap.Object)

/**
 * [char] the player
 * [collider] the tiledMapView
 * [npcs] a list of npcs which may be interacted with
 * [messageBox] text element a message can be displayed in
 */
fun Stage.controlWithKeyboard(
    char: ImageDataView,
    collider: HitTestable,
    npcs: List<NPC> = emptyList(),
    events: Map<String, TiledMap.Object>  = emptyMap(),
    messageBox: UIText,
    tiledMapView: TiledMapView,
    sceneContainer: SceneContainer,
    up: Key = Key.UP, // don't bother with these until we have user-configurable keybindings
    right: Key = Key.RIGHT,
    down: Key = Key.DOWN,
    left: Key = Key.LEFT,
) {

    addUpdater { dt ->

        // 32f being the tile grid size
        val charGridX = (((char.x + (char.width / 2f)) / 32f) - 1).roundToInt()
        val charGridY = (((char.y + (char.height / 2f)) / 32f) - 1).roundToInt() // want position at feet

        val speed = 2.0 * (dt / 16.0.milliseconds)
        var dx = 0.0
        var dy = 0.0
        // allow arrow keys or WASD for movement
        val pressingLeft = keys[Key.LEFT] || keys[Key.A]
        val pressingRight = keys[Key.RIGHT] || keys[Key.D]
        val pressingUp = keys[Key.UP] || keys[Key.W]
        val pressingDown = keys[Key.DOWN] || keys[Key.S]
        if (pressingLeft) dx = -1.0
        if (pressingRight) dx = +1.0
        if (pressingUp) dy = -1.0
        if (pressingDown) dy = +1.0
        if (dx != 0.0 || dy != 0.0) {
            val dpos = Point(dx, dy).normalized * speed
            char.moveWithHitTestable(collider, dpos.x, dpos.y)
        }
        char.animation = when {
            pressingLeft -> "left"
            pressingRight -> "right"
            pressingUp -> "up"
            pressingDown -> "down"
            else -> char.animation
        }
        if (pressingLeft || pressingRight || pressingUp || pressingDown) {
            char.play()
        } else {
            char.stop()
            char.rewind()
            messageBox.text = "[${charGridX},${charGridY}]"
        }

        // check for npc collisions
        val pressingInteract = keys[Key.E]
        if (pressingInteract) {
            for (npc in npcs) {
                if (char.collidesWith(npc.imageDataView)) {
//                    println("Collided with NPC $npc")
                    if (npc.obj.properties["greeting"] != null) {
                        messageBox.text = npc.obj.properties["greeting"].toString()
                        println("NPC says: " + npc.obj.properties["greeting"])
                    }
                }
            }

            //Rectangle(x=352, y=224, width=34, height=31.3333)
            for (event in events) {
                val charRect = Rectangle(char.x, char.y, char.width, char.height)
                val nextScenes = mapOf("dungeon" to DungeonScene::class, "untitled" to RpgIngameScene::class)
                println("charRect: $charRect")
                val eventRect = event.value.bounds
                if (charRect.intersects(eventRect)) {
                    println("Character intersects with event: ${event.key}")
                    val nextScene: String? =  event.value.properties["nextScene"]?.string
                    when(nextScene) {
                        "dungeon" -> launchImmediately { sceneContainer.pushTo<DungeonScene>(
                            transition = MaskTransition(
                                transition = TransitionFilter.Transition.CIRCULAR,
                                reversed = false,
                                smooth = true,
                                filtering = true
                            ),
                            time = 1.seconds)
                        }
                        "untitled" -> launchImmediately { sceneContainer.pushTo<RpgIngameScene>(
                            transition = MaskTransition(
                                transition = TransitionFilter.Transition.CIRCULAR,
                                reversed = false,
                                smooth = true,
                                filtering = true
                            ),
                            time = 1.seconds)
                        }
                    }
/*
                    event.value.properties["nextScene"]?.also {
                        val nextSceneClass: KClass<out Scene>? = nextScenes[it.string]
                        launchImmediately { sceneContainer.changeTo<DungeonScene>(
                            transition = MaskTransition(
                                transition = TransitionFilter.Transition.CIRCULAR,
                                reversed = false,
                                smooth = true,
                                filtering = true
                        ),
                        time = 1.seconds)
                        }
                    }
*/
                }
            }
        }

        // there's a coordinate problem here I need to solve
        // this only returns the ID of the tile used, which doesn't say much at all
        val pressingInfo = keys[Key.I]
        if (pressingInfo) {
            val characterPos = char.pos
            println("Tile at [${characterPos.x},${characterPos.y}]")
            if (charGridX >= 0 && charGridX >= 0) {
                for (layer in tiledMapView.tiledMap.data.tileLayers) {
                    val currentTile = layer[charGridX, charGridY]
                    println("Tile at [${characterPos.x},${characterPos.y}] for layer ${layer.id}(${layer.name}) is: $currentTile")
                    println(tiledMapView.tiledMap.tilesets[0].data.tiles[currentTile].type)
                }
            }
        }

    }
}
