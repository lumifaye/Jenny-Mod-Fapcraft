package com.trolmastercard.sexmod.client;

import com.trolmastercard.sexmod.ClientBridge;
import com.trolmastercard.sexmod.client.gui.ActionMenuScreen;
import com.trolmastercard.sexmod.client.gui.HornyMeterHud;
import com.trolmastercard.sexmod.client.gui.TransitionOverlay;
import com.trolmastercard.sexmod.entity.BaseGirlEntity;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** The client half of {@link ClientBridge}: everything common code may ask of the client. */
public final class ClientBridgeImpl implements ClientBridge {
   private static MinecraftClient client() {
      return MinecraftClient.getInstance();
   }

   @Override
   public PlayerEntity localPlayer() {
      return client().player;
   }

   @Override
   public UUID localPlayerUuid() {
      return client().player == null ? null : client().player.getUuid();
   }

   @Override
   public void sendToServer(Identifier channel, PacketByteBuf buf) {
      if (client().getNetworkHandler() != null) {
         ClientPlayNetworking.send(channel, buf);
      }
   }

   @Override
   public void showChatMessage(String message) {
      if (client().inGameHud != null) {
         client().inGameHud.getChatHud().addMessage(Text.literal(message));
      }
   }

   @Override
   public String translate(String key) {
      return I18n.translate(key);
   }

   @Override
   public void openActionMenu(BaseGirlEntity girl, String[] options, ItemStack[] costs) {
      client().setScreen(new ActionMenuScreen(girl, options, costs));
   }

   @Override
   public boolean isFastKeyDown() {
      return SceneControls.isFastKeyDown();
   }

   @Override
   public void setMovementLocked(boolean locked) {
      SceneControls.setLocked(locked);
   }

   @Override
   public void showHornyMeter() {
      HornyMeterHud.show();
   }

   @Override
   public void hideHornyMeter() {
      HornyMeterHud.hide();
   }

   @Override
   public void resetHornyMeter() {
      HornyMeterHud.reset();
   }

   @Override
   public void addToHornyMeter(double amount) {
      HornyMeterHud.add(amount);
   }

   @Override
   public void playBlackScreenTransition() {
      TransitionOverlay.start();
   }

   @Override
   public void restartActionAnimation(BaseGirlEntity girl) {
      if (girl.actionController != null) {
         girl.actionController.forceAnimationReset();
      }
   }
}
