package com.spooky.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.spooky.SpookyClient;
import com.spooky.client.config.Config;
import com.spooky.client.gui.ClickGui;
import com.spooky.client.module.ChunkFinder;
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
			// Panic is silent: everything off, menu closed, no message.
			while (panicKey.consumeClick()) {
				Modules.panic();
				Config.save();
				if (client.gui.screen() instanceof ClickGui) client.gui.setScreen(null);
			}
			Modules.CHUNK_FINDER.tick();
			Modules.TRAILS.tick();
			Modules.ALERTS.tick();
		});

		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((be, level) -> {
			StorageEsp.onLoad(be, level);
			ChunkFinder.onLoad(be, level);
		});
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((be, level) -> {
			StorageEsp.onUnload(be, level);
			ChunkFinder.onUnload(be, level);
		});

		LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
			Modules.PLAYER_ESP.renderWorld();
			Modules.STORAGE_ESP.renderWorld();
			Modules.CHUNK_FINDER.renderWorld();
			Modules.TRAILS.renderWorld();
		});

		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(SpookyClient.MOD_ID, "overlay"),
				(graphics, deltaTracker) -> {
					Modules.RADAR.render(graphics);
					Modules.ALERTS.render(graphics);
					Modules.TRAILS.renderHud(graphics);
				});

		SpookyClient.LOGGER.info("spooky-client: client init OK (stage 3)");
	}
}
