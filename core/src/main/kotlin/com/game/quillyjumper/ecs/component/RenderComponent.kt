package com.game.quillyjumper.ecs.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.g2d.Sprite
import ktx.ashley.mapperFor

class RenderComponent(var sprite: Sprite = Sprite()) : Component {
    companion object {
        val mapper = mapperFor<RenderComponent>()
    }
}