package com.spooky.client.module;

import com.spooky.client.render.Draw;
import com.spooky.client.render.Fx;
import com.spooky.client.setting.Setting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Flags chunks that look like player bases. Scores storage and utility blocks plus clumps of
 * item frames / armor stands / villagers / minecarts. Ignores terrain blocks (obsidian etc.),
 * spawners and chest minecarts, so caves, dungeons and mineshafts do not score.
 */
public final class ChunkFinder extends Module {
	public final Setting.Header h1 = add(new Setting.Header("Detection"));
	public final Setting.Num minScore = add(new Setting.Num("Min score", 12, 3, 80, 1));
	public final Setting.Num minStorage = add(new Setting.Num("Min storage blocks", 2, 0, 20, 1));
	public final Setting.Num minY = add(new Setting.Num("Ignore below Y", -64, -64, 320, 8));
	public final Setting.Bool useEntities = add(new Setting.Bool("Count entity clusters", true));
	public final Setting.Bool villageFilter = add(new Setting.Bool("Ignore villages", true));
	public final Setting.Bool notify = add(new Setting.Bool("Notify on new find", true));
	public final Setting.Header h2 = add(new Setting.Header("Render"));
	public final Setting.Num range = add(new Setting.Num("Range (chunks)", 24, 2, 64, 1));
	public final Setting.Num thickness = add(new Setting.Num("Line thickness", 2, 1, 6, 1));
	public final Setting.Bool labels = add(new Setting.Bool("Labels", true));
	public final Setting.Bool tracers = add(new Setting.Bool("Tracers", true));
	public final Setting.Bool byScore = add(new Setting.Bool("Color by score", true));
	public final Setting.Rgb color = add(new Setting.Rgb("Color", 0xFFD040));
	public final Setting.Rgb hotColor = add(new Setting.Rgb("High-score color", 0xFF3030));

	public record Flag(int cx, int cz, int score, double yMin, double yMax) { }

	private record Sig(String kind, int y) { }

	// kind -> {weight per block, max contribution per chunk}
	private static final Map<String, int[]> W = new HashMap<>();
	private static final Set<String> STORAGE = Set.of("chest", "trapped_chest", "ender_chest", "barrel",
			"shulker_box", "hopper", "dispenser", "dropper");
	private static final Set<String> STRONG = Set.of("shulker_box", "ender_chest", "hopper", "dispenser", "dropper",
			"trapped_chest", "beacon", "command_block", "brewing_stand", "enchanting_table", "crafter");

	static {
		put("chest", 2, 12); put("trapped_chest", 3, 9); put("barrel", 2, 12); put("shulker_box", 4, 16);
		put("ender_chest", 2, 4); put("hopper", 3, 12); put("dispenser", 2, 6); put("dropper", 2, 6);
		put("furnace", 1, 4); put("blast_furnace", 2, 4); put("smoker", 2, 4); put("bed", 1, 3);
		put("brewing_stand", 2, 4); put("enchanting_table", 2, 2); put("beacon", 6, 6);
		put("sign", 1, 4); put("hanging_sign", 1, 4); put("jukebox", 1, 2); put("command_block", 3, 6);
		put("crafter", 2, 4); put("campfire", 1, 2);
		// entities
		put("item_frame", 1, 6); put("glow_item_frame", 1, 6); put("armor_stand", 2, 8);
		put("villager", 1, 3); put("minecart", 1, 2); put("hopper_minecart", 2, 4);
	}

	private static void put(String k, int w, int cap) {
		W.put(k, new int[] { w, cap });
	}

	private static final Map<BlockEntity, Sig> SIGS = new ConcurrentHashMap<>();
	private static ClientLevel sigLevel;
	private static volatile List<Flag> FLAGS = List.of();
	private final Set<Long> notified = new HashSet<>();
	private ClientLevel lvl;
	private int ticks;

	public ChunkFinder() {
		super("chunkfinder", "Chunk Finder", "Flags chunks that look like player bases (not caves).");
	}

	public static List<Flag> flags() {
		return FLAGS;
	}

	// ---- tracking ----

	public static void onLoad(BlockEntity be, ClientLevel level) {
		if (level != sigLevel) {
			SIGS.clear();
			sigLevel = level;
		}
		var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
		if (key == null) return;
		String kind = key.getPath();
		if (W.containsKey(kind)) SIGS.put(be, new Sig(kind, be.getBlockPos().getY()));
	}

	public static void onUnload(BlockEntity be, ClientLevel level) {
		SIGS.remove(be);
	}

