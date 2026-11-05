package dev.artmap.pen.mixin;

import dev.artmap.pen.ArtMapPenClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft Java 26.3 uses SDL3 relative mouse input rather than GLFW.
 * Suppress emulated mouse movement and clicks *only while tablet drawing is active*.
 * Real pen positions instead drive view rotation via Wintab.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onMove(JDDDD)V", at = @At("HEAD"), cancellable = true)
    private void artmap_pen$cancelMouseLook(long handle, double x, double y,
                                           double dx, double dy, CallbackInfo ci) {
        if (ArtMapPenClient.captureMouse()) ci.cancel();
    }

    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V",
            at = @At("HEAD"), cancellable = true)
    private void artmap_pen$cancelMouseButtons(long handle, MouseButtonInfo buttonInfo,
                                              int action, CallbackInfo ci) {
        if (ArtMapPenClient.captureMouse()) ci.cancel();
    }
}
