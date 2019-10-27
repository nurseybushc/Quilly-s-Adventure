package com.game.quillyjumper.ecs.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.SortedIteratingSystem
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.FloatArray
import com.badlogic.gdx.utils.viewport.Viewport
import com.game.quillyjumper.ecs.component.*
import com.game.quillyjumper.map.*
import com.game.quillyjumper.map.Map
import ktx.ashley.allOf
import ktx.ashley.exclude
import ktx.graphics.use
import ktx.log.logger

private val LOG = logger<RenderSystem>()

class RenderSystem(
    engine: Engine,
    private val batch: SpriteBatch,
    private val gameViewPort: Viewport,
    private val world: World,
    private val mapRenderer: OrthogonalTiledMapRenderer,
    private val box2DDebugRenderer: Box2DDebugRenderer
) : MapChangeListener, SortedIteratingSystem(
    allOf(RenderComponent::class, TransformComponent::class).exclude(RemoveComponent::class).get(),
    compareBy { entity -> entity.transfCmp }
) {
    private val camera = gameViewPort.camera as OrthographicCamera

    private val mapBackgroundLayers = Array<TiledMapTileLayer>()
    private val mapForegroundLayers = Array<TiledMapTileLayer>()
    private val mapParallaxValues = FloatArray()

    private val particleEffects =
        engine.getEntitiesFor(
            allOf(
                ParticleComponent::class,
                TransformComponent::class
            ).exclude(RemoveComponent::class).get()
        )

    override fun update(deltaTime: Float) {
        // Update animation timer for animated tiles
        AnimatedTiledMapTile.updateAnimationBaseTime()
        // always sort entities before rendering
        forceSort()
        // update camera to set the correct matrix for rendering later on
        gameViewPort.apply()
        batch.use {
            // set view of map renderer. Internally sets the projection matrix of the sprite batch
            // which is used to correctly render not map related stuff like our entities
            mapRenderer.setView(camera)
            // render background of map
            val numBgdLayers = mapBackgroundLayers.size
            val parallaxMinWidth = camera.viewportWidth * 0.5f
            for (i in 0 until numBgdLayers) {
                renderTileLayer(mapBackgroundLayers[i], i, parallaxMinWidth)
            }
            // render entities
            super.update(deltaTime)
            // render particle effects and reset blend state manually
            particleEffects.forEach { entity -> entity.particleCmp.effect.draw(it, deltaTime) }
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            // render foreground of map
            for (i in 0 until mapForegroundLayers.size) {
                renderTileLayer(mapForegroundLayers[i], i + numBgdLayers, parallaxMinWidth)
            }
        }
        // debug render box2d
        box2DDebugRenderer.render(world, gameViewPort.camera.combined)
    }

    private fun renderTileLayer(layer: TiledMapTileLayer, parallaxIndex: Int, minWidth: Float) {
        val parallaxValue = mapParallaxValues[parallaxIndex]
        val camPos = camera.position
        if (parallaxValue == 0f || camPos.x <= minWidth) {
            // tile layer has no parallax value or minimum width is not yet reached to trigger
            // the parallax effect
            mapRenderer.renderTileLayer(layer)
        } else {
            // make parallax effect by drawing the layer offset to its original value and
            // therefore creating a sort of "move" effect for the user
            val origVal = camPos.x
            camPos.x += (minWidth - camPos.x) * parallaxValue
            camera.update()
            mapRenderer.setView(camera)
            mapRenderer.renderTileLayer(layer)
            // reset the camera to its original position to draw remaining stuff with original values
            camPos.x = origVal
            camera.update()
            mapRenderer.setView(camera)
        }
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        entity.renderCmp.run {
            sprite.run {
                // if the sprite does not have any texture then do not render it to avoid null pointer exceptions
                if (texture == null) {
                    LOG.error { "Entity is without a texture for rendering" }
                    return
                }

                // adjust sprite position to render image centered around the entity's position
                val transform = entity.transfCmp
                setPosition(
                    transform.interpolatedPosition.x - (width - transform.size.x) * 0.5f,
                    transform.interpolatedPosition.y - 0.01f
                )
                draw(batch)
            }
        }
    }

    override fun mapChange(newMap: Map) {
        mapRenderer.map = newMap.tiledMap
        // retrieve background and foreground tiled map layers for rendering
        mapBackgroundLayers.clear()
        mapForegroundLayers.clear()
        mapParallaxValues.clear()
        mapRenderer.map.layers.forEach { layer ->
            if (layer is TiledMapTileLayer && layer.isVisible) {
                // tiled map layer which is visible for rendering
                // check if it is in the background or foreground
                if (layer.name.startsWith(TILED_LAYER_BACKGROUND_PREFIX)) {
                    mapBackgroundLayers.add(layer)
                } else {
                    mapForegroundLayers.add(layer)
                }
                mapParallaxValues.add(layer.property(PROPERTY_PARALLAX_VALUE, 0f))
            }
        }
    }
}