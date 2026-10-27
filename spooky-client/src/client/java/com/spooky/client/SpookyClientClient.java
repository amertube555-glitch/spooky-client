package com.spooky.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.spooky.SpookyClient;
import com.spooky.client.module.Modules;
import com.spooky.client.module.PlayerEsp;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class SpookyClientClient implements ClientModInitializer {
	private static KeyMapping togglePlayerEsp;
	private static KeyMapping panicKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(SpookyClient.MOD_ID, "main"));

		togglePlayerEsp = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.spookyclient.player_esp", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, category));
		panicKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.spookyclient.panic", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_END, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) return;

			while (togglePlayerEsp.consumeClick()) {
				PlayerEsp.enabled = !PlayerEsp.enabled;
				client.player.sendSystemMessage(Component.literal(
						"Player ESP: " + (PlayerEsp.enabled ? "ON" : "OFF")));
			}
			// Panic is silent on purpose: no message, everything off.
			while (panicKey.consumeClick()) {
				Modules.panic();
			}
		});

		LevelRenderEvents.BEFORE_GIZMOS.register(context -> PlayerEsp.render());

		SpookyClient.LOGGER.info("spooky-client: client init OK (stage 1: Player ESP)");
	}
}
