package com.bame.client.playerwheel;

import java.util.Random;

/**
 * Prüft ohne Minecraft die Strahl-Rechnung des Spieler-Pickings ({@link MouseRay}): Maus → NDC (auch über die
 * GUI-Skalierung), Inverse der Projektion bei verschiedenen FOV (inklusive Zoom) und Seitenverhältnissen, Richtung in
 * der Bildmitte und an den Rändern, Hin- und Rückprojektion, Kameradrehung wie Minecraft und Strahl gegen Hitbox.
 * Gradle-Aufgabe {@code mouseRayTest} (läuft mit {@code check}/{@code build}).
 */
public final class MouseRayTest {
    private static int checks = 0;

    /** FOV in Grad: Vanilla 30-110, Quake Pro, Zoom (Zoom-Modul teilt den FOV) und Sprint-Aufweitung. */
    private static final double[] FOVS = {7.0, 10.0, 17.5, 30.0, 50.0, 70.0, 77.0, 90.0, 110.0, 126.5};
    /** Seitenverhältnisse: 4:3, 16:9, 16:10, 21:9, quadratisch, hochkant, 1366x768. */
    private static final double[] ASPECTS = {4.0 / 3.0, 16.0 / 9.0, 1.6, 21.0 / 9.0, 1.0, 9.0 / 16.0, 1366.0 / 768.0};
    private static final double[] FARS = {64.0, 256.0, 1024.0, 4096.0};
    /** Fenster (Framebuffer-Pixel). */
    private static final int[][] WINDOWS = {
            {854, 480}, {1280, 720}, {1366, 768}, {1600, 900}, {1920, 1080}, {2560, 1440}, {3840, 2160},
            {1280, 1024}, {800, 600}, {1093, 614}, {1001, 777}
    };

