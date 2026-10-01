package com.appliedastralsorcery.transmutation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class TransmutationModelGameTests {
    @GameTest(template = "wand_empty")
    public static void chamberHasNoIntersectingFramePartsOrCoplanarVisibleFaces(GameTestHelper helper) throws Exception {
        var frame = elements("frame");
        for (int i = 0; i < frame.size(); i++) {
            for (int j = i + 1; j < frame.size(); j++) {
                var a = frame.get(i);
                var b = frame.get(j);
                helper.assertTrue(!overlaps(a, b, 0) || !overlaps(a, b, 1) || !overlaps(a, b, 2),
                        "Solid model parts intersect: " + name(a) + " / " + name(b));
            }
        }
        var all = new ArrayList<>(frame);
        all.addAll(elements("glass"));
        var faces = List.of("west", "east", "down", "up", "north", "south");
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                var a = all.get(i);
                var b = all.get(j);
                for (int f = 0; f < faces.size(); f++) {
                    var face = faces.get(f);
                    if (!a.getAsJsonObject("faces").has(face) || !b.getAsJsonObject("faces").has(face)) continue;
                    int axis = f / 2;
                    String edge = f % 2 == 0 ? "from" : "to";
                    boolean coplanar = Math.abs(coordinate(a, edge, axis) - coordinate(b, edge, axis)) < 0.00001;
                    helper.assertTrue(!coplanar || !overlaps(a, b, (axis + 1) % 3) || !overlaps(a, b, (axis + 2) % 3),
                            "Z-fighting on " + face + ": " + name(a) + " / " + name(b));
                }
            }
        }
        helper.succeed();
    }

    private static List<JsonObject> elements(String part) throws Exception {
        String path = "/assets/appliedas/models/block/starlight_transmutation_chamber_" + part + ".json";
        try (var stream = TransmutationModelGameTests.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing model: " + path);
            var model = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var result = new ArrayList<JsonObject>();
            for (var element : model.getAsJsonArray("elements")) result.add(element.getAsJsonObject());
            return result;
        }
    }

    private static boolean overlaps(JsonObject a, JsonObject b, int axis) {
        return Math.min(coordinate(a, "to", axis), coordinate(b, "to", axis))
                - Math.max(coordinate(a, "from", axis), coordinate(b, "from", axis)) > 0.00001;
    }

    private static double coordinate(JsonObject element, String edge, int axis) {
        return element.getAsJsonArray(edge).get(axis).getAsDouble();
    }

    private static String name(JsonObject element) { return element.get("name").getAsString(); }
}
