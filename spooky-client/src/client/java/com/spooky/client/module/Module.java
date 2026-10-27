package com.spooky.client.module;

import com.spooky.client.setting.Setting;
import java.util.ArrayList;
import java.util.List;

public abstract class Module {
	public final String id;
	public final String name;
	public final String description;
	public boolean enabled = false;
	public final List<Setting> settings = new ArrayList<>();

	protected Module(String id, String name, String description) {
		this.id = id;
		this.name = name;
		this.description = description;
	}

	protected <T extends Setting> T add(T setting) {
		settings.add(setting);
		return setting;
	}

	public void toggle() {
		enabled = !enabled;
	}
}
