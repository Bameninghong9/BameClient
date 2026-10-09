package com.bame.client.notification;

public class Notification {
    public enum Type {
        ENABLED(0xFF22C55E, 0xFF15803D, "✓"),
        DISABLED(0xFFEF4444, 0xFFB91C1C, "✕"),
        INFO(0xFF3B82F6, 0xFF1D4ED8, "ℹ");

        public final int color;
        public final int darkColor;
        public final String icon;

        Type(int color, int darkColor, String icon) {
            this.color = color;
            this.darkColor = darkColor;
            this.icon = icon;
        }
    }

    private final String title;
    private final String message;
    private final Type type;
    private final long createdAt;
    private final long durationMs;
    private float currentY = -1;
    private float slideProgress = 0f; // 0 (offscreen right) to 1 (fully visible)
    private boolean dismissed = false;

    public Notification(String title, String message, Type type) {
        this(title, message, type, 2200);
    }

    public Notification(String title, String message, Type type, long durationMs) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.createdAt = System.currentTimeMillis();
        this.durationMs = durationMs;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public Type getType() {
        return type;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public float getSlideProgress() {
        return slideProgress;
    }

    public void setSlideProgress(float slideProgress) {
        this.slideProgress = slideProgress;
    }

    public float getCurrentY() {
        return currentY;
    }

    public void setCurrentY(float currentY) {
        this.currentY = currentY;
    }

    public boolean isDismissed() {
        return dismissed;
    }

    public void dismiss() {
        this.dismissed = true;
    }

    public boolean isExpired() {
        return dismissed || (System.currentTimeMillis() - createdAt >= durationMs);
    }

    public float getRemainingRatio() {
        long elapsed = System.currentTimeMillis() - createdAt;
        if (elapsed >= durationMs) return 0f;
        return 1.0f - (elapsed / (float) durationMs);
    }
}
