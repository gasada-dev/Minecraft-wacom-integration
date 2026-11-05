package dev.artmap.pen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.artmap.pen.input.WintabPen;
import dev.artmap.pen.math.CanvasAim;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Fabric client-only, no custom packets. ArtMap gets regular player look + item-use actions.
 * F6: sample four canvas corners with the ordinary mouse. F8: enable/disable pen.
 * F9: pen debugging. F10: Y flip. No auto-painting on entering the mode.
 */
public final class ArtMapPenClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ArtMapPen");

    private static final String[] CORNERS = {
            "левый верхний", "правый верхний", "правый нижний", "левый нижний"
    };
    private PenConfig config;
    private WintabPen pen;
    private boolean penAvailable = false;
    private CanvasAim.Angle[] measured = new CanvasAim.Angle[4];
    private CanvasAim calibration;
    private Vec3 referenceEye;
    private ClientLevel calibratedLevel;
    private int nextCorner = 0;
    private boolean painting;
    private boolean pressedByPen;
    private boolean side1PreviouslyDown;
    private boolean side2PreviouslyDown;
    private int ticksWithNoPackets;
    private WintabPen.Sample lastSample;

    private KeyMapping toggle;
    private KeyMapping calibrate;
    private KeyMapping status;
    private KeyMapping flipY;

    private static ArtMapPenClient INSTANCE;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        config = PenConfig.load();
        try {
            pen = new WintabPen();
            penAvailable = true;
        } catch (Throwable ex) {
            LOGGER.warn("Wintab unavailable (only Windows with installed Wacom driver is supported)", ex);
        }
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("artmap_pen", "controls"));
        calibrate = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.artmap_pen.calibrate", InputConstants.Type.KEYBOARD, InputConstants.KEY_F6, category));
        toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.artmap_pen.toggle", InputConstants.Type.KEYBOARD, InputConstants.KEY_F8, category));
        status = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.artmap_pen.debug", InputConstants.Type.KEYBOARD, InputConstants.KEY_F9, category));
        flipY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.artmap_pen.flip_y", InputConstants.Type.KEYBOARD, InputConstants.KEY_F10, category));
        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            painting = false;
            releaseClick(mc);
            if (pen != null) pen.close();
        });
        LOGGER.info("ArtMap Pen Absolute initialized; Wintab library loaded: {}", penAvailable);
    }

    /** Referenced by SDL mouse mixin, called on client thread only. */
    public static boolean captureMouse() {
        ArtMapPenClient mod = INSTANCE;
        if (mod == null || !mod.painting || !mod.config.cancelPhysicalMouseInput) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.gui.screen() == null && mc.isWindowActive();
    }

    private void onTick(Minecraft mc) {
        if (calibratedLevel != null && calibratedLevel != mc.level) {
            if (painting) stop(mc, "Мир изменился: нужна новая калибровка");
            calibration = null;
            calibratedLevel = null;
            referenceEye = null;
            nextCorner = 0;
        }
        while (calibrate.consumeClick()) captureCorner(mc);
        while (toggle.consumeClick()) toggle(mc);
        while (status.consumeClick()) showStatus(mc);
        while (flipY.consumeClick()) {
            config.flipY = !config.flipY;
            config.save();
            message(mc, "Инверсия Y: " + (config.flipY ? "ВКЛ" : "ВЫКЛ"));
        }
        if (!painting) return;
        if (mc.player == null || mc.level == null || calibration == null) {
            stop(mc, "Мир/калибровка недоступны");
            return;
        }
        if (referenceEye != null && mc.player.getEyePosition().distanceTo(referenceEye) > config.maxEyeMovementBlocks) {
            stop(mc, "Игрок сместился: нужна новая калибровка F6 ×4");
            calibration = null;
            nextCorner = 0;
            return;
        }
        // Do not interact with inventories, chat or other windows.
        if (mc.gui.screen() != null || !mc.isWindowActive() || pen == null
                || (pen.isOpen() && !pen.isMinecraftFocused())) {
            releaseClick(mc);
            if (pen != null) pen.close();
            return;
        }
        WintabPen.Sample sample;
        try {
            if (!pen.isOpen() && !pen.open()) {
                stop(mc, "Не удалось возобновить WinTab: " + pen.error());
                return;
            }
            sample = pen.poll(config.tipButtonMask, config.pressureThreshold);
        } catch (Throwable e) {
            LOGGER.warn("Wintab polling failed", e);
            stop(mc, "Сбой чтения WinTab; проверь драйвер");
            return;
        }
        if (sample == null) {
            if (++ticksWithNoPackets == 100) {
                message(mc, "Нет событий пера: проверь драйвер Wacom и Windows Ink / Wintab");
            }
            releaseClick(mc);
            return;
        }
        ticksWithNoPackets = 0;
        lastSample = sample;
        // A tap can finish before this tick. Aim at its contact point, not a later hover packet.
        double x = sample.tappedDuringPoll() && !sample.touching() ? sample.tapX() : sample.x();
        double y = sample.tappedDuringPoll() && !sample.touching() ? sample.tapY() : sample.y();
        double u = config.flipX ? 1.0 - x : x;
        double v = config.flipY ? 1.0 - y : y;
        CanvasAim.Angle look = calibration.aim(u, v);
        mc.player.setYRot(look.yaw());
        mc.player.setXRot(look.pitch());
        mc.player.setYHeadRot(look.yaw());
        boolean side1Now = sample.armed() && config.sideButton1Mask != 0
                && (sample.buttons() & config.sideButton1Mask) != 0;
        boolean side2Now = sample.armed() && config.sideButton2Mask != 0
                && (sample.buttons() & config.sideButton2Mask) != 0;
        if (side1Now && !side1PreviouslyDown) useSideButton(mc, config.sideButton1);
        if (side2Now && !side2PreviouslyDown) useSideButton(mc, config.sideButton2);
        side1PreviouslyDown = side1Now;
        side2PreviouslyDown = side2Now;
        KeyMapping key = drawKey(mc);
        // Initial click is the vanilla item-use/attack key, not a custom server packet.
        // Detect short taps that start and finish inside a single 20 Hz tick.
        if (sample.tappedDuringPoll()) KeyMapping.click(KeyMappingHelper.getBoundKeyOf(key));
        key.setDown(sample.touching());
        pressedByPen = sample.touching();
    }

    private void useSideButton(Minecraft mc, PenConfig.SideAction action) {
        if (action == PenConfig.SideAction.LEFT_CLICK)
            KeyMapping.click(KeyMappingHelper.getBoundKeyOf(mc.options.keyAttack));
        else if (action == PenConfig.SideAction.RIGHT_CLICK)
            KeyMapping.click(KeyMappingHelper.getBoundKeyOf(mc.options.keyUse));
    }

    private KeyMapping drawKey(Minecraft mc) {
        return config.drawButton == PenConfig.DrawButton.LEFT ? mc.options.keyAttack : mc.options.keyUse;
    }

    private void releaseClick(Minecraft mc) {
        if (pressedByPen) drawKey(mc).setDown(false);
        pressedByPen = false;
        side1PreviouslyDown = false;
        side2PreviouslyDown = false;
    }

    private void captureCorner(Minecraft mc) {
        if (mc.player == null || mc.gui.screen() != null || !mc.isWindowActive()) return;
        if (painting) stop(mc, "Для калибровки перо временно выключено");
        if (nextCorner == 0) {
            measured = new CanvasAim.Angle[4];
            calibration = null;
            referenceEye = mc.player.getEyePosition();
            calibratedLevel = mc.level;
        }
        if (referenceEye.distanceTo(mc.player.getEyePosition()) > config.maxEyeMovementBlocks) {
            nextCorner = 0;
            message(mc, "Ты сменил положение! Начни калибровку заново с левого верхнего угла.");
            return;
        }
        measured[nextCorner] = new CanvasAim.Angle(mc.player.getYRot(), mc.player.getXRot());
        int stored = nextCorner++;
        if (nextCorner < 4) {
            message(mc, "Угол " + (stored + 1) + "/4 записан. Наведи прицел на "
                    + CORNERS[nextCorner] + " угол холста и нажми F6.");
            return;
        }
        try {
            calibration = CanvasAim.fromCorners(measured);
            message(mc, "4/4: калибровка готова! Поднеси перо и нажми F8.");
        } catch (IllegalArgumentException ex) {
            calibration = null;
            message(mc, "Некорректные углы: " + ex.getMessage() + ". Начни F6 заново.");
        }
        nextCorner = 0;
    }

    private void toggle(Minecraft mc) {
        if (painting) {
            stop(mc, "Режим пера выключен");
            return;
        }
        if (mc.player == null || mc.gui.screen() != null || !mc.isWindowActive()) return;
        if (calibration == null) {
            message(mc, "Сначала откалибруй 4 угла: F6 (левый верхний, правый верхний, правый нижний, левый нижний)");
            return;
        }
        if (!penAvailable || pen == null) {
            message(mc, "WinTab недоступен: нужны Windows и официальный драйвер Wacom");
            return;
        }
        try {
            if (!pen.open()) {
                message(mc, "Нельзя открыть WinTab: " + pen.error());
                return;
            }
            lastSample = null;
            ticksWithNoPackets = 0;
            painting = true;
            mc.options.keyAttack.setDown(false);
            mc.options.keyUse.setDown(false);
            message(mc, "Перо ВКЛ: подними наконечник перед рисованием. F8 выключить, F9 диагностика.");
        } catch (Throwable ex) {
            LOGGER.warn("Cannot open tablet", ex);
            message(mc, "Ошибка WinTab: " + ex.getClass().getSimpleName());
        }
    }

    private void stop(Minecraft mc, String reason) {
        painting = false;
        releaseClick(mc);
        if (pen != null) pen.close();
        message(mc, reason);
    }

    private void showStatus(Minecraft mc) {
        String stage = calibration == null ? "нет" : "готова";
        String coords = lastSample == null ? "нет пакетов" : String.format(Locale.ROOT,
                "x=%.3f y=%.3f pressure=%d buttons=0x%X tip=%s",
                lastSample.x(), lastSample.y(), lastSample.pressure(),
                lastSample.buttons(), lastSample.touching());
        String packets = pen == null ? "-" : String.valueOf(pen.totalPackets());
        message(mc, "Перо=" + (painting ? "ON" : "OFF")
                + ", калибровка=" + stage + ", WinTab пакеты=" + packets + ", " + coords);
    }

    private static void message(Minecraft mc, String text) {
        if (mc.player != null) mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[ArtMapPen] " + text));
    }
}
