package com.spooky.client.render;

import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * All world-space drawing goes through here, so if Mojang changes the gizmo API
 * only this one file needs fixing. Must be called from LevelRenderEvents.BEFORE_GIZMOS.
 * Coordinates are absolute world coordinates.
 */
public final class Draw {
	private Draw() { }

	/** Outline box that shows through walls. */
	public static void box(AABB box, int argb, float width) {
		Gizmos.cuboid(box, GizmoStyle.stroke(argb, width)).setAlwaysOnTop();
	}

	/** Line that shows through walls. */
	public static void line(Vec3 from, Vec3 to, int argb) {
		Gizmos.line(from, to, argb).setAlwaysOnTop();
	}

	/** Camera-facing text label. */
	public static void text(String text, Vec3 pos, int argb) {
		Gizmos.billboardText(text, pos, TextGizmo.Style.forColorAndCentered(argb));
	}
}
