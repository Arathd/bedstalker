package com.example.bedstalker.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuracion comun (servidor + cliente): config/bedstalker-common.toml */
public final class StalkerConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue BREAK_WOODEN_DOORS;
    public static final ForgeConfigSpec.BooleanValue BREAK_IRON_DOORS;
    public static final ForgeConfigSpec.BooleanValue BREAK_WINDOWS;
    public static final ForgeConfigSpec.IntValue WOODEN_DOOR_TICKS;
    public static final ForgeConfigSpec.IntValue IRON_DOOR_TICKS;
    public static final ForgeConfigSpec.IntValue WINDOW_TICKS;
    public static final ForgeConfigSpec.BooleanValue HIDE_UNDER_BEDS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Destruccion de bloques (respeta ademas la gamerule mobGriefing)").push("griefing");
        BREAK_WOODEN_DOORS = b.comment("El Stalker puede romper puertas de madera")
                .define("breakWoodenDoors", true);
        BREAK_IRON_DOORS = b.comment("El Stalker puede romper puertas de hierro (tarda mas)")
                .define("breakIronDoors", true);
        BREAK_WINDOWS = b.comment("El Stalker puede romper bloques y paneles de cristal (tag bedstalker:breakable_windows)")
                .define("breakWindows", true);
        WOODEN_DOOR_TICKS = b.comment("Ticks para romper una puerta de madera (20 ticks = 1 s)")
                .defineInRange("woodenDoorTicks", 60, 5, 1200);
        IRON_DOOR_TICKS = b.comment("Ticks para romper una puerta de hierro")
                .defineInRange("ironDoorTicks", 160, 5, 2400);
        WINDOW_TICKS = b.comment("Ticks para romper un cristal / panel")
                .defineInRange("windowTicks", 20, 1, 600);
        b.pop();

        b.push("behaviour");
        HIDE_UNDER_BEDS = b.comment("El Stalker puede esconderse debajo de las camas")
                .define("hideUnderBeds", true);
        b.pop();

        SPEC = b.build();
    }

    private StalkerConfig() {}
}
