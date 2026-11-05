package dev.artmap.pen;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;

/** Checks exact mixin descriptors without initializing Minecraft or requiring a GPU. */
public final class MinecraftCompatibilitySelfTest {
    private static void method(String owner, String name, String descriptor) throws IOException {
        boolean[] found = {false};
        try (var stream = MinecraftCompatibilitySelfTest.class.getResourceAsStream("/" + owner + ".class")) {
            if (stream == null) throw new AssertionError("Missing Minecraft class: " + owner);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String methodName,
                        String desc, String signature, String[] exceptions) {
                    if (name.equals(methodName) && descriptor.equals(desc)) found[0] = true;
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        if (!found[0]) throw new AssertionError("Missing mixin target: " + owner + "." + name + descriptor);
    }

    public static void main(String[] args) throws IOException {
        method("net/minecraft/client/MouseHandler", "onMove", "(JDDDD)V");
        method("net/minecraft/client/MouseHandler", "onButton",
                "(JLnet/minecraft/client/input/MouseButtonInfo;I)V");
        System.out.println("MinecraftCompatibilitySelfTest: OK (Minecraft 26.3 mixin targets)");
    }
}
