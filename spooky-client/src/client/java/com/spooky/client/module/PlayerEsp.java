package com.spooky.client.module;

import com.spooky.client.render.Draw;
import com.spooky.client.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Flat 2D-style boxes that always face you, tracers and labels for other players.
 * Positions are interpolated with the partial tick so nothing jitters while you move.
 */
public final class PlayerEsp extends Module {
	public final Setting.Bool boxes = add(new Setting.Bool("Boxes", true));
	public final Setting.Mode style = add(new Setting.Mode("Box style", "Corners", "Full"));
	public final Setting.Num thickness = add(new Setting.Num("Line thickness", 2, 1, 4, 1));
	public final Setting.Bool tracers = add(new Setting.Bool("Tracers", true));
	public final Setting.Bool labels = add(new Setting.Bool("Labels", true));
	public final Setting.Bool showDistance = add(new Setting.Bool("Show distance", true));
	public final Setting.Bool showHealth = add(new Setting.Bool("Show health", true));
	public final Setting.Num maxDistance = add(new Setting.Num("Max distance", 256, 16, 512, 16));
	public final Setting.Rgb color = add(new Setting.Rgb("Color", 0xFF4040));
	public final Setting.Bool distanceColor = add(new Setting.Bool("Color by distance", false));
	public final Setting.Rgb farColor = add(new Setting.Rgb("Far color", 0x40FF80));

	public PlayerEsp() {
		super("playeresp", "Player ESP", "Boxes, tracers and labels for other players.");
	}

	public void renderWorld() {
		if (!enabled) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;

		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = mc.player.getEyePosition(pt);
		Vec3 tracerStart = cam.add(mc.player.getViewVector(pt).scale(1.0));
		double max = maxDistance.value;

		for (Player p : mc.level.players()) {
			if (p == mc.player || p.isSpectator()) continue;

			Vec3 pos = p.getPosition(pt);
			double dist = pos.distanceTo(cam);
			if (dist > max) continue;

			int argb = pickColor(dist / max);
			double height = p.getBbHeight();
			double halfWidth = p.getBbWidth() * 0.5 + 0.1;

			if (boxes.value) {
				drawFlatBox(pos, cam, halfWidth, height, argb);
			}
			if (tracers.value) {
				Draw.line(tracerStart, pos.add(0, height * 0.5, 0), argb);
			}
			if (labels.value) {
				StringBuilder sb = new StringBuilder(p.getName().getString());
				if (showHealth.value) {
					sb.append("  ").append(Math.round(p.getHealth() + p.getAbsorptionAmount())).append("hp");
				}
				if (showDistance.value) {
					sb.append("  ").append(Math.round(dist)).append("m");
				}
				Draw.text(sb.toString(), pos.add(0, height + 0.45, 0), argb);
			}
		}
	}

	private int pickColor(double t) {
		if (!distanceColor.value) return color.argb();
		double k = Math.max(0, Math.min(1, t));
		int r = (int) Math.round(color.channel(0) + (farColor.channel(0) - color.channel(0)) * k);
		int g = (int) Math.round(color.channel(1) + (farColor.channel(1) - color.channel(1)) * k);
		int b = (int) Math.round(color.channel(2) + (farColor.channel(2) - color.channel(2)) * k);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	/** Draws a rectangle standing at feet position 'pos', turned to face the camera. */
	private void drawFlatBox(Vec3 pos, Vec3 cam, double halfWidth, double height, int argb) {
		double dx = pos.x - cam.x;
		double dz = pos.z - cam.z;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0e-4) return;
		double rx = -dz / len; // unit "right" vector, horizontal, perpendicular to view direction
		double rz = dx / len;
		boolean corners = style.index == 0;

		for (int i = 0; i < thickness.i(); i++) {
			double inset = i * 0.014;
			double hw = halfWidth - inset;
			double y0 = pos.y + inset;
			double y1 = pos.y + height - inset;
			if (hw <= 0.02 || y1 <= y0) break;

			Vec3 bl = new Vec3(pos.x - rx * hw, y0, pos.z - rz * hw);
			Vec3 br = new Vec3(pos.x + rx * hw, y0, pos.z + rz * hw);
			Vec3 tl = new Vec3(pos.x - rx * hw, y1, pos.z - rz * hw);
			Vec3 tr = new Vec3(pos.x + rx * hw, y1, pos.z + rz * hw);

			if (!corners) {
				Draw.line(bl, br, argb);
				Draw.line(br, tr, argb);
				Draw.line(tr, tl, argb);
				Draw.line(tl, bl, argb);
			} else {
				double cl = hw * 2 * 0.3;   // horizontal corner length
				double cv = (y1 - y0) * 0.2; // vertical corner length
				corner(bl, rx, rz, cl, cv, 1, 1, argb);
				corner(br, rx, rz, cl, cv, -1, 1, argb);
				corner(tl, rx, rz, cl, cv, 1, -1, argb);
				corner(tr, rx, rz, cl, cv, -1, -1, argb);
			}
		}
	}

	/** One L-shaped corner. sx = +1 draws toward +right, sy = +1 draws upward. */
	private static void corner(Vec3 c, double rx, double rz, double cl, double cv, int sx, int sy, int argb) {
		Draw.line(c, new Vec3(c.x + rx * cl * sx, c.y, c.z + rz * cl * sx), argb);
		Draw.line(c, new Vec3(c.x, c.y + cv * sy, c.z), argb);
	}
}
