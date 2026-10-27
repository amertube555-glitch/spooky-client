package com.spooky.client.module;

public final class Modules {
	private Modules() { }

	/** Turns every module off instantly. Add new modules here as they are built. */
	public static void panic() {
		PlayerEsp.enabled = false;
	}
}
