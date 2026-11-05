package dev.artmap.pen.math;

/**
 * Four-ray camera calibration for a flat painting seen from one fixed viewpoint.
 * Converts pen UV (0..1) to Minecraft yaw/pitch using a projective mapping.
 * No Minecraft APIs are used here, allowing deterministic unit tests.
 */
public final class CanvasAim {
    private final Vec3 forward;
    private final Vec3 right;
    private final Vec3 down;
    private final double a, b, c, d, e, f, g, h;

    private CanvasAim(Vec3 forward, Vec3 right, Vec3 down,
                      double a, double b, double c,
                      double d, double e, double f, double g, double h) {
        this.forward = forward;
        this.right = right;
        this.down = down;
        this.a = a; this.b = b; this.c = c;
        this.d = d; this.e = e; this.f = f;
        this.g = g; this.h = h;
    }

    /** Corners are ordered: top left, top right, bottom right, bottom left. */
    public static CanvasAim fromCorners(Angle[] corners) {
        if (corners == null || corners.length != 4) {
            throw new IllegalArgumentException("Four corners required");
        }
        for (Angle corner : corners) {
            if (corner == null || !Float.isFinite(corner.yaw) || !Float.isFinite(corner.pitch)
                    || Math.abs(corner.pitch) > 90) {
                throw new IllegalArgumentException("Invalid corner angle");
            }
        }
        Vec3 tl = direction(corners[0]), tr = direction(corners[1]);
        Vec3 br = direction(corners[2]), bl = direction(corners[3]);
        Vec3 forward = tl.plus(tr).plus(br).plus(bl).unit();
        Vec3 horizontal = tr.minus(tl).plus(br.minus(bl));
        Vec3 right = horizontal.minus(forward.times(horizontal.dot(forward))).unit();
        Vec3 vertical = bl.minus(tl).plus(br.minus(tr));
        Vec3 down = vertical.minus(forward.times(vertical.dot(forward)))
                .minus(right.times(vertical.dot(right))).unit();
        // Gnomonic projection, with forward as camera plane normal.
        Vec3[] rays = {tl, tr, br, bl};
        double[] x = new double[4], y = new double[4];
        for (int i = 0; i < 4; i++) {
            double denom = rays[i].dot(forward);
            if (denom <= 0.01) throw new IllegalArgumentException("Corner behind center ray");
            x[i] = rays[i].dot(right) / denom;
            y[i] = rays[i].dot(down) / denom;
        }
        // A crossed, concave or collapsed quad can put a projection pole inside the canvas.
        double winding = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4, k = (i + 2) % 4;
            double cross = (x[j] - x[i]) * (y[k] - y[j])
                    - (y[j] - y[i]) * (x[k] - x[j]);
            if (Math.abs(cross) < 1e-10 || (i > 0 && cross * winding <= 0))
                throw new IllegalArgumentException("Corners must form a convex canvas in TL/TR/BR/BL order");
            winding = cross;
        }
        // Square-to-quad homography with a perspective term (g,h).
        double dx1 = x[1] - x[2], dx2 = x[3] - x[2];
        double dy1 = y[1] - y[2], dy2 = y[3] - y[2];
        double dx3 = x[0] - x[1] + x[2] - x[3];
        double dy3 = y[0] - y[1] + y[2] - y[3];
        double g = 0, h = 0;
        if (Math.abs(dx3) + Math.abs(dy3) > 1e-12) {
            double den = dx1 * dy2 - dx2 * dy1;
            if (Math.abs(den) < 1e-10) throw new IllegalArgumentException("Degenerate canvas corners");
            g = (dx3 * dy2 - dx2 * dy3) / den;
            h = (dx1 * dy3 - dx3 * dy1) / den;
        }
        if (Math.min(Math.min(1 + g, 1 + h), 1 + g + h) <= 1e-8)
            throw new IllegalArgumentException("Projection crosses the canvas");
        double a = x[1] - x[0] + g * x[1];
        double b = x[3] - x[0] + h * x[3];
        double c = x[0];
        double d = y[1] - y[0] + g * y[1];
        double e = y[3] - y[0] + h * y[3];
        double f = y[0];
        return new CanvasAim(forward, right, down, a, b, c, d, e, f, g, h);
    }

    public Angle aim(double u, double v) {
        if (!Double.isFinite(u) || !Double.isFinite(v))
            throw new IllegalArgumentException("Non-finite pen coordinates");
        u = clamp(u, 0, 1);
        v = clamp(v, 0, 1);
        double divisor = g * u + h * v + 1;
        if (Math.abs(divisor) < 1e-8) throw new IllegalStateException("Invalid calibration projection");
        double x = (a * u + b * v + c) / divisor;
        double y = (d * u + e * v + f) / divisor;
        Vec3 ray = forward.plus(right.times(x)).plus(down.times(y)).unit();
        return new Angle((float)Math.toDegrees(Math.atan2(-ray.x, ray.z)),
                (float)-Math.toDegrees(Math.asin(clamp(ray.y, -1, 1))));
    }

    private static Vec3 direction(Angle angle) {
        double yaw = Math.toRadians(angle.yaw);
        double pitch = Math.toRadians(angle.pitch);
        return new Vec3(-Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    public record Angle(float yaw, float pitch) { }

    private record Vec3(double x, double y, double z) {
        Vec3 plus(Vec3 v) { return new Vec3(x + v.x, y + v.y, z + v.z); }
        Vec3 minus(Vec3 v) { return new Vec3(x - v.x, y - v.y, z - v.z); }
        Vec3 times(double d) { return new Vec3(x * d, y * d, z * d); }
        double dot(Vec3 v) { return x * v.x + y * v.y + z * v.z; }
        Vec3 unit() {
            double len = Math.sqrt(dot(this));
            if (!Double.isFinite(len) || len < 1e-9)
                throw new IllegalArgumentException("Unusable corner calibration");
            return times(1.0 / len);
        }
    }
}
