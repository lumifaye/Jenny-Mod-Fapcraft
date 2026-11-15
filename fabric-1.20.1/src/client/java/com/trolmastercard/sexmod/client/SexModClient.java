package com.trolmastercard.sexmod.client;

import com.trolmastercard.sexmod.SexMod;
import com.trolmastercard.sexmod.client.gui.AgeGateScreen;
import com.trolmastercard.sexmod.client.gui.HornyMeterHud;
import com.trolmastercard.sexmod.client.gui.TransitionOverlay;
import com.trolmastercard.sexmod.client.renderer.EllieRenderer;
import com.trolmastercard.sexmod.client.renderer.JennyRenderer;
import com.trolmastercard.sexmod.client.renderer.ScenePlayerSkins;
import com.trolmastercard.sexmod.entity.BaseGirlEntity;
import com.trolmastercard.sexmod.entity.SexModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/** Client entry point: renderers, HUD, packet receivers, scene controls and the 18+ gate. */
public class SexModClient implements ClientModInitializer {
   @Override
   public void onInitializeClient() {
      SexMod.CLIENT = new ClientBridgeImpl();

      EntityRendererRegistry.register(SexModEntities.JENNY, JennyRenderer::new);
      EntityRendererRegistry.register(SexModEntities.ELLIE, EllieRenderer::new);
      ClientPackets.register();

      ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
         if (entity instanceof BaseGirlEntity) {
            BaseGirlEntity.onGirlLoaded((BaseGirlEntity)entity);
         }
      });
      ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
         if (entity instanceof BaseGirlEntity) {
            BaseGirlEntity.onGirlUnloaded((BaseGirlEntity)entity);
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
         BaseGirlEntity.clearClientGirls();
         SceneControls.reset();
         HornyMeterHud.hide();
         TransitionOverlay.cancel();
         ScenePlayerSkins.clear();
      }));

      ClientTickEvents.START_CLIENT_TICK.register(SceneControls::tick);
      ClientTickEvents.END_CLIENT_TICK.register(client -> {
         TransitionOverlay.tick();
         AgeGateScreen.showIfNeeded(client);
      });

      HudRenderCallback.EVENT.register((context, tickDelta) -> {
         HornyMeterHud.render(context, tickDelta);
         TransitionOverlay.render(context, tickDelta);
      });
   }
}
