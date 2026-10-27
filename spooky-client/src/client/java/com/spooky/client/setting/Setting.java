package com.spooky.client.setting;

/** A single configurable value. Saved to / loaded from the config file as text. */
public abstract class Setting {
	public final String name;

	protected Setting(String name) {
		this.name = name;
	}

	public abstract String save();

	public abstract void load(String text);

	public static final class Bool extends Setting {
		public boolean value;

		public Bool(String name, boolean def) {
			super(name);
			this.value = def;
		}

		@Override
		public String save() {
			return Boolean.toString(value);
		}

		@Override
		public void load(String text) {
			value = Boolean.parseBoolean(text.trim());
		}
	}

	public static final class Num extends Setting {
		public double value;
		public final double min;
		public final double max;
		public final double step;

		public Num(String name, double def, double min, double max, double step) {
			super(name);
			this.min = min;
			this.max = max;
			this.step = step;
			this.value = def;
		}

		public void set(double v) {
			double snapped = Math.round(v / step) * step;
			value = Math.max(min, Math.min(max, snapped));
		}

		public float f() {
			return (float) value;
		}

		public int i() {
			return (int) Math.round(value);
		}

		@Override
		public String save() {
			return Double.toString(value);
		}

		@Override
		public void load(String text) {
			try {
				set(Double.parseDouble(text.trim()));
			} catch (NumberFormatException ignored) {
			}
		}
	}

	public static final class Mode extends Setting {
		public int index;
		public final String[] options;

		public Mode(String name, String... options) {
			super(name);
			this.options = options;
		}

		public String get() {
			return options[index];
		}

		public void cycle() {
			index = (index + 1) % options.length;
		}

		@Override
		public String save() {
			return Integer.toString(index);
		}

		@Override
		public void load(String text) {
			try {
				int i = Integer.parseInt(text.trim());
				if (i >= 0 && i < options.length) index = i;
			} catch (NumberFormatException ignored) {
			}
		}
	}

	/** A colour stored as 0xRRGGBB. */
	public static final class Rgb extends Setting {
		public int rgb;

		public Rgb(String name, int rgb) {
			super(name);
			this.rgb = rgb & 0xFFFFFF;
		}

		/** Opaque ARGB value ready for drawing. */
		public int argb() {
			return 0xFF000000 | rgb;
		}

		public int channel(int c) { // 0 = red, 1 = green, 2 = blue
			return (rgb >> (16 - 8 * c)) & 0xFF;
		}

		public void setChannel(int c, int v) {
			int shift = 16 - 8 * c;
			rgb = (rgb & ~(0xFF << shift)) | ((Math.max(0, Math.min(255, v))) << shift);
		}

		@Override
		public String save() {
			return Integer.toHexString(rgb);
		}

		@Override
		public void load(String text) {
			try {
				rgb = Integer.parseInt(text.trim(), 16) & 0xFFFFFF;
			} catch (NumberFormatException ignored) {
			}
		}
	}
}
