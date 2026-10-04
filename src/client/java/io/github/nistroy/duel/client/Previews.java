package io.github.nistroy.duel.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.nistroy.duel.Duel;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Aperçus d'arène reçus du serveur, en textures dynamiques le temps de la session. */
final class Previews {
	private static final Logger LOG = LoggerFactory.getLogger("duel");

	record Preview(ResourceLocation texture, int width, int height) {
	}

	private static final Map<String, Preview> PREVIEWS = new HashMap<>();

	private Previews() {
	}

	static void put(String arena, byte[] png) {
		try {
			NativeImage image = NativeImage.read(png);
			ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(Duel.MOD_ID, "preview/" + arena);
			Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(image));
			PREVIEWS.put(arena, new Preview(texture, image.getWidth(), image.getHeight()));
		} catch (IOException e) {
			LOG.warn("Aperçu de l'arène {} illisible", arena, e);
		}
	}

	static Preview get(String arena) {
		return PREVIEWS.get(arena);
	}

	static void clear() {
		PREVIEWS.values().forEach(p -> Minecraft.getInstance().getTextureManager().release(p.texture()));
		PREVIEWS.clear();
	}
}
