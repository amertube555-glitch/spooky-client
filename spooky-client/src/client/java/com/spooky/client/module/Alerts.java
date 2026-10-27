package com.spooky.client.module;

import com.spooky.client.setting.Setting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;

/** Short on-screen notices: players joining/leaving the server, entering/leaving your render range, new bases. */
public final class Alerts extends Module {
	public final Setting.Bool joinLeave = add(new Setting.Bool("Join / leave notices", true));
	public final Setting.Bool range = add(new Setting.Bool("Render range notices", true));
	public final Setting.Num seconds = add(new Setting.Num("Show for (seconds)", 5, 2, 15, 1));
	public final Setting.Num maxShown = add(new Setting.Num("Max on screen", 5, 1, 10, 1));
	public final Setting.Rgb joinColor = add(new Setting.Rgb("Join color", 0x60E060));
	public final Setting.Rgb leaveColor = add(new Setting.Rgb("Leave color", 0xFF5050));
	public final Setting.Rgb rangeColor = add(new Setting.Rgb("Range color", 0xFFD040));

	private record Alert(String text, int rgb, long until) { }

	private static final List<Alert> ACTIVE = new ArrayList<>();

	private ClientLevel lvl;
	private boolean ready;
	private Map<UUID, String> inRange = new HashMap<>();
	private Map<UUID, String> online = new HashMap<>();

	public Alerts() {
		super("alerts", "Alerts", "Notices when players join, leave or enter your range.");
		enabled = true;
	}

	/** Queue a notice. Shown only while the Alerts module is on. */
	public static void push(String text, int rgb, double secs) {
		synchronized (ACTIVE) {
			ACTIVE.add(new Alert(text, rgb & 0xFFFFFF, System.currentTimeMillis() + (long) (secs * 1000)));
		}
	}

	private void note(String text, int rgb) {
		push(text, rgb, seconds.value);
	}

	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || mc.getConnection() == null) {
			ready = false;
			inRange = new HashMap<>();
			online = new HashMap<>();
			return;
		}
		if (mc.level != lvl) {
			lvl = mc.level;
			ready = false;
		}

		Map<UUID, String> nowRange = new HashMap<>();
		for (Player p : mc.level.players()) {
			if (p != mc.player && !p.isSpectator()) nowRange.put(p.getUUID(), p.getName().getString());
		}
		Map<UUID, String> nowOnline = new HashMap<>();
		for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
			UUID id = info.getProfile().id();
			if (!id.equals(mc.player.getUUID())) nowOnline.put(id, info.getProfile().name());
		}

		if (ready && enabled) {
			if (joinLeave.value) {
				for (var e : nowOnline.entrySet()) {
					if (!online.containsKey(e.getKey())) note("+ " + e.getValue() + " joined", joinColor.rgb);
				}
				for (var e : online.entrySet()) {
					if (!nowOnline.containsKey(e.getKey())) note("- " + e.getValue() + " left", leaveColor.rgb);
				}
			}
			if (range.value) {
				for (var e : nowRange.entrySet()) {
					if (!inRange.containsKey(e.getKey())) {
						Player p = mc.level.getPlayerByUUID(e.getKey());
						String d = p == null ? "" : " (" + Math.round(p.distanceTo(mc.player)) + "m)";
						note(e.getValue() + " entered range" + d, rangeColor.rgb);
					}
				}
				for (var e : inRange.entrySet()) {
					if (!nowRange.containsKey(e.getKey()) && nowOnline.containsKey(e.getKey())) {
						note(e.getValue() + " left range", rangeColor.rgb);
					}
				}
			}
		}
		inRange = nowRange;
		online = nowOnline;
		ready = true;
	}

	public void render(GuiGraphicsExtractor g) {
		if (!enabled) return;
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		List<Alert> show;
		synchronized (ACTIVE) {
			ACTIVE.removeIf(a -> a.until() < now);
			int from = Math.max(0, ACTIVE.size() - maxShown.i());
			show = new ArrayList<>(ACTIVE.subList(from, ACTIVE.size()));
		}
		int y = 8;
		for (Alert a : show) {
			double left = (a.until() - now) / 1000.0;
			double alpha = Math.min(1.0, left / 0.6);
			int w = mc.font.width(a.text());
			int x = (g.guiWidth() - w) / 2;
			int bg = ((int) Math.round(alpha * 150) << 24) | 0x101018;
			g.fill(x - 5, y - 2, x + w + 5, y + mc.font.lineHeight + 2, bg);
			g.fill(x - 5, y - 2, x - 3, y + mc.font.lineHeight + 2, ((int) Math.round(alpha * 255) << 24) | a.rgb());
			g.text(mc.font, a.text(), x, y, ((int) Math.round(alpha * 255) << 24) | 0xFFFFFF);
			y += mc.font.lineHeight + 6;
		}
	}
}