    public static void main(String[] args) {
        ndcConversions();
        guiScaleEquivalence();
        matrixInverse();
        centerAndEdges();
        translationKeepsDirection();
        roundTripProjection();
        rotationMatchesMinecraft();
        fullPipeline();
        rayBox();
        System.out.println("MOUSE RAY TESTS PASSED (" + checks + " checks)");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    private static boolean near(double a, double b, double eps) {
        return Math.abs(a - b) <= eps;
    }

    // ------------------------------------------------------------------------------------------------------------

    private static void ndcConversions() {
        check(MouseRay.ndcX(0, 800) == -1.0 && MouseRay.ndcX(800, 800) == 1.0 && MouseRay.ndcX(400, 800) == 0.0, "ndc x edges/centre");
        check(MouseRay.ndcY(0, 600) == 1.0 && MouseRay.ndcY(600, 600) == -1.0 && MouseRay.ndcY(300, 600) == 0.0, "ndc y: top = +1");
        check(MouseRay.ndcX(5, 0) == 0.0 && MouseRay.ndcY(5, 0) == 0.0, "zero size does not divide by zero");
        for (int x = 0; x <= 1000; x += 7) {
            check(MouseRay.ndcX(x, 1000) <= MouseRay.ndcX(x + 1, 1000), "ndc x monotonic");
        }
    }

    /** GUI-Skalierung: Mausposition in GUI-Pixeln ergibt dieselbe NDC wie in Fensterpixeln; Zeichnen passt dazu. */
    private static void guiScaleEquivalence() {
        Random random = new Random(7);
        for (int[] win : WINDOWS) {
            int w = win[0], h = win[1];
            for (int scale = 1; scale <= 6; scale++) {
                if (w / scale < 320 || h / scale < 240) continue; // höher lässt Minecraft die GUI-Skalierung nicht zu
                int sw = MouseRay.scaledSize(w, scale);
                int sh = MouseRay.scaledSize(h, scale);
                check(sw == (int) Math.ceil(w / (double) scale) && sh == (int) Math.ceil(h / (double) scale), "scaled size like Window");
                double[] xs = {0, 1, w / 3.0, w / 2.0, w - 1, w, random.nextDouble() * w, random.nextDouble() * w};
                for (double x : xs) {
                    double gx = MouseRay.guiFromWindow(x, w, sw);
                    check(near(MouseRay.ndcX(gx, sw), MouseRay.ndcX(x, w), 1e-12), "gui and window give the same ndc x");
                    double ndc = MouseRay.ndcX(x, w);
                    double drawn = MouseRay.guiFromNdcX(ndc, w, scale);
                    check(near(drawn * scale, x, 1e-9), "drawn position hits the same framebuffer pixel");
                    check(Math.abs(drawn - gx) < 1.0, "mouse gui position and drawn position differ by < 1 gui px");
                }
                double[] ys = {0, h / 2.0, h, random.nextDouble() * h};
                for (double y : ys) {
                    double gy = MouseRay.guiFromWindow(y, h, sh);
                    check(near(MouseRay.ndcY(gy, sh), MouseRay.ndcY(y, h), 1e-12), "gui and window give the same ndc y");
                    double drawn = MouseRay.guiFromNdcY(MouseRay.ndcY(y, h), h, scale);
                    check(near(drawn * scale, y, 1e-9), "drawn y hits the same framebuffer pixel");
                }
            }
        }
    }

    private static void matrixInverse() {
        double[] p = new double[16];
        double[] inv = new double[16];
        double[] prod = new double[16];
        for (double fov : FOVS) {
            for (double aspect : ASPECTS) {
                for (double far : FARS) {
                    MouseRay.perspective(Math.toRadians(fov), aspect, 0.05, far, p);
                    check(MouseRay.invert(p, inv), "perspective is invertible");
                    MouseRay.multiply(p, inv, prod);
                    for (int i = 0; i < 16; i++) {
                        double expected = (i % 5 == 0) ? 1.0 : 0.0;
                        check(near(prod[i], expected, 1e-6), "P * P^-1 = I (fov " + fov + ", aspect " + aspect + ", far " + far + ")");
                    }
                }
            }
        }
        double[] zero = new double[16];
        double[] out = new double[16];
        out[3] = 42.0;
        check(!MouseRay.invert(zero, out) && out[3] == 42.0, "singular matrix rejected, output untouched");
        // Allgemeine Matrix (Drehung, Skalierung, Verschiebung): invert(invert(M)) = M
        double[] m = {0.8, 0.1, -0.3, 0, -0.2, 1.1, 0.4, 0, 0.5, -0.6, 0.9, 0, 3, -7, 11, 1};
        double[] mi = new double[16];
        double[] back = new double[16];
        check(MouseRay.invert(m, mi) && MouseRay.invert(mi, back), "general matrix invertible");
        for (int i = 0; i < 16; i++) check(near(back[i], m[i], 1e-9), "double inverse returns the matrix");
        double[] same = m.clone();
        check(MouseRay.invert(same, same), "in-place inverse");
        for (int i = 0; i < 16; i++) check(near(same[i], mi[i], 1e-12), "in-place inverse equals out-of-place");
    }

    private static void centerAndEdges() {
        double[] p = new double[16];
        double[] inv = new double[16];
        double[] dir = new double[3];
        double[] tmp = new double[3];
        for (double fov : FOVS) {
            for (double aspect : ASPECTS) {
                MouseRay.perspective(Math.toRadians(fov), aspect, 0.05, 1024.0, p);
                MouseRay.invert(p, inv);
                double t = Math.tan(Math.toRadians(fov) / 2.0);
                String c = " [fov " + fov + " aspect " + aspect + "]";

                check(MouseRay.viewDirection(inv, 0, 0, dir, tmp), "centre ray" + c);
                check(near(dir[0], 0, 1e-9) && near(dir[1], 0, 1e-9) && near(dir[2], -1, 1e-9), "centre looks along -z" + c);

                MouseRay.viewDirection(inv, 0, 1, dir, tmp);
                check(near(Math.atan2(dir[1], -dir[2]), Math.toRadians(fov) / 2.0, 1e-7), "top edge = half fov up" + c);
                MouseRay.viewDirection(inv, 0, -1, dir, tmp);
                check(near(Math.atan2(dir[1], -dir[2]), -Math.toRadians(fov) / 2.0, 1e-7), "bottom edge = half fov down" + c);
                MouseRay.viewDirection(inv, 1, 0, dir, tmp);
                check(near(Math.atan2(dir[0], -dir[2]), Math.atan(aspect * t), 1e-7), "right edge from aspect and fov" + c);
                MouseRay.viewDirection(inv, -1, 0, dir, tmp);
                check(near(Math.atan2(dir[0], -dir[2]), -Math.atan(aspect * t), 1e-7), "left edge" + c);
                for (double nx = -1; nx <= 1.0001; nx += 0.25) {
                    for (double ny = -1; ny <= 1.0001; ny += 0.25) {
                        check(MouseRay.viewDirection(inv, nx, ny, dir, tmp), "ray exists" + c);
                        check(near(Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]), 1.0, 1e-9), "normalised" + c);
                        check(dir[2] < 0, "always in front of the camera" + c);
                        check(near(dir[0] / -dir[2], nx * aspect * t, 1e-7) && near(dir[1] / -dir[2], ny * t, 1e-7),
                                "linear in ndc (pinhole camera)" + c);
                    }
                }
            }
        }
    }

    /** Wackeln beim Laufen verschiebt die Projektion; die Richtung des Strahls bleibt gleich. */
    private static void translationKeepsDirection() {
        double[] p = new double[16];
        double[] t = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0.03, -0.05, 0.0, 1};
        double[] pt = new double[16];
        double[] inv = new double[16];
        double[] invT = new double[16];
        double[] a = new double[3];
        double[] b = new double[3];
        double[] tmp = new double[3];
        MouseRay.perspective(Math.toRadians(70), 16.0 / 9.0, 0.05, 512, p);
        MouseRay.multiply(p, t, pt);
        MouseRay.invert(p, inv);
        MouseRay.invert(pt, invT);
        for (double nx = -1; nx <= 1.0001; nx += 0.5) {
            for (double ny = -1; ny <= 1.0001; ny += 0.5) {
                MouseRay.viewDirection(inv, nx, ny, a, tmp);
                MouseRay.viewDirection(invT, nx, ny, b, tmp);
                check(near(a[0], b[0], 1e-6) && near(a[1], b[1], 1e-6) && near(a[2], b[2], 1e-6), "translation does not change the direction");
            }
        }
    }

    private static void roundTripProjection() {
        Random random = new Random(42);
        double[] p = new double[16];
        double[] inv = new double[16];
        double[] dir = new double[3];
        double[] tmp = new double[3];
        double[] ndc = new double[3];
        for (double fov : FOVS) {
            for (double aspect : ASPECTS) {
                MouseRay.perspective(Math.toRadians(fov), aspect, 0.05, 2048.0, p);
                MouseRay.invert(p, inv);
                for (int i = 0; i < 60; i++) {
                    double nx = random.nextDouble() * 2 - 1;
                    double ny = random.nextDouble() * 2 - 1;
                    double yaw = random.nextDouble() * 720 - 360;
                    double pitch = random.nextDouble() * 180 - 90;
                    check(MouseRay.worldDirection(inv, yaw, pitch, nx, ny, dir, tmp), "world ray");
                    for (double d : new double[]{0.5, 3.0, 20.0, 47.9}) {
                        check(MouseRay.projectRelative(p, yaw, pitch, dir[0] * d, dir[1] * d, dir[2] * d, ndc), "point in front projects");
                        check(near(ndc[0], nx, 1e-6) && near(ndc[1], ny, 1e-6), "round trip mouse -> ray -> point -> mouse"
                                + " [fov " + fov + " yaw " + yaw + " pitch " + pitch + " d " + d + "]");
                        check(!MouseRay.projectRelative(p, yaw, pitch, -dir[0] * d, -dir[1] * d, -dir[2] * d, ndc), "point behind the camera rejected");
                    }
                }
            }
        }
    }

    /** Kameradrehung wie Minecraft: Blickrichtung = Entity.getRotationVector(pitch, yaw). */
    private static void rotationMatchesMinecraft() {
        double[] out = new double[3];
        double[] back = new double[3];
        for (int yaw = -360; yaw <= 360; yaw += 15) {
            for (int pitch = -90; pitch <= 90; pitch += 15) {
                double f = Math.toRadians(pitch);
                double g = Math.toRadians(-yaw);
                double ex = Math.sin(g) * Math.cos(f), ey = -Math.sin(f), ez = Math.cos(g) * Math.cos(f);
                MouseRay.rotateToWorld(yaw, pitch, 0, 0, -1, out);
                check(near(out[0], ex, 1e-9) && near(out[1], ey, 1e-9) && near(out[2], ez, 1e-9),
                        "forward = Minecraft look vector (yaw " + yaw + ", pitch " + pitch + ")");
                double[] v = {0.3, -0.7, 0.648};
                MouseRay.rotateToWorld(yaw, pitch, v[0], v[1], v[2], out);
                MouseRay.rotateToView(yaw, pitch, out[0], out[1], out[2], back);
                check(near(back[0], v[0], 1e-9) && near(back[1], v[1], 1e-9) && near(back[2], v[2], 1e-9), "rotateToView undoes rotateToWorld");
            }
            MouseRay.rotateToWorld(yaw, 0, 0, 1, 0, out);
            check(near(out[0], 0, 1e-9) && near(out[1], 1, 1e-9) && near(out[2], 0, 1e-9), "up stays up without pitch");
            MouseRay.rotateToWorld(yaw, 0, 1, 0, 0, out);
            double r = Math.toRadians(yaw);
            check(near(out[0], -Math.cos(r), 1e-9) && near(out[2], -Math.sin(r), 1e-9), "screen right = player's right hand side");
        }
        MouseRay.rotateToWorld(0, 0, 1, 0, 0, out);
        check(near(out[0], -1, 1e-9), "facing south (+z): right is west (-x)");
        MouseRay.rotateToWorld(0, 90, 0, 0, -1, out);
        check(near(out[1], -1, 1e-9), "pitch 90 looks straight down");
    }

    /** Ganzer Weg: Spieler in der Welt → Mausposition (Fenster und GUI) → Strahl trifft ihn, daneben nicht. */
    private static void fullPipeline() {
        Random random = new Random(1234);
        double[] p = new double[16];
        double[] inv = new double[16];
        double[] ndc = new double[3];
        double[] dir = new double[3];
        double[] tmp = new double[3];
        for (int[] win : WINDOWS) {
            int w = win[0], h = win[1];
            for (int scale = 1; scale <= 4; scale++) {
                if (w / scale < 320 || h / scale < 240) continue;
                int sw = MouseRay.scaledSize(w, scale);
                int sh = MouseRay.scaledSize(h, scale);
                for (double fov : new double[]{30, 70, 110, 10}) {
                    MouseRay.perspective(Math.toRadians(fov), w / (double) h, 0.05, 1024.0, p);
                    MouseRay.invert(p, inv);
                    for (int i = 0; i < 25; i++) {
                        double yaw = random.nextDouble() * 360 - 180;
                        double pitch = random.nextDouble() * 120 - 60;
                        // Ziel irgendwo im Bild, 2-45 Blöcke entfernt
                        MouseRay.worldDirection(inv, yaw, pitch, random.nextDouble() * 1.8 - 0.9, random.nextDouble() * 1.8 - 0.9, dir, tmp);
                        double dist = 2 + random.nextDouble() * 43;
                        double tx = dir[0] * dist, ty = dir[1] * dist, tz = dir[2] * dist;
                        check(MouseRay.projectRelative(p, yaw, pitch, tx, ty, tz, ndc), "target on screen");
                        double windowX = (ndc[0] + 1) / 2 * w;
                        double windowY = (1 - ndc[1]) / 2 * h;
                        double guiX = MouseRay.guiFromWindow(windowX, w, sw);
                        double guiY = MouseRay.guiFromWindow(windowY, h, sh);
                        MouseRay.worldDirection(inv, yaw, pitch, MouseRay.ndcX(guiX, sw), MouseRay.ndcY(guiY, sh), dir, tmp);
                        double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
                        double cos = (dir[0] * tx + dir[1] * ty + dir[2] * tz) / len;
                        check(cos > 1 - 1e-9, "ray through the mouse points at the target");
                        // Spieler-Hitbox 0,6 x 1,8 um das Ziel: Treffer; 2 Blöcke daneben: kein Treffer
                        double hit = MouseRay.rayBox(0, 0, 0, dir[0], dir[1], dir[2], tx - 0.3, ty - 0.9, tz - 0.3, tx + 0.3, ty + 0.9, tz + 0.3, 48);
                        check(hit >= 0 && hit <= len + 1e-9, "hitbox around the target is hit");
                        double side = MouseRay.rayBox(0, 0, 0, dir[0], dir[1], dir[2], tx + 2, ty - 0.9, tz + 2, tx + 2.6, ty + 0.9, tz + 2.6, 48);
                        if (side >= 0) {
                            // nur möglich, wenn die verschobene Box zufällig genau hinter/vor dem Ziel liegt
                            double px = dir[0] * side, py = dir[1] * side, pz = dir[2] * side;
                            check(px >= tx + 2 - 1e-6 && px <= tx + 2.6 + 1e-6 && pz >= tz + 2 - 1e-6 && pz <= tz + 2.6 + 1e-6
                                    && py >= ty - 0.9 - 1e-6 && py <= ty + 0.9 + 1e-6, "a reported side hit is really inside that box");
                        }
                        double blocked = MouseRay.rayBox(0, 0, 0, dir[0], dir[1], dir[2], tx - 0.3, ty - 0.9, tz - 0.3, tx + 0.3, ty + 0.9, tz + 0.3, len * 0.5);
                        check(blocked < 0, "a wall halfway (shorter max distance) blocks the pick");
                    }
                }
            }
        }
    }

    private static void rayBox() {
        check(near(MouseRay.rayBox(-5, 0.5, 0.5, 1, 0, 0, 0, 0, 0, 1, 1, 1, 100), 5, 1e-12), "straight hit at 5");
        check(MouseRay.rayBox(0.5, 0.5, 0.5, 1, 0, 0, 0, 0, 0, 1, 1, 1, 100) == 0, "origin inside = distance 0");
        check(MouseRay.rayBox(5, 0.5, 0.5, 1, 0, 0, 0, 0, 0, 1, 1, 1, 100) < 0, "box behind the ray");
        check(MouseRay.rayBox(-5, 0.5, 0.5, 1, 0, 0, 0, 0, 0, 1, 1, 1, 4.9) < 0, "max distance too short");
        check(near(MouseRay.rayBox(-5, 0.5, 0.5, 1, 0, 0, 0, 0, 0, 1, 1, 1, 5.0), 5, 1e-12), "max distance exactly reaches the box");
        check(MouseRay.rayBox(-5, 2, 0.5, 1, 0, 0, 0, 0, 0, 1, 1, 1, 100) < 0, "parallel ray outside the slab");
        check(MouseRay.rayBox(-5, 1, 1, 1, 0, 0, 0, 0, 0, 1, 1, 1, 100) >= 0, "ray along an edge counts as hit");
        double s = Math.sqrt(0.5);
        check(near(MouseRay.rayBox(-1, -1, 0.5, s, s, 0, 0, 0, 0, 1, 1, 1, 100), Math.sqrt(2), 1e-9), "diagonal through the corner");
        check(MouseRay.rayBox(-1, 0, 0.5, s, s, 0, 0.5, 0, 0, 1, 0.4, 1, 100) < 0, "diagonal passes above a flat box");
        // Zufallsvergleich mit Abtasten entlang des Strahls
        Random random = new Random(99);
        for (int i = 0; i < 4000; i++) {
            double ox = random.nextDouble() * 10 - 5, oy = random.nextDouble() * 10 - 5, oz = random.nextDouble() * 10 - 5;
            double dx = random.nextGaussian(), dy = random.nextGaussian(), dz = random.nextGaussian();
            if (i % 5 == 0) dy = 0;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= len;
            dy /= len;
            dz /= len;
            double minX = random.nextDouble() * 4 - 2, minY = random.nextDouble() * 4 - 2, minZ = random.nextDouble() * 4 - 2;
            double maxX = minX + 0.2 + random.nextDouble(), maxY = minY + 0.2 + random.nextDouble() * 2, maxZ = minZ + 0.2 + random.nextDouble();
            double maxT = 2 + random.nextDouble() * 10;
            double t = MouseRay.rayBox(ox, oy, oz, dx, dy, dz, minX, minY, minZ, maxX, maxY, maxZ, maxT);
            double sampled = -1;
            for (double u = 0; u <= maxT; u += 0.002) {
                double x = ox + dx * u, y = oy + dy * u, z = oz + dz * u;
                if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) {
                    sampled = u;
                    break;
                }
            }
            if (sampled >= 0) check(t >= 0 && t <= sampled + 1e-9, "sampling found the box, slab test must hit no later");
            if (t >= 0) {
                double u = Math.min(maxT, t + 1e-7);
                double x = ox + dx * u, y = oy + dy * u, z = oz + dz * u;
                check(x >= minX - 1e-6 && x <= maxX + 1e-6 && y >= minY - 1e-6 && y <= maxY + 1e-6 && z >= minZ - 1e-6 && z <= maxZ + 1e-6,
                        "reported entry point lies on the box");
                check(t <= maxT, "hit within the max distance");
            }
        }
    }
}
