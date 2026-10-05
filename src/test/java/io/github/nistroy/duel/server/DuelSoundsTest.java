package io.github.nistroy.duel.server;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Le serveur joue le son par son id : sans entrée dans sounds.json ni fichier .ogg, le client ne joue rien. */
class DuelSoundsTest {
	@Test
	void bothSoundPartsAreDeclaredWithTheirFiles() throws IOException {
		JsonObject sounds;
		try (InputStream in = resource("/assets/duel/sounds.json")) {
			sounds = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}

		for (String id : new String[] {DuelSounds.ACCEPT, DuelSounds.ARENA}) {
			JsonObject sound = sounds.getAsJsonObject(id);
			assertNotNull(sound, "sounds.json déclare " + id);
			String file = sound.getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString();
			assertEquals("duel:" + id, file);
			try (InputStream ogg = resource("/assets/duel/sounds/" + id + ".ogg")) {
				assertTrue(ogg.readAllBytes().length > 0, id + ".ogg non vide");
			}
		}
	}

	private static InputStream resource(String path) {
		InputStream in = DuelSoundsTest.class.getResourceAsStream(path);
		assertNotNull(in, path + " présent dans le jar");
		return in;
	}
}
