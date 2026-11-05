package dev.artmap.pen.input;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.util.List;

/**
 * A polling Wintab32.dll input source for Windows + Wacom's real driver.
 * Accesses actual digitizer coordinates; never guesses from SDL relative cursor motion.
 * All methods must be invoked on the Minecraft client thread.
 */
public final class WintabPen implements AutoCloseable {
    // Wintab 1.4 header values: WTI_DEFCONTEXT=3, PK_BUTTONS|PK_X|PK_Y|PK_NORMAL_PRESSURE.
    private static final int PK_STATUS = 0x2, PK_BUTTONS = 0x40, PK_X = 0x80, PK_Y = 0x100, PK_PRESSURE = 0x400;
    private static final int OUTPUT_SIZE = 10000;
    private static final int PACKET_SIZE = 5 * 4;
    private static final int BUFFER_PACKETS = 128;

    private final Wintab wintab;
    private final User32 user32;
    private final Kernel32 kernel32;
    private final Memory packetBuffer = new Memory(PACKET_SIZE * BUFFER_PACKETS);
    private Pointer context;
    private Pointer hwnd;
    private final PenPacketState state = new PenPacketState();
    private int totalPackets;
    private String error = "Not initialized";

    public WintabPen() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows"))
            throw new IllegalStateException("WinTab needs Windows with the Wacom tablet driver");
        wintab = Native.load("Wintab32", Wintab.class);
        user32 = Native.load("user32", User32.class);
        kernel32 = Native.load("kernel32", Kernel32.class);
    }

    public boolean open() {
        close();
        hwnd = user32.GetForegroundWindow();
        if (hwnd == null) { error = "Minecraft window not focused"; return false; }
        IntByReference process = new IntByReference();
        user32.GetWindowThreadProcessId(hwnd, process);
        if (process.getValue() != kernel32.GetCurrentProcessId()) {
            error = "Active window does not belong to Minecraft";
            return false;
        }
        LogContext lc = new LogContext();
        int result = wintab.WTInfoA(3, 0, lc.getPointer());
        if (result < lc.size()) {
            error = "Wintab WTInfoA failed (driver not installed or not running)";
            return false;
        }
        lc.read();
        // Digitizer context does not move the system pointer. On deactivation,
        // WTClose restores normal mouse behavior.
        lc.lcOptions &= ~1;              // clear CXO_SYSTEM
        lc.lcOptions &= ~4;              // polling, no WT_PACKET messages needed
        lc.lcPktData = PK_STATUS | PK_BUTTONS | PK_X | PK_Y | PK_PRESSURE;
        lc.lcPktMode = 0;                // absolute coordinates
        lc.lcMoveMask = lc.lcPktData;
        lc.lcBtnDnMask = -1;
        lc.lcBtnUpMask = -1;
        lc.lcOutOrgX = 0;
        lc.lcOutOrgY = 0;
        lc.lcOutExtX = OUTPUT_SIZE;
        lc.lcOutExtY = OUTPUT_SIZE;
        lc.write();
        context = wintab.WTOpenA(hwnd, lc.getPointer(), 1);
        if (context == null) {
            error = "Wintab WTOpenA failed; check Wacom driver / Windows Ink settings";
            return false;
        }
        wintab.WTOverlap(context, 1);
        wintab.WTQueueSizeSet(context, BUFFER_PACKETS);
        error = "OK";
        state.reset();
        totalPackets = 0;
        return true;
    }

    public boolean isOpen() { return context != null; }
    public String error() { return error; }
    public int totalPackets() { return totalPackets; }

    public boolean isMinecraftFocused() {
        return hwnd != null && hwnd.equals(user32.GetForegroundWindow());
    }

    /** Returns recent coordinates/state, or null after proximity loss or a 500 ms input timeout. */
    public Sample poll(int tipButtonMask, int pressureThreshold) {
        if (context == null) return null;
        state.beginPoll();
        int iterations = 0;
        while (++iterations <= 4) {
            int count = wintab.WTPacketsGet(context, BUFFER_PACKETS, packetBuffer);
            if (count < 0 || count > BUFFER_PACKETS) {
                state.reset();
                throw new IllegalStateException("Invalid WTPacketsGet count: " + count);
            }
            for (int i = 0; i < count; i++) {
                long offset = (long)i * PACKET_SIZE;
                int status = packetBuffer.getInt(offset);
                int buttons = packetBuffer.getInt(offset + 4);
                int x = packetBuffer.getInt(offset + 8);
                int y = packetBuffer.getInt(offset + 12);
                int pressure = packetBuffer.getInt(offset + 16);
                state.accept(status, buttons, x, y, pressure, tipButtonMask,
                        pressureThreshold, System.nanoTime());
                totalPackets++;
            }
            if (count < BUFFER_PACKETS) break;
        }
        return state.finishPoll(System.nanoTime());
    }

    @Override public void close() {
        if (context != null) {
            wintab.WTClose(context);
            context = null;
        }
        hwnd = null;
        state.reset();
    }

    public record Sample(double x, double y, int pressure, int buttons,
                         boolean touching, boolean tappedDuringPoll,
                         double tapX, double tapY, boolean armed) {}

    public interface Wintab extends StdCallLibrary {
        int WTInfoA(int category, int index, Pointer data);
        Pointer WTOpenA(Pointer window, Pointer context, int enable);
        int WTClose(Pointer context);
        int WTOverlap(Pointer context, int top);
        int WTPacketsGet(Pointer context, int maxPackets, Pointer buffer);
        int WTQueueSizeSet(Pointer context, int packets);
    }

    public interface User32 extends StdCallLibrary {
        Pointer GetForegroundWindow();
        int GetWindowThreadProcessId(Pointer hwnd, IntByReference processId);
    }

    public interface Kernel32 extends StdCallLibrary {
        int GetCurrentProcessId();
    }

    /** Matches wintab.h LOGCONTEXTA; all UINT/LONG/BOOL fields are 32-bit on Windows x64. */
    public static final class LogContext extends Structure {
        public byte[] lcName = new byte[40];
        public int lcOptions, lcStatus, lcLocks, lcMsgBase, lcDevice, lcPktRate;
        public int lcPktData, lcPktMode, lcMoveMask, lcBtnDnMask, lcBtnUpMask;
        public int lcInOrgX, lcInOrgY, lcInOrgZ;
        public int lcInExtX, lcInExtY, lcInExtZ;
        public int lcOutOrgX, lcOutOrgY, lcOutOrgZ;
        public int lcOutExtX, lcOutExtY, lcOutExtZ;
        public int lcSensX, lcSensY, lcSensZ;
        public int lcSysMode, lcSysOrgX, lcSysOrgY, lcSysExtX, lcSysExtY;
        public int lcSysSensX, lcSysSensY;

        @Override protected List<String> getFieldOrder() {
            return List.of("lcName", "lcOptions", "lcStatus", "lcLocks", "lcMsgBase",
                    "lcDevice", "lcPktRate", "lcPktData", "lcPktMode", "lcMoveMask",
                    "lcBtnDnMask", "lcBtnUpMask", "lcInOrgX", "lcInOrgY", "lcInOrgZ",
                    "lcInExtX", "lcInExtY", "lcInExtZ", "lcOutOrgX", "lcOutOrgY",
                    "lcOutOrgZ", "lcOutExtX", "lcOutExtY", "lcOutExtZ", "lcSensX",
                    "lcSensY", "lcSensZ", "lcSysMode", "lcSysOrgX", "lcSysOrgY",
                    "lcSysExtX", "lcSysExtY", "lcSysSensX", "lcSysSensY");
        }
    }
}
