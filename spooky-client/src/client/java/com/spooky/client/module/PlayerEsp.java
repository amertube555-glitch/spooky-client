package com.spooky.client.module;

import com.spooky.client.render.Draw;
import com.spooky.client.render.Fx;
import com.spooky.client.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Camera-facing boxes, health bars, tracers and labels for other players. Interpolated, so no jitter. */
public final class PlayerEsp extends Module {
	public final Setting.Header h1 = add(new Setting.Header("Targets"));
	public final Setting.Num maxDistance = add(new Setting.Num("Max distance", 256, 16, 512, 16));
	public final Setting.Bool showInvisible = add(new Setting.Bool("Show invisible", true));
	public final Setting.Bool fade = add(new Setting.Bool("Fade with distance", true));
	public final Setting.Header h2 = add(new Setting.Header("Box"));
	public final Setting.Bool boxes = add(new Setting.Bool("Boxes", true));
	public final Setting.Mode style = add(new Setting.Mode("Box style", "Corners", "Full"));
	public final Setting.Num thickness = add(new Setting.Num("Line thickness", 2, 1, 4, 1));
	public final Setting.Bool healthBar = add(new Setting.Bool("Health bar", true));
	public final Setting.Header h3 = add(new Setting.Header("Tracers"));
	public final Setting.Bool tracers = add(new Setting.Bool("Tracers", true));
	public final Setting.Mode tracerTarget = add(new Setting.Mode("Tracer target", "Center", "Feet", "Head"));
	public final Setting.Header h4 = add(new Setting.Header("Label"));
	public final Setting.Bool labels = add(new Setting.Bool("Labels", true));
	public final Setting.Bool showHealth = add(new Setting.Bool("Show health", true));
	public final Setting.Bool showDistance = add(new Setting.Bool("Show distance", true));
	public final Setting.Bool showHeld = add(new Setting.Bool("Show held item", true));
	public final Setting.Header h5 = add(new Setting.Header("Colors"));
	public final Setting.Mode colorMode = add(new Setting.Mode("Color mode", "Solid", "Distance", "Health", "Rainbow"));
	public final Setting.Rgb color = add(new Setting.Rgb("Color", 0xFF4040));
	public final Setting.Rgb farColor = add(new Setting.Rgb("Far color", 0x40FF80));

	public PlayerEsp() {
		super("playeresp", "Player ESP", "Boxes, health bars, tracers and labels for other players.");
	}

	/** Colour a given player would be drawn with (also used by the radar). */
	public int colorFor(double distT, double hp, int index) {
		return switch (colorMode.index) {
			case 1 -> Fx.lerp(color.argb(), farColor.argb(), distT);
			case 2 -> Fx.health(hp);
			case 3 -> Fx.rainbow(index * 0.07);
			default -> color.argb();
		};
	}

	public void renderWorld() {
		if (!enabled) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;

		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = mc.player.getEyePosition(pt);
		Vec3 start = Fx.tracerStart(mc, pt);
		double max = maxDistance.value;
		int index = 0;

		for (Player p : mc.level.players()) {
			if (p == mc.player || p.isSpectator()) continue;
			if (!showInvisible.value && p.isInvisible()) continue;

			Vec3 pos = p.getPosition(pt);
			double dist = pos.distanceTo(cam);
			if (dist > max) continue;

			double hp = Math.max(0, Math.min(1, p.getHealth() / Math.max(1f, p.getMaxHealth())));
			int argb = colorFor(dist / max, hp, index++);
			if (fade.value) argb = Fx.alpha(argb, 1.0 - 0.55 * Math.min(1, dist / max));
			double height = p.getBbHeight();
			double halfWidth = p.getBbWidth() * 0.5 + 0.1;

			if (boxes.value) drawFlatBox(pos, cam, halfWidth, height, argb, hp);
			if (tracers.value) {
				double ty = switch (tracerTarget.index) {
					case 1 -> 0.0;
					case 2 -> height;
					default -> height * 0.5;
				};
				Draw.line(start, pos.add(0, ty, 0), argb);
			}
			if (labels.value) {
				StringBuilder sb = new StringBuilder(p.getName().getString());
				if (showHealth.value) {
					sb.append("  ").append(Math.round(p.getHealth() + p.getAbsorptionAmount())).append("hp");
				}
				if (showDistance.value) sb.append("  ").append(Math.round(dist)).append("m");
				if (showHeld.value) {
					ItemStack held = p.getMainHandItem();
					if (!held.isEmpty()) sb.append("  [").append(held.getHoverName().getString()).append("]");
				}
				Draw.text(sb.toString(), pos.add(0, height + 0.45, 0), argb);
			}
		}
	}

	private void drawFlatBox(Vec3 pos, Vec3 cam, double halfWidth, double height, int argb, double hp) {
		double dx = pos.x - cam.x;
		double dz = pos.z - cam.z;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0e-4) return;
		double rx = -dz / len;
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
				double cl = hw * 2 * 0.3;
				double cv = (y1 - y0) * 0.2;
				corner(bl, rx, rz, cl, cv, 1, 1, argb);
				corner(br, rx, rz, cl, cv, -1, 1, argb);
				corner(tl, rx, rz, cl, cv, 1, -1, argb);
				corner(tr, rx, rz, cl, cv, -1, -1, argb);
			}
		}

		if (healthBar.value) {
			double off = halfWidth + 0.09;
			double top = pos.y + height * hp;
			int hc = Fx.alpha(Fx.health(hp), (argb >>> 24) / 255.0);
			for (int k = 0; k < 2; k++) {
				double o = off + k * 0.02;
				Draw.line(new Vec3(pos.x - rx * o, pos.y, pos.z - rz * o),
						new Vec3(pos.x - rx * o, top, pos.z - rz * o), hc);
			}
		}
	}

	private static void corner(Vec3 c, double rx, double rz, double cl, double cv, int sx, int sy, int argb) {
		Draw.line(c, new Vec3(c.x + rx * cl * sx, c.y, c.z + rz * cl * sx), argb);
		Draw.line(c, new Vec3(c.x, c.y + cv * sy, c.z), argb);
	}
}
