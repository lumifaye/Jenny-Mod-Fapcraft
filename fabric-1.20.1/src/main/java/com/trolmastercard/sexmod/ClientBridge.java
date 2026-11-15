package com.trolmastercard.sexmod;

import com.trolmastercard.sexmod.entity.BaseGirlEntity;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * Everything the common entity code needs from the client. In 1.12.2 these
 * were {@code @SideOnly(Side.CLIENT)} methods reaching straight into
 * {@code Minecraft.getMinecraft()}; on Fabric the common source set cannot see
 * client classes, so the calls go through this interface. The defaults are the
 * dedicated-server behaviour (nothing happens); {@code ClientBridgeImpl}
 * overrides all of them.
 */
public interface ClientBridge {
   /** The player in front of this screen, or {@code null} on a dedicated server. */
   default PlayerEntity localPlayer() {
      return null;
   }

   default UUID localPlayerUuid() {
      return null;
   }

   default void sendToServer(Identifier channel, PacketByteBuf buf) {
   }

   default void showChatMessage(String message) {
   }

   default String translate(String key) {
      return key;
   }

   /** Opens the girl's action menu. {@code costs} may be {@code null}, as may its entries. */
   default void openActionMenu(BaseGirlEntity girl, String[] options, ItemStack[] costs) {
   }

   /** {@code true} while the "speed up" key (sneak) is held during a scene. */
   default boolean isFastKeyDown() {
      return false;
   }

   /** Freezes or releases the local player's input for a scene. */
   default void setMovementLocked(boolean locked) {
   }

   default void showHornyMeter() {
   }

   default void hideHornyMeter() {
   }

   default void resetHornyMeter() {
   }

   default void addToHornyMeter(double amount) {
   }

   /** Plays the fade-to-black transition used at the end of some scenes. */
   default void playBlackScreenTransition() {
   }

   /** Restarts the girl's action animation from its first frame. */
   default void restartActionAnimation(BaseGirlEntity girl) {
   }
}
