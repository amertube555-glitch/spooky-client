package com.spooky.client.module;

import com.spooky.client.render.Draw;
import com.spooky.client.render.Fx;
import com.spooky.client.setting.Setting;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Outlines storage blocks (through walls). Each type can be switched on/off and recoloured. */
public final class StorageEsp extends Module {
	private static final String[] TYPE_NAMES = {
		"Chests", "Trapped chests", "Ender chests", "Shulker boxes", "Barrels",
		"Hoppers", "Furnaces", "Dispensers/Droppers", "Spawners"
	};
	private static final boolean[] DEFAULT_ON = { true, true, true, true, true, false, false, false, true };
	private static final int[] DEFAULT_COLOR = {
		0xFFA640, 0xFF4040, 0xB040FF, 0xFF60D0, 0xC08040, 0x909090, 0x707070, 0x4080FF, 0x40FFC0
	};

	/** Block entities the client currently knows about. Filled by Fabric load/unload events. */
	private static final Set<BlockEntity> TRACKED = ConcurrentHashMap.newKeySet();
	private static ClientLevel trackedLevel;

	public final Setting.Header h1 = add(new Setting.Header("General"));
	public final Setting.Num maxDistance = add(new Setting.Num("Max distance", 128, 16, 512, 16));
	public final Setting.Num thickness = add(new Setting.Num("Line thickness", 2, 1, 6, 1));
	public final Setting.Bool tracers = add(new Setting.Bool("Tracers", false));
	public final Setting.Bool fade = add(new Setting.Bool("Fade with distance", true));
	public final Setting.Header h2 = add(new Setting.Header("Block types"));
	public final Setting.Bool[] typeOn = new Setting.Bool[TYPE_NAMES.length];
	public final Setting.Rgb[] typeColor = new Setting.Rgb[TYPE_NAMES.length];

	public StorageEsp() {
		super("storageesp", "Storage ESP", "Outlines chests, shulkers, barrels and more through walls.");
		for (int i = 0; i < TYPE_NAMES.length; i++) {
			typeOn[i] = add(new Setting.Bool(TYPE_NAMES[i], DEFAULT_ON[i]));
			typeColor[i] = add(new Setting.Rgb(TYPE_NAMES[i] + " color", DEFAULT_COLOR[i]));
		}
	}

	// ---- tracking (called from Fabric events) ----

	public static void onLoad(BlockEntity be, ClientLevel level) {
		if (level != trackedLevel) {
			TRACKED.clear();
			trackedLevel = level;
		}
		if (typeIndex(be) >= 0) TRACKED.add(be);
	}

	public static void onUnload(BlockEntity be, ClientLevel level) {
		TRACKED.remove(be);
	}

	public static Set<BlockEntity> tracked() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != trackedLevel) {
			TRACKED.clear();
			trackedLevel = mc.level;
		}
		return TRACKED;
	}

	// ---- classification ----

	/** Index into the type tables, or -1 if this block entity is not storage we care about. */
	public static int typeIndex(BlockEntity be) {
		if (be instanceof TrappedChestBlockEntity) return 1; // must be checked before ChestBlockEntity
		if (be instanceof ChestBlockEntity) return 0;
		if (be instanceof EnderChestBlockEntity) return 2;
		if (be instanceof ShulkerBoxBlockEntity) return 3;
		if (be instanceof BarrelBlockEntity) return 4;
		if (be instanceof HopperBlockEntity) return 5;
		if (be instanceof AbstractFurnaceBlockEntity) return 6;
		if (be instanceof DispenserBlockEntity) return 7;
		if (be instanceof SpawnerBlockEntity) return 8;
		return -1;
	}

	/** ARGB colour for this block entity if its type is switched on, otherwise 0. */
	public int colorOf(BlockEntity be) {
		int t = typeIndex(be);
		if (t < 0 || !typeOn[t].value) return 0;
		return typeColor[t].argb();
	}

	public void renderWorld() {
		if (!enabled) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;

		double maxSq = maxDistance.value * maxDistance.value;
		var eye = mc.player.getEyePosition();
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double max = maxDistance.value;

		for (BlockEntity be : tracked()) {
			if (be.isRemoved()) continue;
			int argb = colorOf(be);
			if (argb == 0) continue;
			var pos = be.getBlockPos();
			if (pos.distToCenterSqr(eye.x, eye.y, eye.z) > maxSq) continue;
			double d = Math.sqrt(pos.distToCenterSqr(eye.x, eye.y, eye.z));
			if (fade.value) argb = Fx.alpha(argb, 1.0 - 0.6 * Math.min(1, d / max));
			Draw.box(new AABB(pos), argb, thickness.f());
			if (tracers.value) Draw.line(Fx.tracerStart(mc, pt), Vec3.atCenterOf(pos), argb);
		}
	}
}
