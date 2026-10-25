package com.example.bedstalker.client;

import com.example.bedstalker.BedStalkerMod;
import com.example.bedstalker.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Solo se carga en el cliente: registra el renderer GeckoLib. */
@Mod.EventBusSubscriber(modid = BedStalkerMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.STALKER.get(), StalkerRenderer::new);
    }

    private ClientModEvents() {}
}
