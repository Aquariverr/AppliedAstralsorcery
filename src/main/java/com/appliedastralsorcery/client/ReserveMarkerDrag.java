package com.appliedastralsorcery.client;

public final class ReserveMarkerDrag {
    private boolean dragging;
    private int amount;

    public static int amountAt(double mouseX, int left, int width, int maximum) {
        if (width <= 1 || maximum <= 0) return 0;
        return (int) Math.round(Math.clamp((mouseX - left) / (width - 1), 0.0, 1.0) * maximum);
    }

    public void begin(double mouseX, int left, int width, int maximum) {
        dragging = true;
        update(mouseX, left, width, maximum);
    }

    public void update(double mouseX, int left, int width, int maximum) {
        if (dragging) amount = amountAt(mouseX, left, width, maximum);
    }

    public boolean isDragging() { return dragging; }
    public int displayed(int confirmed) { return dragging ? amount : confirmed; }
    public int finish() { dragging = false; return amount; }
    public void cancel() { dragging = false; }
}
