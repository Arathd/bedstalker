package com.example.bedstalker;

import com.example.bedstalker.config.StalkerConfig;
import com.example.bedstalker.registry.ModEntities;
import com.example.bedstalker.registry.ModItems;
import com.example.bedstalker.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(BedStalkerMod.MOD_ID)
public class BedStalkerMod {
    public static final String MOD_ID = "bedstalker";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BedStalkerMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModItems.ITEMS.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, StalkerConfig.SPEC);
    }
}
