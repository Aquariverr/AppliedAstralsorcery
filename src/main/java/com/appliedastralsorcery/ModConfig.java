package com.appliedastralsorcery;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Processing times in ticks, controlled by the server and synced to clients. */
public final class ModConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue AUTO_CHISEL_TICKS;
    public static final ModConfigSpec.IntValue TRANSMUTATION_FOCAL_TICKS;
    public static final ModConfigSpec.IntValue TRANSMUTATION_STARLIGHT_TICKS;
    public static final ModConfigSpec.IntValue TRANSMUTATION_OVERCLOCK_TICKS;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("Machine processing times in ticks (20 ticks = 1 second).")
                .translation("appliedas.configuration.processing").push("processing");
        AUTO_CHISEL_TICKS = builder.comment("Automatic Starmetal Chisel duration, for both inventory and dropped items.")
                .translation("appliedas.configuration.autoChiselTicks")
                .defineInRange("autoChiselTicks", 40, 1, Integer.MAX_VALUE);
        TRANSMUTATION_FOCAL_TICKS = builder.comment("Transmutation duration when using a focal point directly.")
                .translation("appliedas.configuration.transmutationFocalTicks")
                .defineInRange("transmutationFocalTicks", 1200, 1, Integer.MAX_VALUE);
        TRANSMUTATION_STARLIGHT_TICKS = builder.comment(
                        "Transmutation duration with crystal or other transmitted starlight.",
                        "Matching transmitted starlight takes priority over a local focal point.")
                .translation("appliedas.configuration.transmutationStarlightTicks")
                .defineInRange("transmutationStarlightTicks", 200, 1, Integer.MAX_VALUE);
        TRANSMUTATION_OVERCLOCK_TICKS = builder.comment("Transmutation duration after paying the lumen overclock cost.")
                .translation("appliedas.configuration.transmutationOverclockTicks")
                .defineInRange("transmutationOverclockTicks", 20, 1, Integer.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();
    }

    private ModConfig() {}
}
