package dev.artmap.pen.input;

public final class PenPacketStateSelfTest {
    private static final long NOW = 1_000_000_000L;

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void packet(PenPacketState state, int status, int buttons, int x, int y, int pressure) {
        state.accept(status, buttons, x, y, pressure, 1, 1, NOW);
    }

    public static void main(String[] args) {
        var state = new PenPacketState();
        require(state.finishPoll(NOW) == null, "No synthetic input on startup");
        state.beginPoll();
        packet(state, 0, 1, 1000, 2000, 100);
        require(!state.finishPoll(NOW).touching(), "Tip held on enable must not draw");
        packet(state, 0, 0, 1000, 2000, 0);
        packet(state, 0, 1, 2500, 7500, 100);
        packet(state, 0, 0, 9000, 9000, 0);
        var tap = state.finishPoll(NOW);
        require(tap.tappedDuringPoll() && !tap.touching(), "Short tap survives polling");
        require(tap.tapX() == 0.25 && tap.tapY() == 0.75, "Tap retains contact coordinates");
        state.beginPoll();
        require(!state.finishPoll(NOW).tappedDuringPoll(), "Tap must not repeat");
        packet(state, 0, 0, 5000, 5000, 50);
        require(state.finishPoll(NOW).touching(), "Pressure fallback detects contact");
        state.beginPoll();
        require(state.finishPoll(NOW).touching(), "No-change poll keeps a recent held stroke");
        require(state.finishPoll(NOW + PenPacketState.STALE_NANOS) == null, "Stale input releases stroke");
        packet(state, 0, 1, 5000, 5000, 100);
        require(!state.finishPoll(NOW).touching(), "Stale recovery requires lift");
        for (int invalid : new int[] {1, 2, 4, 8}) {
            packet(state, 0, 0, 0, 0, 0);
            packet(state, 0, 1, 0, 0, 100);
            packet(state, invalid, 1, 0, 0, 100);
            require(state.finishPoll(NOW) == null, "Invalid status must release: " + invalid);
        }
        state.reset();
        packet(state, 0, 0, -100, 20000, 0);
        var clamped = state.finishPoll(NOW);
        require(clamped.x() == 0 && clamped.y() == 1, "Clamp native coordinates");
        require(new WintabPen.LogContext().size() == 172, "LOGCONTEXTA must match WinTab ABI");
        System.out.println("PenPacketStateSelfTest: OK");
    }
}
