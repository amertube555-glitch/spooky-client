package com.spooky;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpookyClient implements ModInitializer {
	public static final String MOD_ID = "spookyclient";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("spooky-client: common init OK");
	}
}
