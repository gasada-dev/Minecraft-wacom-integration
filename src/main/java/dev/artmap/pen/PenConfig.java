package dev.artmap.pen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** User preferences only. Camera calibration intentionally does NOT persist across easels/sessions. */
public final class PenConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("artmap-pen.json");

    public boolean flipY = true;
    public boolean flipX = false;
    public int tipButtonMask = 1;
    public int pressureThreshold = 1;
    public DrawButton drawButton = DrawButton.RIGHT;
    public boolean cancelPhysicalMouseInput = true;
    public int sideButton1Mask = 2;
    public int sideButton2Mask = 4;
    public SideAction sideButton1 = SideAction.LEFT_CLICK;
    public SideAction sideButton2 = SideAction.NONE;
    public double maxEyeMovementBlocks = 0.05;

    public enum DrawButton { RIGHT, LEFT }
    public enum SideAction { NONE, LEFT_CLICK, RIGHT_CLICK }

    public static PenConfig load() {
        try {
            if (Files.isRegularFile(FILE)) {
                PenConfig c = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), PenConfig.class);
                if (c != null) {
                    if (c.drawButton == null) c.drawButton = DrawButton.RIGHT;
                    c.tipButtonMask = Math.max(0, c.tipButtonMask);
                    c.sideButton1Mask = Math.max(0, c.sideButton1Mask);
                    c.sideButton2Mask = Math.max(0, c.sideButton2Mask);
                    if (c.sideButton1 == null) c.sideButton1 = SideAction.NONE;
                    if (c.sideButton2 == null) c.sideButton2 = SideAction.NONE;
                    c.pressureThreshold = Math.max(1, c.pressureThreshold);
                    if (!Double.isFinite(c.maxEyeMovementBlocks)
                            || c.maxEyeMovementBlocks < 0.001 || c.maxEyeMovementBlocks > 0.35)
                        c.maxEyeMovementBlocks = 0.05;
                    return c;
                }
            }
        } catch (Exception e) {
            ArtMapPenClient.LOGGER.warn("Could not read artmap-pen.json", e);
        }
        PenConfig c = new PenConfig();
        c.save();
        return c;
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ArtMapPenClient.LOGGER.warn("Could not save artmap-pen.json", e);
        }
    }
}
