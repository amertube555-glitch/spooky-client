package com.spooky.client.gui;

import com.spooky.client.config.Config;
import com.spooky.client.module.Module;
import com.spooky.client.module.Modules;
import com.spooky.client.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The menu. Left: list of modules (click a name to open it, click the switch to turn it on/off).
 * Right: that module's settings. Drag sliders, click switches, scroll with the mouse wheel.
 * Drawn "immediate mode": every frame rebuilds the list of clickable areas.
 */
public final class ClickGui extends Screen {
	private static final int PW = 480;
	private static final int PH = 300;
	private static final int LEFT = 118;
	private static final int HEAD = 24;
	private static final int ROW = 20;

	private static final int ACCENT = 0xFF8A5CF5;
	private static final int BG = 0xF0171720;
	private static final int HEADER_BG = 0xFF101018;
	private static final int ROW_BG = 0xFF23232F;
	private static final int SELECT_BG = 0xFF33334A;
	private static final int OFF = 0xFF3A3A4C;
	private static final int TEXT = 0xFFE8E8F0;
	private static final int DIM = 0xFF9090A4;

	private record Hit(int x, int y, int w, int h, DoubleConsumer press, DoubleConsumer drag) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final List<Hit> hits = new ArrayList<>();
	private Hit dragging;
	private Module selected = Modules.ALL.get(0);
	private int scroll;
	private int maxScroll;
	private int px;
	private int py;
	private int pw;
	private int ph;

	public ClickGui() {
		super(Component.literal("Spooky Client"));
	}

	@Override
	protected void init() {
		pw = Math.min(PW, width - 20);
		ph = Math.min(PH, height - 20);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		hits.clear();
		int lh = font.lineHeight;

		g.fill(0, 0, width, height, 0x90000000);
		g.fill(px, py, px + pw, py + ph, BG);
		g.fill(px, py, px + pw, py + HEAD, HEADER_BG);
		g.fill(px, py + HEAD - 1, px + pw, py + HEAD, ACCENT);
		g.text(font, "Spooky Client", px + 8, py + (HEAD - lh) / 2, TEXT);
		String hint = "Esc to close";
		g.text(font, hint, px + pw - 8 - font.width(hint), py + (HEAD - lh) / 2, DIM);

		// ---- left: module list ----
		int ly = py + HEAD + 6;
		for (Module m : Modules.ALL) {
			int rx = px + 6;
			int rw = LEFT - 12;
			g.fill(rx, ly, rx + rw, ly + ROW, m == selected ? SELECT_BG : ROW_BG);
			g.text(font, m.name, rx + 6, ly + (ROW - lh) / 2, m.enabled ? TEXT : DIM);

			int pillW = 22;
			int pillH = ROW - 8;
			int pillX = rx + rw - pillW - 4;
			int pillY = ly + 4;
			drawSwitch(g, pillX, pillY, pillW, pillH, m.enabled);

			hits.add(new Hit(rx, ly, rw - pillW - 8, ROW, mx -> {
				selected = m;
				scroll = 0;
			}, null));
			hits.add(new Hit(pillX, pillY, pillW, pillH, mx -> {
				m.toggle();
				Config.save();
			}, null));
			ly += ROW + 4;
		}

		// ---- right: settings of the selected module ----
		int rx0 = px + LEFT;
		int rx1 = px + pw - 8;
		int top = py + HEAD + 8;
		int bottom = py + ph - 8;
		g.text(font, selected.name, rx0 + 4, top, ACCENT);
		g.text(font, selected.description, rx0 + 4, top + 11, DIM);

		int listTop = top + 28;
		int y = listTop - scroll;
		for (Setting s : selected.settings) {
			if (y >= listTop && y + ROW <= bottom) {
				drawSetting(g, s, rx0, rx1, y);
			}
			y += ROW + 3;
		}
		int content = (y + scroll) - listTop;
		maxScroll = Math.max(0, content - (bottom - listTop));
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		if (maxScroll > 0) {
			String more = "Scroll for more";
			g.text(font, more, rx1 - font.width(more), top + 11, DIM);
		}
	}

	private void drawSwitch(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean on) {
		g.fill(x, y, x + w, y + h, on ? ACCENT : OFF);
		int knob = on ? x + w - h : x;
		g.fill(knob + 1, y + 1, knob + h - 1, y + h - 1, 0xFFFFFFFF);
	}

	private void drawSetting(GuiGraphicsExtractor g, Setting s, int x0, int x1, int y) {
		int lh = font.lineHeight;
		int ty = y + (ROW - lh) / 2;
		g.fill(x0, y, x1, y + ROW, ROW_BG);
		g.text(font, s.name, x0 + 6, ty, TEXT);

		if (s instanceof Setting.Bool b) {
			drawSwitch(g, x1 - 6 - 22, y + 4, 22, ROW - 8, b.value);
			hits.add(new Hit(x0, y, x1 - x0, ROW, mx -> {
				b.value = !b.value;
				Config.save();
			}, null));
		} else if (s instanceof Setting.Mode mode) {
			String label = mode.get();
			int bw = font.width(label) + 16;
			int bx = x1 - 6 - bw;
			g.fill(bx, y + 3, bx + bw, y + ROW - 3, OFF);
			g.text(font, label, bx + 8, ty, TEXT);
			hits.add(new Hit(x0, y, x1 - x0, ROW, mx -> {
				mode.cycle();
				Config.save();
			}, null));
		} else if (s instanceof Setting.Num n) {
			final int tw = 130;
			final int tx = x1 - 6 - tw;
			double frac = (n.value - n.min) / (n.max - n.min);
			g.fill(tx, y + 5, tx + tw, y + ROW - 5, OFF);
			g.fill(tx, y + 5, tx + (int) Math.round(frac * tw), y + ROW - 5, ACCENT);
			String shown = n.step >= 1 ? Integer.toString(n.i()) : String.format("%.2f", n.value);
			g.text(font, shown, tx + (tw - font.width(shown)) / 2, ty, TEXT);
			DoubleConsumer setter = mx -> n.set(n.min + (mx - tx) / (double) tw * (n.max - n.min));
			hits.add(new Hit(tx, y, tw, ROW, setter, setter));
		} else if (s instanceof Setting.Rgb c) {
			final int sw = 50;
			final int gap = 4;
			final int sx = x1 - 6 - (3 * sw + 2 * gap);
			g.fill(sx - 20, y + 3, sx - 6, y + ROW - 3, c.argb());
			int[] tint = { 0xFFE05050, 0xFF50D060, 0xFF5080F0 };
			for (int ch = 0; ch < 3; ch++) {
				final int channel = ch;
				final int tx = sx + ch * (sw + gap);
				g.fill(tx, y + 5, tx + sw, y + ROW - 5, OFF);
				g.fill(tx, y + 5, tx + (int) Math.round(c.channel(ch) / 255.0 * sw), y + ROW - 5, tint[ch]);
				DoubleConsumer setter = mx -> c.setChannel(channel, (int) Math.round((mx - tx) / (double) sw * 255.0));
				hits.add(new Hit(tx, y, sw, ROW, setter, setter));
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				Hit h = hits.get(i);
				if (h.contains(event.x(), event.y())) {
					h.press().accept(event.x());
					if (h.drag() != null) dragging = h;
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging != null && dragging.drag() != null) {
			dragging.drag().accept(event.x());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			Config.save();
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(scrollY * 20)));
		return true;
	}

	@Override
	public void onClose() {
		Config.save();
		super.onClose();
	}
}
