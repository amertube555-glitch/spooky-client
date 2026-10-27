package com.spooky.client.module;

import java.util.List;

public final class Modules {
	private Modules() { }

	public static final PlayerEsp PLAYER_ESP = new PlayerEsp();
	public static final StorageEsp STORAGE_ESP = new StorageEsp();
	public static final ChunkFinder CHUNK_FINDER = new ChunkFinder();
	public static final Trails TRAILS = new Trails();
	public static final Radar RADAR = new Radar();
	public static final Alerts ALERTS = new Alerts();

	/** Order here is the order in the menu. */
	public static final List<Module> ALL = List.of(PLAYER_ESP, STORAGE_ESP, CHUNK_FINDER, TRAILS, RADAR, ALERTS);

	public static void panic() {
		for (Module m : ALL) m.enabled = false;
	}
}
