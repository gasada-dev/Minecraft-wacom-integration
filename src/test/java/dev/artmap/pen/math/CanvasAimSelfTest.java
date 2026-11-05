package dev.artmap.pen.math;

/** Standalone geometry test: can run with any JDK >= 17. */
public final class CanvasAimSelfTest {
    private static void check(float actual, float expected) {
        if (!Float.isFinite(actual) || Math.abs(actual - expected) > 0.001f)
            throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) {
        var corners = new CanvasAim.Angle[] {
            new CanvasAim.Angle(-12, -9),
            new CanvasAim.Angle(12, -9),
            new CanvasAim.Angle(12, 9),
            new CanvasAim.Angle(-12, 9)
        };
        var aim = CanvasAim.fromCorners(corners);
        var uvs = new double[][] {{0,0},{1,0},{1,1},{0,1}};
        for (int i = 0; i < 4; i++) {
            var result = aim.aim(uvs[i][0], uvs[i][1]);
            check(result.yaw(), corners[i].yaw());
            check(result.pitch(), corners[i].pitch());
        }
        var center = aim.aim(0.5, 0.5);
        check(center.yaw(), 0);
        check(center.pitch(), 0);
        // A rectangular plane viewed obliquely. Interior points must interpolate on the
        // physical plane rather than linearly interpolating yaw and pitch.
        for (double yawOffset : new double[] {0, 179, -179, 90}) {
            var perspective = new CanvasAim.Angle[] {
                angle(-1, 0.8, 2, yawOffset), angle(1, 0.8, 3, yawOffset),
                angle(1, -0.8, 3, yawOffset), angle(-1, -0.8, 2, yawOffset)
            };
            var projection = CanvasAim.fromCorners(perspective);
            for (int i = 0; i <= 10; i++) {
                for (int j = 0; j <= 10; j++) {
                    double u = i / 10.0, v = j / 10.0;
                    var actual = projection.aim(u, v);
                    var expected = angle(-1 + 2*u, 0.8 - 1.6*v, 2 + u, yawOffset);
                    check(wrap(actual.yaw() - expected.yaw()), 0);
                    check(actual.pitch(), expected.pitch());
                }
            }
        }
        check(aim.aim(-2, -2).yaw(), corners[0].yaw());
        reject(new CanvasAim.Angle[] {corners[0], corners[2], corners[1], corners[3]});
        reject(new CanvasAim.Angle[] {corners[0], corners[0], corners[0], corners[0]});
        reject(new CanvasAim.Angle[] {null, corners[1], corners[2], corners[3]});
        reject(new CanvasAim.Angle[] {new CanvasAim.Angle(Float.NaN, 0), corners[1], corners[2], corners[3]});
        try {
            aim.aim(Double.NaN, 0);
            throw new AssertionError("NaN must be rejected");
        } catch (IllegalArgumentException expected) { }
        System.out.println("CanvasAimSelfTest: OK");
    }

    private static CanvasAim.Angle angle(double x, double y, double z, double offset) {
        double length = Math.sqrt(x*x + y*y + z*z);
        return new CanvasAim.Angle(wrap((float)(Math.toDegrees(Math.atan2(-x, z)) + offset)),
                (float)-Math.toDegrees(Math.asin(y / length)));
    }

    private static float wrap(float yaw) { return ((yaw + 180) % 360 + 360) % 360 - 180; }

    private static void reject(CanvasAim.Angle[] corners) {
        try {
            CanvasAim.fromCorners(corners);
            throw new AssertionError("Invalid canvas accepted");
        } catch (IllegalArgumentException expected) { }
    }
}
