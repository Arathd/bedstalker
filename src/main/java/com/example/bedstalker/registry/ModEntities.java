package com.example.bedstalker.registry;

import com.example.bedstalker.BedStalkerMod;
import com.example.bedstalker.entity.StalkerEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BedStalkerMod.MOD_ID);

    /** Alto (2.9 bloques) y delgado (0.7 de ancho). */
    public static final RegistryObject<EntityType<StalkerEntity>> STALKER = ENTITIES.register("stalker",
            () -> EntityType.Builder.<StalkerEntity>of(StalkerEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.9F)
                    .clientTrackingRange(10)
                    .build(BedStalkerMod.MOD_ID + ":stalker"));

    private ModEntities() {}
}
