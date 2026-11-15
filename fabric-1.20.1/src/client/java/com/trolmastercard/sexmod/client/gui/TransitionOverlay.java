package com.trolmastercard.sexmod.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Fade to black and back, played at the end of a scene (the
 * {@code bjcBlackScreen} keyframe). 1.12.2 used a wipe texture over 1200 ms;
 * this is a plain fade over the same time.
 */
public final class TransitionOverlay {
   private static final int FADE_TICKS = 12;
   private static final int HOLD_TICKS = 12;
   private static final int TOTAL_TICKS = FADE_TICKS * 2 + HOLD_TICKS;

   private static int ticks = -1;

   private TransitionOverlay() {
   }

   public static void start() {
      if (ticks < 0) {
         ticks = 0;
      }
   }

   public static void cancel() {
      ticks = -1;
   }

   public static void tick() {
      if (ticks >= 0 && !MinecraftClient.getInstance().isPaused() && ++ticks > TOTAL_TICKS) {
         ticks = -1;
      }
   }

   public static void render(DrawContext context, float tickDelta) {
      if (ticks < 0) {
         return;
      }
      float time = ticks + tickDelta;
      float alpha;
      if (time < FADE_TICKS) {
         alpha = time / FADE_TICKS;
      } else if (time < FADE_TICKS + HOLD_TICKS) {
         alpha = 1.0F;
      } else {
         alpha = Math.max(0.0F, (TOTAL_TICKS - time) / FADE_TICKS);
      }
      int color = (int)(Math.min(1.0F, alpha) * 255.0F) << 24;
      context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), color);
   }
}
