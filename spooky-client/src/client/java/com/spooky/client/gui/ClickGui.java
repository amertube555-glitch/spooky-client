package com.spooky.client.gui;

import com.spooky.client.config.Config;
import com.spooky.client.module.Module;
import com.spooky.client.module.Modules;
import com.spooky.client.render.Fx;
import com.spooky.client.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Sidebar of modules on the left, that module's settings on the right.
 * Sections, switches, sliders, mode buttons, and colour pickers with RGB sliders, hex and a palette.
 */
public final class ClickGui extends Screen {
	private static final int PW = 560;
	private static final int PH = 340;
	private static final int LEFT = 132;
	private static final int HEAD = 28;
	private static final int ROW = 20;
	private static final int RGB_ROW = 40;
	private static final int HEADER_ROW = 17;

	private static final int ACCENT = 0xFF8A5CF5;
	private static final int BG = 0xF2141420;
	private static final int SIDE_BG = 0xFF0F0F18;
	private static final int HEADER_BG = 0xFF0C0C14;
	private static final int ROW_BG = 0xFF1E1E2B;
	private static final int ROW_HOVER = 0xFF282838;
	private static final int SELECT_BG = 0xFF2A2A40;
	private static final int OFF = 0xFF353548;
	private static final int TEXT = 0xFFE8E8F0;
	private static final int DIM = 0xFF8888A0;
	private static final int[] PALETTE = {
		0xFF4040, 0xFF8A30, 0xFFD040, 0x60E060, 0x40FFC0, 0x40C0FF,
		0x4080FF, 0x8A5CF5, 0xFF60D0, 0xFFFFFF, 0xA0A0A0, 0x202020
	};

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
	private int mx;
	private int my;

	public ClickGui() {
		super(Component.literal("Spooky Client"));
	}

	@Override
	protected void init() {
		pw = Math.min(PW, width - 16);
		ph = Math.min(PH, height - 16);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
	}

