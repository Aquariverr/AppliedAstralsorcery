package com.appliedastralsorcery.client;

import java.util.IdentityHashMap;
import java.util.Map;
import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.util.data.Vector3;

/** Fixed star-map vertices. This math-only class is also safe to verify on a dedicated server. */
public final class ConstellationRenderGeometry {
    public static final float SCALE = 2.7F;
    private static final float LINE_WIDTH = 0.45F;
    private final float rotation;
    private final int gridSize;
    private final double centerX, centerZ;
    private final float scale;
    private final float[] connections, stars;

    public static final class Cache {
        private final Map<BaseConstellation, ConstellationRenderGeometry> entries = new IdentityHashMap<>();

        public ConstellationRenderGeometry get(BaseConstellation constellation, float rotation) {
            var geometry = entries.get(constellation);
            if (geometry == null || Float.floatToIntBits(geometry.rotation) != Float.floatToIntBits(rotation)
                    || geometry.gridSize != BaseConstellation.STAR_GRID_WIDTH_HEIGHT) {
                geometry = new ConstellationRenderGeometry(constellation, rotation);
                entries.put(constellation, geometry);
            }
            return geometry;
        }
    }

    private ConstellationRenderGeometry(BaseConstellation constellation, float rotation) {
        this.rotation = rotation;
        gridSize = BaseConstellation.STAR_GRID_WIDTH_HEIGHT;
        var locations = constellation.getStars();
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        double gridCenter = gridSize / 2.0;
        double unit = SCALE / gridSize;
        double cos = Math.cos(rotation), sin = Math.sin(rotation);
        for (var star : locations) {
            double x = (star.x() - gridCenter) * unit, z = (star.y() - gridCenter) * unit;
            double rotatedX = cos * x + sin * z, rotatedZ = -sin * x + cos * z;
            minX = Math.min(minX, rotatedX);
            maxX = Math.max(maxX, rotatedX);
            minZ = Math.min(minZ, rotatedZ);
            maxZ = Math.max(maxZ, rotatedZ);
        }
        double spriteWidth = 2 * unit * (Math.abs(cos) + Math.abs(sin));
        centerX = locations.isEmpty() ? 0 : (minX + maxX) / 2;
        centerZ = locations.isEmpty() ? 0 : (minZ + maxZ) / 2;
        scale = locations.isEmpty() ? 1 : (float) Math.min(1,
                2.9 / (Math.max(maxX - minX, maxZ - minZ) + spriteWidth));

        // Match Astral Sorcery's vertex order and incremental Y offsets exactly.
        float starSize = 1F / gridSize * SCALE;
        var offset = new Vector3(gridSize, 0, gridSize).multiply(-0.5F * starSize);
        var pos = new Vector3(0, 0.012, 0);
        var links = constellation.getStarConnections();
        connections = new float[links.size() * 12];
        int index = 0;
        for (var link : links) {
            pos.addY(0.001F);
            var u = link.getRight().asLevelVector().subtract(link.getLeft().asLevelVector()).multiply(starSize);
            var v = u.copy().crossProduct(Vector3.RotAxis.Y_AXIS.getVector()).setY(0).normalize()
                    .multiply(LINE_WIDTH * starSize);
            var base = pos.copy().add(link.getLeft().asLevelVector().multiply(starSize))
                    .subtract(v.copy().divide(2)).add(offset);
            quad(connections, index, base, u, v);
            index += 12;
        }
        pos.addY(0.002F);
        stars = new float[locations.size() * 12];
        var u = new Vector3(starSize * 2, 0, 0);
        var v = new Vector3(0, 0, starSize * 2);
        index = 0;
        for (var star : locations) {
            var base = pos.copy().add(star.asLevelVector().multiply(starSize))
                    .add(-starSize, 0, -starSize).add(offset);
            quad(stars, index, base, u, v);
            index += 12;
        }
    }

    private static void quad(float[] target, int index, Vector3 base, Vector3 u, Vector3 v) {
        vertex(target, index, base.copy().add(v));
        vertex(target, index + 3, base.copy().add(u).add(v));
        vertex(target, index + 6, base.copy().add(u));
        vertex(target, index + 9, base);
    }

    private static void vertex(float[] target, int index, Vector3 pos) {
        target[index] = (float) pos.getX();
        target[index + 1] = (float) pos.getY();
        target[index + 2] = (float) pos.getZ();
    }

    public double centerX() { return centerX; }
    public double centerZ() { return centerZ; }
    public float scale() { return scale; }
    public int connectionVertexCount() { return connections.length / 3; }
    public int starVertexCount() { return stars.length / 3; }
    public float coordinate(boolean connection, int vertex, int axis) {
        return (connection ? connections : stars)[vertex * 3 + axis];
    }
}
