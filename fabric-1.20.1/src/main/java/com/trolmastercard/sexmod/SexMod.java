package com.trolmastercard.sexmod;

import com.trolmastercard.sexmod.entity.BaseGirlEntity;
import com.trolmastercard.sexmod.entity.SexModEntities;
import com.trolmastercard.sexmod.item.ModItems;
import com.trolmastercard.sexmod.networking.ModPackets;
import com.trolmastercard.sexmod.potion.HornyPotion;
import com.trolmastercard.sexmod.util.SoundHandler;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Mod entry point (Fabric 1.20.1). Counterpart of the 1.12.2 {@code Main} +
 * {@code CommonProxy}: prints the banner, loads the config and registers
 * everything that exists on both sides. Client-only setup is in
 * {@code SexModClient}.
 */
public class SexMod implements ModInitializer {
   public static final String MOD_ID = "sexmod";
   public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

   /** Client-only operations; a no-op on a dedicated server, replaced by the client entry point. */
   public static ClientBridge CLIENT = new ClientBridge() {
   };

   public static Identifier id(String path) {
      return new Identifier(MOD_ID, path);
   }

   @Override
   public void onInitialize() {
      printBanner();
      ModConfig.load();
      SoundHandler.registerSounds();
      HornyPotion.register();
      SexModEntities.registerEntities();
      ModItems.registerAll();
      ModPackets.registerServerReceivers();

      ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
         if (entity instanceof BaseGirlEntity) {
            BaseGirlEntity.onGirlLoaded((BaseGirlEntity)entity);
         }
      });
      ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
         if (entity instanceof BaseGirlEntity) {
            BaseGirlEntity.onGirlUnloaded((BaseGirlEntity)entity);
         }
      });
      // Stale statics across world reloads cause ghost girls, so drop them when the server stops.
      ServerLifecycleEvents.SERVER_STOPPED.register(server -> BaseGirlEntity.clearServerGirls());
   }

   /**
    * Prints {@code banner.txt} to the log. The banner file is shared with the
    * 1.12.2 build and is printed unmodified, as the licence terms require.
    */
   private static void printBanner() {
      try (InputStream in = SexMod.class.getResourceAsStream("/banner.txt")) {
         if (in == null) {
            LOGGER.warn("Could not read banner.txt: resource missing");
            return;
         }
         BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
         String line;
         while ((line = reader.readLine()) != null) {
            LOGGER.info(line);
         }
      } catch (Exception e) {
         LOGGER.warn("Could not read banner.txt: {}", e.toString());
      }
      LOGGER.info("Fabric 1.20.1 port, build {}", BuildInfo.VERSION);
   }
}
