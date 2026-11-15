package com.trolmastercard.sexmod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * {@code config/sexmod.json}. Same file name and keys as the 1.12.2 mod
 * ({@code shouldGenBuildings}, {@code shouldLoadOtherSkins}, {@code allowFlying}),
 * so an existing config keeps working. The 1.12.2 code parsed the file by
 * character offset; this reads it as JSON.
 */
public final class ModConfig {
   private static final String DEFAULT_JSON = "{\"shouldGenBuildings\":true,\"shouldLoadOtherSkins\":false,\"allowFlying\":true}";

   public static boolean shouldGenBuildings = true;
   public static boolean shouldLoadOtherSkins = false;
   public static boolean allowFlying = true;

   private ModConfig() {
   }

   public static void load() {
      Path file = FabricLoader.getInstance().getConfigDir().resolve("sexmod.json");
      try {
         if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.write(file, DEFAULT_JSON.getBytes(StandardCharsets.UTF_8));
         }
         String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
         JsonObject root = JsonParser.parseString(json).getAsJsonObject();
         shouldGenBuildings = bool(root, "shouldGenBuildings", true);
         shouldLoadOtherSkins = bool(root, "shouldLoadOtherSkins", false);
         allowFlying = bool(root, "allowFlying", true);
      } catch (IOException | RuntimeException e) {
         SexMod.LOGGER.warn("Could not read config/sexmod.json, using defaults: {}", e.toString());
      }
   }

   private static boolean bool(JsonObject root, String key, boolean fallback) {
      return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsBoolean() : fallback;
   }
}
