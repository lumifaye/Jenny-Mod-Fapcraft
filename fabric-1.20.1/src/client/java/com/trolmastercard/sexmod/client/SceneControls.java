package com.trolmastercard.sexmod.client;

import com.trolmastercard.sexmod.client.gui.HornyMeterHud;
import com.trolmastercard.sexmod.entity.BaseGirlEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;

/**
 * Input while the local player is in a scene (1.12.2 {@code HandlePlayerMovement}).
 * <p>
 * While locked, the player's {@link Input} is swapped for one that never
 * moves, and attack/use/drop/inventory presses are swallowed. Two keys keep a
 * meaning: holding <b>sneak</b> asks the girl for the faster variant of the
 * current action, and pressing <b>jump</b> with a full meter finishes the scene.
 * <p>
 * The lock releases itself if no girl is bound to the player for
 * {@link #ORPHAN_TIMEOUT_TICKS} ticks (she died, was unloaded, the server
 * reset her), so a lost packet cannot leave the player stuck.
 */
public final class SceneControls {
   private static final int ORPHAN_TIMEOUT_TICKS = 100;

   private static boolean locked;
   private static boolean fastKeyDown;
   private static boolean jumpWasDown;
   private static int orphanTicks;

   private SceneControls() {
   }

   public static boolean isLocked() {
      return locked;
   }

   public static void setLocked(boolean lock) {
      locked = lock;
      orphanTicks = 0;
      if (!lock) {
         fastKeyDown = false;
      }
   }

   /** {@code true} while the local player is in a scene and holds the "faster" key. */
   public static boolean isFastKeyDown() {
      return locked && fastKeyDown;
   }

   public static void reset() {
      locked = false;
      fastKeyDown = false;
      jumpWasDown = false;
      orphanTicks = 0;
   }

   public static void tick(MinecraftClient client) {
      if (client.player == null) {
         reset();
         return;
      }
      if (!locked) {
         if (client.player.input instanceof LockedInput) {
            client.player.input = new KeyboardInput(client.options);
         }
         return;
      }
      if (!(client.player.input instanceof LockedInput)) {
         client.player.input = new LockedInput();
      }
      GameOptions options = client.options;
      swallow(options.attackKey);
      swallow(options.useKey);
      swallow(options.pickItemKey);
      swallow(options.dropKey);
      swallow(options.swapHandsKey);
      swallow(options.inventoryKey);

      BaseGirlEntity girl = BaseGirlEntity.getActiveScene(client.player.getUuid(), true);
      if (girl == null) {
         fastKeyDown = false;
         if (++orphanTicks > ORPHAN_TIMEOUT_TICKS) {
            setLocked(false);
            HornyMeterHud.hide();
         }
         return;
      }
      orphanTicks = 0;

      boolean inGame = client.currentScreen == null;
      fastKeyDown = inGame && options.sneakKey.isPressed();
      boolean jumpDown = inGame && options.jumpKey.isPressed();
      if (fastKeyDown) {
         girl.triggerFastSexAction();
      }
      if (jumpDown && !jumpWasDown && HornyMeterHud.isFull()) {
         HornyMeterHud.markFinishing();
         girl.triggerCumAction();
      }
      jumpWasDown = jumpDown;
   }

   private static void swallow(KeyBinding key) {
      while (key.wasPressed()) {
         // discard queued presses
      }
      key.setPressed(false);
   }

   /** An input that never moves, jumps or sneaks. */
   private static final class LockedInput extends Input {
      @Override
      public void tick(boolean slowDown, float slowDownFactor) {
         this.movementForward = 0.0F;
         this.movementSideways = 0.0F;
         this.pressingForward = false;
         this.pressingBack = false;
         this.pressingLeft = false;
         this.pressingRight = false;
         this.jumping = false;
         this.sneaking = false;
      }
   }
}
