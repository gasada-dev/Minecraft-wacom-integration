package dev.artmap.pen.input;

/** Native-independent packet state. A new context must see hover before it can draw. */
public final class PenPacketState {
    static final long STALE_NANOS = 500_000_000L;
    private WintabPen.Sample last;
    private long lastPacketNanos;
    private boolean armed;
    private boolean tapped;
    private double tapX, tapY;

    public void reset() {
        last = null;
        armed = false;
        tapped = false;
    }

    public void beginPoll() { tapped = false; }

    public void accept(int status, int buttons, int x, int y, int pressure,
                       int tipMask, int threshold, long now) {
        // TPS_PROXIMITY / TPS_QUEUE_ERR / TPS_MARGIN / TPS_GRAB: release on invalid input.
        if ((status & 0x0f) != 0) {
            reset();
            return;
        }
        boolean rawDown = (buttons & tipMask) != 0 || pressure >= threshold;
        if (!rawDown) armed = true;
        boolean down = armed && rawDown;
        double u = Math.max(0, Math.min(1, x / 10000.0));
        double v = Math.max(0, Math.min(1, y / 10000.0));
        if (down && (last == null || !last.touching())) {
            tapped = true;
            tapX = u;
            tapY = v;
        }
        last = new WintabPen.Sample(u, v, pressure, buttons, down, false, u, v, armed);
        lastPacketNanos = now;
    }

    public WintabPen.Sample finishPoll(long now) {
        if (last == null || now - lastPacketNanos >= STALE_NANOS) {
            reset();
            return null;
        }
        return new WintabPen.Sample(last.x(), last.y(), last.pressure(), last.buttons(),
                last.touching(), tapped, tapX, tapY, armed);
    }
}
