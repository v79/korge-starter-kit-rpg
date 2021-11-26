import com.soywiz.klock.Stopwatch
import com.soywiz.korge.component.docking.keepChildrenSortedByY
import com.soywiz.korge.scene.Scene
import com.soywiz.korge.tiled.*
import com.soywiz.korge.ui.uiText
import com.soywiz.korge.view.*
import com.soywiz.korge.view.animation.ImageDataView
import com.soywiz.korge.view.animation.imageDataView
import com.soywiz.korge.view.camera.cameraContainer
import com.soywiz.korge.view.filter.IdentityFilter
import com.soywiz.korim.atlas.MutableAtlasUnit
import com.soywiz.korim.format.ASE
import com.soywiz.korim.format.ImageDataContainer
import com.soywiz.korim.format.readImageDataContainer
import com.soywiz.korio.file.std.resourcesVfs
import com.soywiz.korma.geom.Point

class RpgIngameScene(val sceneMap: SceneMap) : Scene() {
    val atlas = MutableAtlasUnit(2048, 2048)
    lateinit var tilemap: TiledMap
    lateinit var characters: ImageDataContainer

    override suspend fun Container.sceneInit() {

        val sw = Stopwatch().start()

        println("start resources loading...")
//BasicTilemap/untitled.tmx     MiniTileset-Dungeon/dungeon.tmx     Texture/basic.tmx
        tilemap = resourcesVfs[sceneMap.value].readTiledMap(atlas = atlas)
        characters = resourcesVfs["vampire.ase"].readImageDataContainer(ASE, atlas = atlas)

        println("loaded resources in ${sw.elapsed}")
    }

    override suspend fun Container.sceneMain() {
        container {
            scale(2.0)

            lateinit var player: ImageDataView
            lateinit var tiledMapView: TiledMapView
            val npcsMap = mutableMapOf<String, NPC>()
            val eventsMap = mutableMapOf<String, TiledMap.Object>()

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
                    npcsMap[npcObject.name] = NPC(npcImageDV, npcObject)
                }
                println("Loaded all npcs into npcsMap")
                println(npcsMap)

                // get all the event locations
                for(eventObj in tiledMapView.tiledMap.data.getObjectByType("event")) {
                    eventsMap[eventObj.name] = eventObj
                }
                println("Loaded all events into eventsMap")
                println(eventsMap)

                player =
                    charactersLayer.imageDataView(characters["vampire"], "right", playing = false, smoothing = false) {
                        xy(startPos)
                    }
                println("charactersLayer after=$charactersLayer")
            }

            val textPos = Point(5.0, this.height - 20.0)
            val hintText = uiText("Press 'E' to interact").xy(textPos)

            cameraContainer.cameraViewportBounds.copyFrom(tiledMapView.getLocalBoundsOptimized())

            stage!!.controlWithKeyboard(player, tiledMapView, npcs = npcsMap.values.toList(), eventsMap, hintText, tiledMapView, sceneContainer)

            cameraContainer.follow(player, setImmediately = true)

            //cameraContainer.tweenCamera(cameraContainer.getCameraRect(Rectangle(200, 200, 100, 100)))
        }
    }
}