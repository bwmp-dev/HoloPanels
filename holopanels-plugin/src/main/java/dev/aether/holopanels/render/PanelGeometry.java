package dev.aether.holopanels.render;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.Optional;

public final class PanelGeometry {
    private PanelGeometry() {
    }

    public static Vector normalFor(Location location) {
        Location copy = location.clone();
        copy.setPitch(0.0F);
        return copy.getDirection().normalize();
    }

    public static Vector rightFor(Location location) {
        Vector normal = normalFor(location);
        return new Vector(normal.getZ(), 0.0, -normal.getX()).normalize();
    }

    /**
     * The yaw a board needs so that {@link #normalFor} points along
     * {@code direction} — that is, so the board faces that way.
     * <p>
     * The Y component is ignored, because a board's normal is always
     * horizontal. A direction with no horizontal component has no answer, so
     * the caller gets an empty result rather than an arbitrary yaw.
     */
    public static Optional<Float> yawFacing(Vector direction) {
        double x = direction.getX();
        double z = direction.getZ();
        if (Math.abs(x) < 0.0001 && Math.abs(z) < 0.0001) {
            return Optional.empty();
        }
        return Optional.of(normalizeYaw((float) Math.toDegrees(Math.atan2(-x, z))));
    }

    /** Folded into (-180, 180], which is the range Minecraft itself stores. */
    public static float normalizeYaw(float yaw) {
        float wrapped = yaw % 360.0F;
        if (wrapped <= -180.0F) {
            wrapped += 360.0F;
        } else if (wrapped > 180.0F) {
            wrapped -= 360.0F;
        }
        // -0.0F compares equal to 0.0F but serializes as "-0.0", which reads
        // like a mistake in boards.yml.
        return wrapped == 0.0F ? 0.0F : wrapped;
    }

    public static Location offset(Location anchor, double right, double up, double forward) {
        return anchor.clone()
                .add(rightFor(anchor).multiply(right))
                .add(normalFor(anchor).multiply(forward))
                .add(0.0, up, 0.0);
    }

    public record Hit(double distance, double horizontal, double vertical) {
    }

    public static Optional<Hit> hitCentered(
            Location eye,
            Location center,
            double width,
            double height,
            double maxDistance
    ) {
        if (eye.getWorld() == null || !eye.getWorld().equals(center.getWorld())) {
            return Optional.empty();
        }

        Vector origin = eye.toVector();
        Vector direction = eye.getDirection().normalize();
        Vector normal = normalFor(center);
        double denominator = direction.dot(normal);
        if (Math.abs(denominator) < 0.0001) {
            return Optional.empty();
        }

        double distance = center.toVector().subtract(origin).dot(normal) / denominator;
        if (distance < 0.0 || distance > maxDistance) {
            return Optional.empty();
        }

        Vector offset = origin.clone().add(direction.multiply(distance)).subtract(center.toVector());
        double vertical = offset.getY();
        if (Math.abs(vertical) > height / 2.0) {
            return Optional.empty();
        }

        double horizontal = offset.dot(rightFor(center));
        if (Math.abs(horizontal) > width / 2.0) {
            return Optional.empty();
        }
        return Optional.of(new Hit(distance, horizontal, vertical));
    }
}
