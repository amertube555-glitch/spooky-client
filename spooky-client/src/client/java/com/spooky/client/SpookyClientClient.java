package com.spooky.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.spooky.SpookyClient;
import com.spooky.client.config.Config;
import com.spooky.client.gui.ClickGui;
import com.spooky.client.module.Modules;
import com.spooky.client.module.StorageEsp;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class SpookyClientClient implements ClientModInitializer {
	private static KeyMapping menuKey;
	private static KeyMapping panicKey;

	@Override
	public void onInitializeClient() {
		Config.load();

		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(SpookyClient.MOD_ID, "main"));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.spookyclient.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
		panicKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.spookyclient.panic", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_END, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (menuKey.consumeClick()) {
				if (client.gui.screen() == null) client.gui.setScreen(new ClickGui());
			}
			// Panic is silent on purpose: everything off, menu closed, no message.
			while (panicKey.consumeClick()) {
				Modules.panic();
				Config.save();
				if (client.gui.screen() instanceof ClickGui) client.gui.setScreen(null);
			}
		});

		// Keep a list of storage blocks the server has sent us.
		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register(StorageEsp::onLoad);
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register(StorageEsp::onUnload);

		// World drawing (through-walls boxes and lines).
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
			Modules.PLAYER_ESP.renderWorld();
			Modules.STORAGE_ESP.renderWorld();
		});

		// Screen drawing (radar).
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(SpookyClient.MOD_ID, "radar"),
				(graphics, deltaTracker) -> Modules.RADAR.render(graphics));

		SpookyClient.LOGGER.info("spooky-client: client init OK (stage 2: menu, Player ESP, Storage ESP, Radar)");
	}
}
