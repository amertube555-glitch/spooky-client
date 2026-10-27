package com.spooky.client.module;

import java.util.List;

public final class Modules {
	private Modules() { }

	public static final PlayerEsp PLAYER_ESP = new PlayerEsp();
	public static final StorageEsp STORAGE_ESP = new StorageEsp();
	public static final Radar RADAR = new Radar();

	/** Order here is the order in the menu. Add new modules to this list. */
	public static final List<Module> ALL = List.of(PLAYER_ESP, STORAGE_ESP, RADAR);

	/** Turns every module off instantly. */
	public static void panic() {
		for (Module m : ALL) m.enabled = false;
	}
}
