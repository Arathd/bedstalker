package com.example.bedstalker.event;

import com.example.bedstalker.BedStalkerMod;
import com.example.bedstalker.entity.StalkerEntity;
import com.example.bedstalker.registry.ModEntities;
import com.example.bedstalker.registry.ModItems;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos del bus del mod (registro de atributos, reglas de spawn y pestana creativa). */
@Mod.EventBusSubscriber(modid = BedStalkerMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEventBusEvents {

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.STALKER.get(), StalkerEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(ModEntities.STALKER.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                StalkerEntity::checkStalkerSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    @SubscribeEvent
    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.STALKER_SPAWN_EGG);
        }
    }

    private ModEventBusEvents() {}
}
