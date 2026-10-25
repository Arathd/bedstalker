package com.example.bedstalker.registry;

import com.example.bedstalker.BedStalkerMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Los SoundEvent se registran en el registro comun (cliente y servidor).
 * Los archivos .ogg se enlazan en assets/bedstalker/sounds.json.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, BedStalkerMod.MOD_ID);

    public static final RegistryObject<SoundEvent> STEP    = register("entity.stalker.step");
    public static final RegistryObject<SoundEvent> BREATH  = register("entity.stalker.breath");
    public static final RegistryObject<SoundEvent> WHISPER = register("entity.stalker.whisper");
    public static final RegistryObject<SoundEvent> SCREAM  = register("entity.stalker.scream");
    public static final RegistryObject<SoundEvent> BANG    = register("entity.stalker.bang");
    public static final RegistryObject<SoundEvent> SMASH   = register("entity.stalker.smash");
    public static final RegistryObject<SoundEvent> HIDE    = register("entity.stalker.hide");
    public static final RegistryObject<SoundEvent> EMERGE  = register("entity.stalker.emerge");
    public static final RegistryObject<SoundEvent> HURT    = register("entity.stalker.hurt");
    public static final RegistryObject<SoundEvent> DEATH   = register("entity.stalker.death");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(BedStalkerMod.MOD_ID, name)));
    }

    private ModSounds() {}
}
