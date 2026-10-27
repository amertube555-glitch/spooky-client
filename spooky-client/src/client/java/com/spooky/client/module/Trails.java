package com.spooky.client.module;

import com.spooky.client.render.Draw;
import com.spooky.client.render.Fx;
import com.spooky.client.setting.Setting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Fading movement trails plus a "last seen" memory for players who left your view. */
public final class Trails extends Module {
	public final Setting.Header h1 = add(new Setting.Header("Trail"));
	public final Setting.Num lifetime = add(new Setting.Num("Trail seconds", 20, 5, 120, 5));
	public final Setting.Num interval = add(new Setting.Num("Sample every (ticks)", 4, 1, 20, 1));
	public final Setting.Bool perPlayer = add(new Setting.Bool("Color per player", true));
	public final Setting.Rgb color = add(new Setting.Rgb("Trail color", 0x40C0FF));
	public final Setting.Header h2 = add(new Setting.Header("Last seen"));
	public final Setting.Bool markers = add(new Setting.Bool("Last-seen markers", true));
	public final Setting.Bool log = add(new Setting.Bool("Last-seen list (HUD)", true));
	public final Setting.Num forget = add(new Setting.Num("Forget after (min)", 10, 1, 60, 1));
	public final Setting.Num logLines = add(new Setting.Num("List lines", 6, 1, 12, 1));

	private record Pt(Vec3 pos, long t) { }

	private static final class Seen {
		String name = "";
		Vec3 pos = Vec3.ZERO;
		long time;
		boolean visible;
	}

	private final Map<UUID, ArrayDeque<Pt>> trails = new HashMap<>();
	private final Map<UUID, Seen> seen = new HashMap<>();
	private ClientLevel lvl;
	private int counter;

	public Trails() {
		super("trails", "Trails", "Fading player trails and a last-seen log.");
	}

	public void tick() {
		if (!enabled) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			trails.clear();
			seen.clear();
			lvl = null;
			return;
		}
		if (mc.level != lvl) {
			lvl = mc.level;
			trails.clear();
			seen.clear();
		}
		long now = System.currentTimeMillis();
		boolean sample = (++counter % interval.i()) == 0;
		for (Seen s : seen.values()) s.visible = false;

		for (Player p : mc.level.players()) {
			if (p == mc.player || p.isSpectator()) continue;
			UUID id = p.getUUID();
			Seen s = seen.computeIfAbsent(id, k -> new Seen());
			s.name = p.getName().getString();
			s.pos = p.position();
			s.time = now;
			s.visible = true;
			if (sample) {
				ArrayDeque<Pt> d = trails.computeIfAbsent(id, k -> new ArrayDeque<>());
				Pt last = d.peekLast();
				if (last == null || last.pos().distanceToSqr(s.pos) > 0.04) d.addLast(new Pt(s.pos, now));
			}
		}
		long life = (long) (lifetime.value * 1000);
		for (ArrayDeque<Pt> d : trails.values()) {
			while (!d.isEmpty() && now - d.peekFirst().t() > life) d.pollFirst();
		}
		trails.values().removeIf(ArrayDeque::isEmpty);
		long forgetMs = (long) (forget.value * 60000);
		seen.values().removeIf(s -> !s.visible && now - s.time > forgetMs);
	}

	private int colorOf(UUID id) {
		if (!perPlayer.value) return color.argb();
		return Fx.hsv((id.hashCode() & 0xFFFF) / 65535f, 0.7f, 1f);
	}

	private static String age(long sec) {
		return sec < 60 ? sec + "s" : (sec / 60) + "m";
	}

	public void renderWorld() {
		if (!enabled) return;
		long now = System.currentTimeMillis();
		double life = lifetime.value * 1000;

		for (var e : trails.entrySet()) {
			int col = colorOf(e.getKey());
			Pt prev = null;
			for (Pt p : e.getValue()) {
				if (prev != null) {
					Draw.line(prev.pos(), p.pos(), Fx.alpha(col, 1.0 - (now - p.t()) / life));
				}
				prev = p;
			}
		}
		if (markers.value) {
			for (var e : seen.entrySet()) {
				Seen s = e.getValue();
				if (s.visible) continue;
				int col = colorOf(e.getKey());
				Draw.line(s.pos, s.pos.add(0, 2, 0), Fx.alpha(col, 0.6));
				Draw.text(s.name + "  " + age((now - s.time) / 1000), s.pos.add(0, 2.3, 0), 0xFFC8C8D8);
			}
		}
	}

	public void renderHud(GuiGraphicsExtractor g) {
		if (!enabled || !log.value) return;
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		List<Seen> gone = new ArrayList<>();
		for (Seen s : seen.values()) if (!s.visible) gone.add(s);
		if (gone.isEmpty()) return;
		gone.sort((a, b) -> Long.compare(b.time, a.time));
		int y = 8;
		String title = "Last seen";
		g.text(mc.font, title, g.guiWidth() - 6 - mc.font.width(title), y, 0xFF8A5CF5);
		y += mc.font.lineHeight + 2;
		for (int i = 0; i < Math.min(gone.size(), logLines.i()); i++) {
			Seen s = gone.get(i);
			String line = s.name + "  " + (int) s.pos.x + " " + (int) s.pos.y + " " + (int) s.pos.z
					+ "  " + age((now - s.time) / 1000) + " ago";
			g.text(mc.font, line, g.guiWidth() - 6 - mc.font.width(line), y, 0xFFC8C8D8);
			y += mc.font.lineHeight + 1;
		}
	}
}
