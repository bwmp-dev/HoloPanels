package dev.aether.holopanels.render;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelGeometryTest {
    private final World world = (World) Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[]{World.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getName")) {
                    return "test";
                }
                if (method.getName().equals("equals")) {
                    return proxy == args[0];
                }
                Class<?> type = method.getReturnType();
                if (type.equals(boolean.class)) return false;
                if (type.equals(int.class)) return 0;
                if (type.equals(long.class)) return 0L;
                if (type.equals(double.class)) return 0.0;
                if (type.equals(float.class)) return 0.0F;
                return null;
            });

    @Test
    void hitsForwardFacingPanel() {
        Location eye = new Location(world, 0.0, 1.0, -3.0, 0.0F, 0.0F);
        Location center = new Location(world, 0.0, 1.0, 0.0, 0.0F, 0.0F);

        PanelGeometry.Hit hit = PanelGeometry.hitCentered(eye, center, 4.0, 2.0, 6.0).orElseThrow();

        assertEquals(3.0, hit.distance(), 0.0001);
        assertEquals(0.0, hit.horizontal(), 0.0001);
        assertEquals(0.0, hit.vertical(), 0.0001);
    }

    @Test
    void hitsPanelAtNinetyDegreeYaw() {
        Location eye = new Location(world, 3.0, 1.0, 0.0, 90.0F, 0.0F);
        Location center = new Location(world, 0.0, 1.0, 0.0, 90.0F, 0.0F);

        assertTrue(PanelGeometry.hitCentered(eye, center, 4.0, 2.0, 6.0).isPresent());
    }

    @Test
    void rejectsHitOutsidePanelWidth() {
        Location eye = new Location(world, 3.0, 1.0, -3.0);
        eye.setDirection(new Vector(0.0, 0.0, 1.0));
        Location center = new Location(world, 0.0, 1.0, 0.0, 0.0F, 0.0F);

        assertTrue(PanelGeometry.hitCentered(eye, center, 4.0, 2.0, 6.0).isEmpty());
    }

    @Test
    void offsetsInReaderRelativeAxes() {
        Location anchor = new Location(world, 10.0, 20.0, 30.0, 0.0F, 0.0F);

        Location result = PanelGeometry.offset(anchor, 2.0, 3.0, 1.0);

        assertEquals(12.0, result.getX(), 0.0001);
        assertEquals(23.0, result.getY(), 0.0001);
        assertEquals(31.0, result.getZ(), 0.0001);
    }
}
