import com.soywiz.klock.Stopwatch
import com.soywiz.klock.milliseconds
import com.soywiz.klock.seconds
import com.soywiz.korev.Key
import com.soywiz.korge.Korge
import com.soywiz.korge.component.docking.keepChildrenSortedByY
import com.soywiz.korge.input.keys
import com.soywiz.korge.scene.MaskTransition
import com.soywiz.korge.scene.Scene
import com.soywiz.korge.scene.sceneContainer
import com.soywiz.korge.tiled.*
import com.soywiz.korge.ui.UIText
import com.soywiz.korge.ui.textAlignment
import com.soywiz.korge.ui.uiText
import com.soywiz.korge.view.*
import com.soywiz.korge.view.animation.ImageDataView
import com.soywiz.korge.view.animation.imageDataView
import com.soywiz.korge.view.camera.cameraContainer
import com.soywiz.korge.view.filter.IdentityFilter
import com.soywiz.korge.view.filter.TransitionFilter
import com.soywiz.korim.atlas.MutableAtlasUnit
import com.soywiz.korim.color.Colors
import com.soywiz.korim.format.ASE
import com.soywiz.korim.format.ImageDataContainer
import com.soywiz.korim.format.readImageDataContainer
import com.soywiz.korio.dynamic.dyn
import com.soywiz.korio.file.std.resourcesVfs
import com.soywiz.korma.geom.Point
import kotlin.math.roundToInt

suspend fun main() =
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

class RpgIngameScene : Scene() {
    val atlas = MutableAtlasUnit(2048, 2048)
    lateinit var tilemap: TiledMap
    lateinit var characters: ImageDataContainer

    override suspend fun Container.sceneInit() {

        val sw = Stopwatch().start()

        println("start resources loading...")
//BasicTilemap/untitled.tmx     MiniTileset-Dungeon/dungeon.tmx
        tilemap = resourcesVfs["Texture/basic.tmx"].readTiledMap(atlas = atlas)
        characters = resourcesVfs["vampire.ase"].readImageDataContainer(ASE, atlas = atlas)

        println("loaded resources in ${sw.elapsed}")
    }

    override suspend fun Container.sceneMain() {
        container {
            scale(2.0)

            lateinit var player: ImageDataView
            lateinit var tiledMapView: TiledMapView
            val npcsMap = mutableMapOf<String, NPC>()

            val cameraContainer = cameraContainer(
                256.0, 256.0, clip = true,
                block = {
                    clampToBounds = true
                }
            ) {
                tiledMapView = tiledMapView(tilemap, smoothing = false, showShapes = false)
                tiledMapView.filter = IdentityFilter(false)

                // error is thrown if this is null, because we don't know where to place the player
                println("tiledMapView[\"start\"]=${tiledMapView["start"].firstOrNull}")
                /*val npcs: List<TiledMap.Object> = tiledMapView.tiledMap.data.getObjectByType("npc")
                for(npc in npcs) {
                    println("- npc = $npc")
                }*/

                for (obj in tiledMapView.tiledMap.data.objectLayers.objects) {
                    println("- obj = $obj")
                }
                println(tiledMapView.firstDescendantWith { it.getPropString("type") == "start" })
                val startPos = tiledMapView["start"].firstOrNull?.pos ?: Point(50, 50)
                val charactersLayer = tiledMapView["characters"].first as Container

                println("charactersLayer before=$charactersLayer") // this is empty on load, but then populated by the npcs?

                charactersLayer.keepChildrenSortedByY()

                // here we are loading the NPCs for display, but they don't exist as independent objects within the game world
                // can I add their game world properties to the npcs list?
                for (npcObject in tiledMapView.tiledMap.data.getObjectByType("npc")) {
                    val npcImageDV = charactersLayer.imageDataView(
                        characters[npcObject.str("skin")],
                        "down",
                        playing = false,
                        smoothing = false
                    ) {
                        xy(npcObject.x, npcObject.y)
                    }
                    npcsMap[npcObject.name] = NPC(npcImageDV,npcObject)
                }
                println("Loaded all npcs into npcsMap")
                println(npcsMap)

                player =
                    charactersLayer.imageDataView(characters["vampire"], "right", playing = false, smoothing = false) {
                        xy(startPos)
                    }
                println("charactersLayer after=$charactersLayer")
            }

            val textPos = Point(5.0, this.height-20.0)
            val hintText = uiText("Press 'E' to interact").xy(textPos)

            cameraContainer.cameraViewportBounds.copyFrom(tiledMapView.getLocalBoundsOptimized())

            stage!!.controlWithKeyboard(player, tiledMapView, npcs = npcsMap.values.toList(), hintText, tiledMapView)

            cameraContainer.follow(player, setImmediately = true)

            //cameraContainer.tweenCamera(cameraContainer.getCameraRect(Rectangle(200, 200, 100, 100)))
        }
    }
}

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
    messageBox: UIText,
    tiledMapView: TiledMapView,
    up: Key = Key.UP, // don't bother with these until we have user-configurable keybindings
    right: Key = Key.RIGHT,
    down: Key = Key.DOWN,
    left: Key = Key.LEFT,
) {

    addUpdater { dt ->

        // 32f being the tile grid size
        val charGridX = ((char.x / 32f) - 1).roundToInt()
        val charGridY = ((char.y / 32f) - 1).roundToInt()

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
        if(pressingInteract) {
            for (npc in npcs) {
                if (char.collidesWith(npc.imageDataView)) {
//                    println("Collided with NPC $npc")
                    if (npc.obj.properties["greeting"] != null) {
                        messageBox.text = npc.obj.properties["greeting"].toString()
                        println("NPC says: " + npc.obj.properties["greeting"])
                    }
                }
            }
        }

        // there's a coordinate problem here I need to solve
        val pressingInfo = keys[Key.I]
        if(pressingInfo) {
            val characterPos = char.pos
            println("Tile at [${characterPos.x},${characterPos.y}]")
            val currentTile = tiledMapView.tiledMap.data.tileLayers.first()[charGridX,charGridY]
            println("Tile at [${characterPos.x},${characterPos.y}] is: $currentTile")
        }

    }
}
