package com.example.bedstalker.client;

import com.example.bedstalker.entity.StalkerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class StalkerRenderer extends GeoEntityRenderer<StalkerEntity> {

    public StalkerRenderer(EntityRendererProvider.Context context) {
        super(context, new StalkerModel());
        this.shadowRadius = 0.45F;
        // Ojos brillantes: usa textures/entity/stalker_glowmask.png
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