	private static long ck(int cx, int cz) {
		return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
	}

	private static final class Acc {
		final Map<String, Integer> counts = new HashMap<>();
		double yMin = Double.MAX_VALUE;
		double yMax = -Double.MAX_VALUE;
		int cx;
		int cz;

		void add(String kind, double y) {
			counts.merge(kind, 1, Integer::sum);
			yMin = Math.min(yMin, y);
			yMax = Math.max(yMax, y);
		}

		int score(boolean strongOnly) {
			int total = 0;
			for (var e : counts.entrySet()) {
				if (strongOnly && !STRONG.contains(e.getKey())) continue;
				int[] w = W.get(e.getKey());
				total += Math.min(w[0] * e.getValue(), w[1]);
			}
			return total;
		}

		int storage() {
			int n = 0;
			for (var e : counts.entrySet()) if (STORAGE.contains(e.getKey())) n += e.getValue();
			return n;
		}
	}

	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			FLAGS = List.of();
			return;
		}
		if (mc.level != lvl) {
			lvl = mc.level;
			notified.clear();
			FLAGS = List.of();
		}
		if (!enabled || ++ticks % 20 != 0) return;

		Map<Long, Acc> chunks = new HashMap<>();
		for (var e : SIGS.entrySet()) {
			BlockEntity be = e.getKey();
			if (be.isRemoved()) continue;
			Sig s = e.getValue();
			if (s.y() < minY.value) continue;
			int cx = be.getBlockPos().getX() >> 4;
			int cz = be.getBlockPos().getZ() >> 4;
			Acc a = chunks.computeIfAbsent(ck(cx, cz), k -> new Acc());
			a.cx = cx;
			a.cz = cz;
			a.add(s.kind(), s.y());
		}
		if (useEntities.value) {
			for (Entity en : mc.level.entitiesForRendering()) {
				var key = BuiltInRegistries.ENTITY_TYPE.getKey(en.getType());
				if (key == null) continue;
				String kind = key.getPath();
				if (!W.containsKey(kind) || en.getY() < minY.value) continue;
				int cx = ((int) Math.floor(en.getX())) >> 4;
				int cz = ((int) Math.floor(en.getZ())) >> 4;
				Acc a = chunks.computeIfAbsent(ck(cx, cz), k -> new Acc());
				a.cx = cx;
				a.cz = cz;
				a.add(kind, en.getY());
			}
		}

		List<Flag> out = new ArrayList<>();
		for (var e : chunks.entrySet()) {
			Acc a = e.getValue();
			int score = a.score(false);
			if (score < minScore.i() || a.storage() < minStorage.i()) continue;
			if (villageFilter.value && a.counts.getOrDefault("villager", 0) > 0 && a.score(true) < 4) continue;
			out.add(new Flag(a.cx, a.cz, score, a.yMin, a.yMax));
			if (notify.value && notified.add(e.getKey())) {
				Alerts.push("Possible base  " + (a.cx * 16 + 8) + ", " + (a.cz * 16 + 8) + "  (score " + score + ")",
						color.rgb, 8);
			}
		}
		FLAGS = List.copyOf(out);
	}

	public int colorOf(Flag f) {
		if (!byScore.value) return color.argb();
		double t = (f.score() - minScore.value) / Math.max(1.0, minScore.value * 2.0);
		return Fx.lerp(color.argb(), hotColor.argb(), t);
	}

	public void renderWorld() {
		if (!enabled) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 eye = mc.player.getEyePosition(pt);
		Vec3 start = Fx.tracerStart(mc, pt);
		double maxD = range.value * 16;

		for (Flag f : FLAGS) {
			double wx = f.cx() * 16 + 8;
			double wz = f.cz() * 16 + 8;
			double d = Math.hypot(wx - eye.x, wz - eye.z);
			if (d > maxD) continue;
			int argb = Fx.alpha(colorOf(f), 1.0 - 0.5 * Math.min(1, d / maxD));
			Draw.box(new AABB(f.cx() * 16, f.yMin() - 1, f.cz() * 16, f.cx() * 16 + 16, f.yMax() + 2, f.cz() * 16 + 16),
					argb, thickness.f());
			double my = (f.yMin() + f.yMax()) / 2;
			if (labels.value) {
				Draw.text("Base " + f.score() + "  " + Math.round(d) + "m", new Vec3(wx, f.yMax() + 3, wz), argb);
			}
			if (tracers.value) Draw.line(start, new Vec3(wx, my, wz), argb);
		}
	}
}
