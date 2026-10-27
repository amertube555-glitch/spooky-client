package com.spooky.client.module;

import com.spooky.client.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Top-down radar: players, storage and flagged base chunks. */
public final class Radar extends Module {
	public final Setting.Header h1 = add(new Setting.Header("Layout"));
	public final Setting.Mode anchor = add(new Setting.Mode("Corner", "Top left", "Top right", "Bottom left", "Bottom right"));
	public final Setting.Num size = add(new Setting.Num("Size", 120, 60, 240, 10));
	public final Setting.Num range = add(new Setting.Num("Range (blocks)", 64, 16, 512, 8));
	public final Setting.Num opacity = add(new Setting.Num("Background opacity", 0.55, 0, 1, 0.05));
	public final Setting.Num offsetX = add(new Setting.Num("Offset X", 0, 0, 600, 5));
	public final Setting.Num offsetY = add(new Setting.Num("Offset Y", 0, 0, 400, 5));
	public final Setting.Bool rotate = add(new Setting.Bool("Rotate with view", true));
	public final Setting.Bool rings = add(new Setting.Bool("Range rings", true));
	public final Setting.Header h2 = add(new Setting.Header("Show"));
	public final Setting.Bool showPlayers = add(new Setting.Bool("Players", true));
	public final Setting.Bool showNames = add(new Setting.Bool("Player names", false));
	public final Setting.Bool showStorage = add(new Setting.Bool("Storage", true));
	public final Setting.Bool showChunks = add(new Setting.Bool("Flagged base chunks", true));
	public final Setting.Header h3 = add(new Setting.Header("Colors"));
	public final Setting.Rgb border = add(new Setting.Rgb("Border", 0x8A5CF5));
	public final Setting.Rgb background = add(new Setting.Rgb("Background", 0x101018));

	public Radar() {
		super("radar", "Radar", "Top-down minimap of players, storage and flagged bases.");
	}

	private static void outline(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int c) {
		g.fill(x0, y0, x1, y0 + 1, c);
		g.fill(x0, y1 - 1, x1, y1, c);
		g.fill(x0, y0, x0 + 1, y1, c);
		g.fill(x1 - 1, y0, x1, y1, c);
	}

	public void render(GuiGraphicsExtractor g) {
		if (!enabled) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;

		int s = size.i();
		int x0 = (anchor.index == 1 || anchor.index == 3) ? g.guiWidth() - s - 6 - offsetX.i() : 6 + offsetX.i();
		int y0 = (anchor.index >= 2) ? g.guiHeight() - s - 6 - offsetY.i() : 6 + offsetY.i();
		int cx = x0 + s / 2;
		int cy = y0 + s / 2;
		double lim = s / 2.0 - 3;
		double scale = lim / range.value;
		int bc = border.argb();

		int bgAlpha = (int) Math.round(opacity.value * 255);
		g.fill(x0, y0, x0 + s, y0 + s, (bgAlpha << 24) | background.rgb);
		outline(g, x0 - 1, y0 - 1, x0 + s + 1, y0 + s + 1, bc);
		g.fill(cx, y0, cx + 1, y0 + s, 0x28FFFFFF);
		g.fill(x0, cy, x0 + s, cy + 1, 0x28FFFFFF);
		if (rings.value) {
			int h = s / 4;
			outline(g, cx - h, cy - h, cx + h + 1, cy + h + 1, 0x22FFFFFF);
		}

		double yaw = Math.toRadians(mc.player.getYRot());
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		double px = mc.player.getX();
		double pz = mc.player.getZ();

		if (showChunks.value) {
			for (ChunkFinder.Flag f : ChunkFinder.flags()) {
				int dot = rotate.value ? 5 : Math.max(5, (int) Math.round(16 * scale));
				plot(g, f.cx() * 16 + 8 - px, f.cz() * 16 + 8 - pz, sin, cos, scale, cx, cy, lim,
						Modules.CHUNK_FINDER.colorOf(f), dot, true);
			}
		}
		if (showStorage.value) {
			for (BlockEntity be : StorageEsp.tracked()) {
				if (be.isRemoved()) continue;
				int argb = Modules.STORAGE_ESP.colorOf(be);
				if (argb == 0) continue;
				plot(g, be.getBlockPos().getX() + 0.5 - px, be.getBlockPos().getZ() + 0.5 - pz,
						sin, cos, scale, cx, cy, lim, argb, 2, false);
			}
		}
		if (showPlayers.value) {
			int i = 0;
			for (Player p : mc.level.players()) {
				if (p == mc.player || p.isSpectator()) continue;
				double hp = Math.max(0, Math.min(1, p.getHealth() / Math.max(1f, p.getMaxHealth())));
				int argb = Modules.PLAYER_ESP.colorFor(Math.min(1, p.distanceTo(mc.player) / range.value), hp, i++);
				int[] pos = plot(g, p.getX() - px, p.getZ() - pz, sin, cos, scale, cx, cy, lim, argb | 0xFF000000, 4, true);
				if (showNames.value && pos != null) {
					g.text(mc.font, p.getName().getString(), pos[0] + 4, pos[1] - 4, 0xFFE8E8F0);
				}
			}
		}

		// north marker
		double nx = rotate.value ? sin * (lim - 4) : 0;
		double ny = rotate.value ? cos * (lim - 4) : -(lim - 4);
		g.text(mc.font, "N", (int) Math.round(cx + nx) - 2, (int) Math.round(cy + ny) - 4, 0xFFFF6060);
		// you
		g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
	}

	private int[] plot(GuiGraphicsExtractor g, double dx, double dz, double sin, double cos, double scale,
			int cx, int cy, double lim, int argb, int dot, boolean clamp) {
		double right;
		double forward;
		if (rotate.value) {
			forward = -dx * sin + dz * cos;
			right = -dx * cos - dz * sin;
		} else {
			forward = -dz;
			right = dx;
		}
		double sx = right * scale;
		double sy = -forward * scale;
		if (Math.abs(sx) > lim || Math.abs(sy) > lim) {
			if (!clamp) return null;
			double k = lim / Math.max(Math.abs(sx), Math.abs(sy));
			sx *= k;
			sy *= k;
		}
		int x = (int) Math.round(cx + sx);
		int y = (int) Math.round(cy + sy);
		int h = dot / 2;
		g.fill(x - h - 1, y - h - 1, x - h + dot + 1, y - h + dot + 1, 0xAA000000);
		g.fill(x - h, y - h, x - h + dot, y - h + dot, argb);
		return new int[] { x, y };
	}
}
