package com.appliedastralsorcery;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Processing times and lumen costs, controlled by the server and synced to clients. */
public final class ModConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue AUTO_CHISEL_TICKS;
    public static final ModConfigSpec.IntValue TRANSMUTATION_FOCAL_TICKS;
    public static final ModConfigSpec.IntValue TRANSMUTATION_STARLIGHT_TICKS;
    public static final ModConfigSpec.IntValue TRANSMUTATION_OVERCLOCK_TICKS;
    public static final ModConfigSpec.IntValue ATTUNEMENT_TICKS;
    public static final ModConfigSpec.IntValue AUTO_CHISEL_LUMEN_PER_CRAFT;
    public static final ModConfigSpec.IntValue ATTUNEMENT_LUMEN_PER_CRAFT;

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
        ATTUNEMENT_TICKS = builder.comment("Constellation Relay attunement duration; native attunement takes 500 ticks.")
                .translation("appliedas.configuration.attunementTicks")
                .defineInRange("attunementTicks", 500, 1, Integer.MAX_VALUE);
        builder.pop();

        builder.comment("Lumen consumed per completed operation.")
                .translation("appliedas.configuration.lumenConsumption").push("lumenConsumption");
        AUTO_CHISEL_LUMEN_PER_CRAFT = builder.comment(
                        "Evorsio lumen per completed Automatic Starmetal Chisel operation, for both inventory and dropped items.")
                .translation("appliedas.configuration.autoChiselLumenPerCraft")
                .defineInRange("autoChiselLumenPerCraft", 25, 1, 2000);
        ATTUNEMENT_LUMEN_PER_CRAFT = builder.comment(
                        "Prismatic lumen per completed attunement that bypassed constellation or sky requirements.",
                        "Requires the full amount to progress; consumes it once after the product is successfully emitted.")
                .translation("appliedas.configuration.attunementLumenPerCraft")
                .defineInRange("attunementLumenPerCraft", 50, 1, 16000);
        builder.pop();
        SPEC = builder.build();
    }

    private ModConfig() {}
}
