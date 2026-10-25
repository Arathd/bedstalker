package com.example.bedstalker.registry;

import com.example.bedstalker.BedStalkerMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    public static final TagKey<Block> BREAKABLE_WOODEN_DOORS = block("breakable_wooden_doors");
    public static final TagKey<Block> BREAKABLE_IRON_DOORS = block("breakable_iron_doors");
    public static final TagKey<Block> BREAKABLE_WINDOWS = block("breakable_windows");

    private static TagKey<Block> block(String name) {
        return TagKey.create(Registries.BLOCK, new ResourceLocation(BedStalkerMod.MOD_ID, name));
    }

    private ModTags() {}
}
