package com.spooky.client;

import com.spooky.SpookyClient;
import net.fabricmc.api.ClientModInitializer;

public class SpookyClientClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SpookyClient.LOGGER.info("spooky-client: client init OK (stage 0, pipeline test)");
	}
}
