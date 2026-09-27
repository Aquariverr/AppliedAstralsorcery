package com.appliedastralsorcery.client;

import java.util.List;
import net.minecraft.client.renderer.Rect2i;

final class MarkerAreas {
    private MarkerAreas() {}

    static boolean contains(List<Rect2i> areas, double x, double y) {
        return areas.stream().anyMatch(area -> x >= area.getX() && x < area.getX() + area.getWidth()
                && y >= area.getY() && y < area.getY() + area.getHeight());
    }
}
