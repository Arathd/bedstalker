package com.example.bedstalker.client;

import com.example.bedstalker.BedStalkerMod;
import com.example.bedstalker.entity.StalkerEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class StalkerModel extends GeoModel<StalkerEntity> {
    private static final ResourceLocation MODEL =
            new ResourceLocation(BedStalkerMod.MOD_ID, "geo/entity/stalker.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(BedStalkerMod.MOD_ID, "textures/entity/stalker.png");
    private static final ResourceLocation ANIMATIONS =
            new ResourceLocation(BedStalkerMod.MOD_ID, "animations/entity/stalker.animation.json");

    @Override
    public ResourceLocation getModelResource(StalkerEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(StalkerEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(StalkerEntity animatable) {
        return ANIMATIONS;
    }

    /** La cabeza sigue la mirada, salvo mientras esta agachado / bajo la cama. */
    @Override
    public void setCustomAnimations(StalkerEntity animatable, long instanceId, AnimationState<StalkerEntity> animationState) {
        if (animatable.getBedState() != StalkerEntity.BedState.NONE) {
            return;
        }
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        if (head != null) {
            EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
            head.setRotX(data.headPitch() * Mth.DEG_TO_RAD);
            head.setRotY(data.netHeadYaw() * Mth.DEG_TO_RAD);
        }
    }
}
