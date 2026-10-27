package com.spooky.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Colour and tracer helpers shared by every module. */
public final class Fx {
	private Fx() { }

	public static int lerp(int a, int b, double t) {
		t = Math.max(0, Math.min(1, t));
		int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
		int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
		int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	public static int alpha(int argb, double a) {
		int al = (int) Math.round(Math.max(0, Math.min(1, a)) * 255);
		return (al << 24) | (argb & 0xFFFFFF);
	}

	public static int hsv(float h, float s, float v) {
		h = h - (float) Math.floor(h);
		int i = (int) (h * 6);
		float f = h * 6 - i;
		float p = v * (1 - s);
		float q = v * (1 - f * s);
		float t = v * (1 - (1 - f) * s);
		float r;
		float g;
		float b;
		switch (i % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
	}

	public static int rainbow(double offset) {
		float h = (float) (((System.currentTimeMillis() % 6000L) / 6000.0) + offset);
		return hsv(h, 0.75f, 1f);
	}

	/** Red (0) to green (1). */
	public static int health(double frac) {
		return lerp(0xFF3030, 0x40FF60, frac);
	}

	/** Point just in front of the crosshair, where tracers start. */
	public static Vec3 tracerStart(Minecraft mc, float pt) {
		return mc.player.getEyePosition(pt).add(mc.player.getViewVector(pt).scale(1.0));
	}
}
