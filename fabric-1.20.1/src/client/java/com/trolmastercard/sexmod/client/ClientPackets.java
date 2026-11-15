package com.trolmastercard.sexmod.client;

import com.trolmastercard.sexmod.client.gui.HornyMeterHud;
import com.trolmastercard.sexmod.entity.BaseGirlEntity;
import com.trolmastercard.sexmod.networking.ModPackets;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;

/** Receivers for the server-to-client channels of {@link ModPackets}. */
public final class ClientPackets {
   private ClientPackets() {
   }

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(ModPackets.SEND_CHAT_MESSAGE, (client, handler, buf, sender) -> {
         String line = buf.readString();
         client.execute(() -> client.inGameHud.getChatHud().addMessage(Text.literal(line)));
      });
      ClientPlayNetworking.registerGlobalReceiver(ModPackets.SET_PLAYER_MOVEMENT, (client, handler, buf, sender) -> {
         boolean free = buf.readBoolean();
         client.execute(() -> {
            SceneControls.setLocked(!free);
            if (free) {
               HornyMeterHud.hide();
            }
         });
      });
      ClientPlayNetworking.registerGlobalReceiver(ModPackets.RESET_CONTROLLER, (client, handler, buf, sender) -> {
         UUID girlId = buf.readUuid();
         client.execute(() -> {
            BaseGirlEntity girl = BaseGirlEntity.getClientGirlEntity(girlId);
            if (girl != null && girl.actionController != null) {
               girl.actionController.forceAnimationReset();
            }
         });
      });
   }
}
