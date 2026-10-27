package com.spooky.client.module;

import com.spooky.client.render.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Stage 1: boxes, tracers and name/health/distance labels for other players. */
public final class PlayerEsp {
	private PlayerEsp() { }

	// ---- settings (a GUI comes later; for now edit here) ----
	public static boolean enabled = false;
	public static boolean boxes = true;
	public static boolean tracers = true;
	public static boolean labels = true;
	public static double maxDistance = 256.0;
	public static float boxWidth = 2.0f;

	public static void render() {
		if (!enabled) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;

		Vec3 eye = mc.player.getEyePosition();
		Vec3 tracerStart = eye.add(mc.player.getLookAngle().scale(1.0));

		for (Player p : mc.level.players()) {
			if (p == mc.player || p.isSpectator()) continue;
			double dist = mc.player.distanceTo(p);
			if (dist > maxDistance) continue;

			int color = colorFor(p, dist);

			if (boxes) {
				Draw.box(p.getBoundingBox(), color, boxWidth);
			}
			if (tracers) {
				Draw.line(tracerStart, p.getBoundingBox().getCenter(), color);
			}
			if (labels) {
				float hp = p.getHealth() + p.getAbsorptionAmount();
				String text = p.getName().getString() + "  " + Math.round(dist) + "m  " + Math.round(hp) + "hp";
				Draw.text(text, new Vec3(p.getX(), p.getY() + p.getBbHeight() + 0.6, p.getZ()), 0xFFFFFFFF);
			}
		}
	}

	/** Red when close, fading to yellow, then green far away. */
	private static int colorFor(Player p, double dist) {
		if (dist < 32) return ARGB.color(255, 255, 60, 60);
		if (dist < 96) return ARGB.color(255, 255, 200, 40);
		return ARGB.color(255, 80, 255, 120);
	}
}