	private boolean over(int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static int heightOf(Setting s) {
		if (s instanceof Setting.Header) return HEADER_ROW;
		if (s instanceof Setting.Rgb) return RGB_ROW;
		return ROW;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		this.mx = mouseX;
		this.my = mouseY;
		hits.clear();
		int lh = font.lineHeight;

		g.fill(0, 0, width, height, 0x99000000);
		g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF000000);
		g.fill(px, py, px + pw, py + ph, BG);
		g.fill(px, py, px + pw, py + HEAD, HEADER_BG);
		for (int i = 0; i < pw; i += 4) {
			g.fill(px + i, py + HEAD - 2, Math.min(px + i + 4, px + pw), py + HEAD, Fx.rainbow(i / (double) pw));
		}
		g.text(font, "SPOOKY CLIENT", px + 10, py + (HEAD - 2 - lh) / 2, TEXT);
		String hint = "Esc to close";
		g.text(font, hint, px + pw - 10 - font.width(hint), py + (HEAD - 2 - lh) / 2, DIM);

		// ---- sidebar ----
		g.fill(px, py + HEAD, px + LEFT, py + ph, SIDE_BG);
		int ly = py + HEAD + 8;
		for (Module m : Modules.ALL) {
			int rx = px + 6;
			int rw = LEFT - 12;
			boolean sel = m == selected;
			boolean hov = over(rx, ly, rw, 22);
			g.fill(rx, ly, rx + rw, ly + 22, sel ? SELECT_BG : hov ? ROW_HOVER : ROW_BG);
			if (sel) g.fill(rx, ly, rx + 2, ly + 22, ACCENT);
			g.text(font, m.name, rx + 8, ly + (22 - lh) / 2, m.enabled ? TEXT : DIM);
			int pillW = 22;
			int pillH = 12;
			int pillX = rx + rw - pillW - 5;
			int pillY = ly + 5;
			drawSwitch(g, pillX, pillY, pillW, pillH, m.enabled);
			hits.add(new Hit(rx, ly, rw - pillW - 10, 22, v -> {
				selected = m;
				scroll = 0;
			}, null));
			hits.add(new Hit(pillX - 2, pillY - 2, pillW + 4, pillH + 4, v -> {
				m.toggle();
				Config.save();
			}, null));
			ly += 26;
		}
		g.text(font, "Right Shift: menu", px + 8, py + ph - 2 * lh - 8, DIM);
		g.text(font, "End: panic (all off)", px + 8, py + ph - lh - 5, DIM);

		// ---- settings ----
		int rx0 = px + LEFT + 8;
		int rx1 = px + pw - 8;
		int top = py + HEAD + 8;
		int bottom = py + ph - 8;
		g.text(font, selected.name, rx0, top, ACCENT);
		g.text(font, selected.description, rx0, top + 11, DIM);

		int listTop = top + 26;
		int y = listTop - scroll;
		for (Setting s : selected.settings) {
			int h = heightOf(s);
			if (y >= listTop && y + h <= bottom) drawSetting(g, s, rx0, rx1, y);
			y += h + 3;
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

		if (s instanceof Setting.Header) {
			g.text(font, s.name.toUpperCase(), x0 + 2, y + 3, ACCENT);
			g.fill(x0, y + HEADER_ROW - 2, x1, y + HEADER_ROW - 1, 0xFF2E2E44);
			return;
		}

		int h = heightOf(s);
		g.fill(x0, y, x1, y + h, over(x0, y, x1 - x0, h) ? ROW_HOVER : ROW_BG);
		g.text(font, s.name, x0 + 7, ty, TEXT);

		if (s instanceof Setting.Bool b) {
			drawSwitch(g, x1 - 7 - 22, y + 4, 22, ROW - 8, b.value);
			hits.add(new Hit(x0, y, x1 - x0, ROW, v -> {
				b.value = !b.value;
				Config.save();
			}, null));
		} else if (s instanceof Setting.Mode mode) {
			String label = "< " + mode.get() + " >";
			int bw = font.width(label) + 14;
			int bx = x1 - 7 - bw;
			g.fill(bx, y + 3, bx + bw, y + ROW - 3, over(bx, y + 3, bw, ROW - 6) ? SELECT_BG : OFF);
			g.text(font, label, bx + 7, ty, TEXT);
			hits.add(new Hit(x0, y, x1 - x0, ROW, v -> {
				mode.cycle();
				Config.save();
			}, null));
		} else if (s instanceof Setting.Num n) {
			final int tw = 150;
			final int tx = x1 - 7 - tw;
			double frac = (n.value - n.min) / (n.max - n.min);
			g.fill(tx, y + 5, tx + tw, y + ROW - 5, OFF);
			g.fill(tx, y + 5, tx + (int) Math.round(frac * tw), y + ROW - 5, ACCENT);
			String shown = n.step >= 1 ? Integer.toString(n.i()) : String.format("%.2f", n.value);
			g.text(font, shown, tx + (tw - font.width(shown)) / 2, ty, TEXT);
			DoubleConsumer setter = v -> n.set(n.min + (v - tx) / (double) tw * (n.max - n.min));
			hits.add(new Hit(tx, y, tw, ROW, setter, setter));
		} else if (s instanceof Setting.Rgb c) {
			// line 1: swatch + hex under the name (left), RGB sliders (right)
			g.fill(x0 + 7, y + 18, x0 + 21, y + 32, c.argb());
			g.text(font, String.format("#%06X", c.rgb), x0 + 26, y + 21, DIM);
			final int sw = 56;
			final int gap = 4;
			final int sx = x1 - 7 - (3 * sw + 2 * gap);
			int[] tint = { 0xFFE05050, 0xFF50D060, 0xFF5080F0 };
			for (int ch = 0; ch < 3; ch++) {
				final int channel = ch;
				final int tx = sx + ch * (sw + gap);
				g.fill(tx, y + 4, tx + sw, y + 15, OFF);
				g.fill(tx, y + 4, tx + (int) Math.round(c.channel(ch) / 255.0 * sw), y + 15, tint[ch]);
				String val = Integer.toString(c.channel(ch));
				g.text(font, val, tx + (sw - font.width(val)) / 2, y + 5, TEXT);
				DoubleConsumer setter = v -> c.setChannel(channel, (int) Math.round((v - tx) / (double) sw * 255.0));
				hits.add(new Hit(tx, y + 2, sw, 15, setter, setter));
			}
			// line 2: palette
			final int size = 13;
			final int pgap = 3;
			int total = PALETTE.length * (size + pgap) - pgap;
			int palX = x1 - 7 - total;
			for (int i = 0; i < PALETTE.length; i++) {
				final int col = PALETTE[i];
				int sxp = palX + i * (size + pgap);
				boolean sel = c.rgb == col;
				g.fill(sxp - 1, y + 21, sxp + size + 1, y + 21 + size + 2, sel ? 0xFFFFFFFF : 0xFF000000);
				g.fill(sxp, y + 22, sxp + size, y + 22 + size, 0xFF000000 | col);
				hits.add(new Hit(sxp, y + 22, size, size, v -> {
					c.rgb = col;
					Config.save();
				}, null));
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
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(scrollY * 24)));
		return true;
	}

	@Override
	public void onClose() {
		Config.save();
		super.onClose();
	}
}
