package com.spooky.client.module;

import com.spooky.client.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Small top-down radar in the corner of the screen showing nearby players and storage. */
public final class Radar extends Module {
	public final Setting.Num size = add(new Setting.Num("Size", 110, 60, 220, 10));
	public final Setting.Num range = add(new Setting.Num("Range", 64, 16, 256, 8));
	public final Setting.Num opacity = add(new Setting.Num("Background opacity", 0.55, 0, 1, 0.05));
	public final Setting.Num offsetX = add(new Setting.Num("Offset X", 0, 0, 600, 5));
	public final Setting.Num offsetY = add(new Setting.Num("Offset Y", 0, 0, 400, 5));
	public final Setting.Bool rotate = add(new Setting.Bool("Rotate with view", true));
	public final Setting.Bool showPlayers = add(new Setting.Bool("Show players", true));
	public final Setting.Bool showStorage = add(new Setting.Bool("Show storage", true));

	public Radar() {
		super("radar", "Radar", "Top-down minimap of nearby players and storage.");
	}

	public void render(GuiGraphicsExtractor g) {
		if (!enabled) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;

		int s = size.i();
		int x0 = 6 + offsetX.i();
		int y0 = 6 + offsetY.i();
		int cx = x0 + s / 2;
		int cy = y0 + s / 2;
		double scale = (s / 2.0 - 3) / range.value;

		int bgAlpha = (int) Math.round(opacity.value * 255);
		g.fill(x0, y0, x0 + s, y0 + s, (bgAlpha << 24) | 0x101018);
		// border
		g.fill(x0 - 1, y0 - 1, x0 + s + 1, y0, 0xFF8A5CF5);
		g.fill(x0 - 1, y0 + s, x0 + s + 1, y0 + s + 1, 0xFF8A5CF5);
		g.fill(x0 - 1, y0, x0, y0 + s, 0xFF8A5CF5);
		g.fill(x0 + s, y0, x0 + s + 1, y0 + s, 0xFF8A5CF5);
		// crosshair lines
		g.fill(cx, y0, cx + 1, y0 + s, 0x30FFFFFF);
		g.fill(x0, cy, x0 + s, cy + 1, 0x30FFFFFF);

		double yaw = Math.toRadians(mc.player.getYRot());
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		double px = mc.player.getX();
		double pz = mc.player.getZ();

		if (showStorage.value) {
			for (BlockEntity be : StorageEsp.tracked()) {
				if (be.isRemoved()) continue;
				int argb = Modules.STORAGE_ESP.colorOf(be);
				if (argb == 0) continue;
				plot(g, be.getBlockPos().getX() + 0.5 - px, be.getBlockPos().getZ() + 0.5 - pz,
						sin, cos, scale, cx, cy, s, argb, 2, false);
			}
		}
		if (showPlayers.value) {
			for (Player p : mc.level.players()) {
				if (p == mc.player || p.isSpectator()) continue;
				plot(g, p.getX() - px, p.getZ() - pz, sin, cos, scale, cx, cy, s,
						Modules.PLAYER_ESP.color.argb(), 4, true);
			}
		}
		// you
		g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
	}

	/** Converts a world offset to radar pixels and draws a dot. Clamps to the edge if out of range. */
	private void plot(GuiGraphicsExtractor g, double dx, double dz, double sin, double cos, double scale,
			int cx, int cy, int s, int argb, int dot, boolean clamp) {
		double right;
		double forward;
		if (rotate.value) {
			forward = -dx * sin + dz * cos;
			right = -dx * cos - dz * sin;
		} else {
			forward = -dz; // north (-Z) is up
			right = dx;
		}
		double sx = right * scale;
		double sy = -forward * scale;
		double lim = s / 2.0 - 3;
		boolean outside = Math.abs(sx) > lim || Math.abs(sy) > lim;
		if (outside) {
			if (!clamp) return;
			double k = lim / Math.max(Math.abs(sx), Math.abs(sy));
			sx *= k;
			sy *= k;
		}
		int x = (int) Math.round(cx + sx);
		int y = (int) Math.round(cy + sy);
		int h = dot / 2;
		g.fill(x - h, y - h, x - h + dot, y - h + dot, argb);
	}
}
