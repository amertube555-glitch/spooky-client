package com.spooky.client.config;

import com.spooky.SpookyClient;
import com.spooky.client.module.Module;
import com.spooky.client.module.Modules;
import com.spooky.client.setting.Setting;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Saves module on/off state and every setting to config/spookyclient.properties. */
public final class Config {
	private Config() { }

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("spookyclient.properties");
	}

	private static String key(Module m, Setting s) {
		return m.id + "." + s.name.replace(' ', '_');
	}

	public static void load() {
		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(file())) {
			props.load(in);
		} catch (IOException e) {
			return; // first run: no file yet
		}
		for (Module m : Modules.ALL) {
			String en = props.getProperty(m.id + ".enabled");
			if (en != null) m.enabled = Boolean.parseBoolean(en.trim());
			for (Setting s : m.settings) {
				String v = props.getProperty(key(m, s));
				if (v != null) s.load(v);
			}
		}
	}

	public static void save() {
		Properties props = new Properties();
		for (Module m : Modules.ALL) {
			props.setProperty(m.id + ".enabled", Boolean.toString(m.enabled));
			for (Setting s : m.settings) {
				props.setProperty(key(m, s), s.save());
			}
		}
		try (OutputStream out = Files.newOutputStream(file())) {
			props.store(out, "Spooky Client settings");
		} catch (IOException e) {
			SpookyClient.LOGGER.warn("spooky-client: could not save config", e);
		}
	}
}
