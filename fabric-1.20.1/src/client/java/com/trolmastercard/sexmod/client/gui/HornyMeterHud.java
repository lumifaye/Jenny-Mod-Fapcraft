package com.trolmastercard.sexmod.client.gui;

import com.trolmastercard.sexmod.SexMod;
import com.trolmastercard.sexmod.client.SceneControls;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * The meter shown during a scene. Scene keyframes fill it; once it is full,
 * the finish key ends the scene. The layout and texture regions are those of
 * the 1.12.2 HUD ({@code hornymeter.png} / {@code buttons.png}, both 256x256).
 */
public final class HornyMeterHud {
   private static final Identifier BUTTONS = SexMod.id("textures/gui/buttons.png");
   private static final Identifier METER = SexMod.id("textures/gui/hornymeter.png");
   /** Ticks for the meter to slide in. */
   private static final float SLIDE_IN_TICKS = 10.0F;
   private static final float SLIDE_OUT_TICKS = 15.0F;

   private static boolean visible;
   private static boolean finishing;
   private static double value;
   private static double shownValue;
   private static float slideIn;
   private static float slideOut;

   private HornyMeterHud() {
   }

   public static void show() {
      if (!visible) {
         reset();
         visible = true;
      }
   }

   public static void hide() {
      reset();
      visible = false;
   }

   public static void reset() {
      value = 0.0;
      shownValue = 0.0;
      finishing = false;
      slideIn = 0.0F;
      slideOut = 0.0F;
   }

   public static void add(double amount) {
      value = Math.min(1.0, value + amount);
   }

   public static boolean isFull() {
      return visible && value >= 1.0;
   }

   /** The finish key was accepted: the fill slides out. */
   public static void markFinishing() {
      finishing = true;
   }

   public static void render(DrawContext context, float tickDelta) {
      if (!visible) {
         return;
      }
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.options.hudHidden) {
         return;
      }
      float frameTicks = client.getLastFrameDuration();
      slideIn = Math.min(1.0F, slideIn + frameTicks / SLIDE_IN_TICKS);
      shownValue = MathHelper.lerp(Math.min(1.0, frameTicks * 0.3), shownValue, value);

      context.getMatrices().push();
      context.getMatrices().scale(0.75F, 0.75F, 1.0F);
      context.drawTexture(METER, 10, (int)MathHelper.lerp(slideIn, -200.0F, 10.0F), 0, 0, 146, 175);
      if (!finishing) {
         int fillHeight = (int)MathHelper.lerp(shownValue, 0.0, 160.0);
         int fillV = (int)MathHelper.lerp(shownValue, 167.0, 8.0);
         double fillY = MathHelper.lerp(shownValue, 178.0, 18.0);
         context.drawTexture(METER, 67, (int)MathHelper.lerp((double)slideIn, -45.0, fillY), 159, fillV, 32, fillHeight);
         double empty = 1.0 - shownValue;
         int capY = (int)MathHelper.lerp((double)slideIn, -58.0, MathHelper.lerp(empty, 178.0, 149.0));
         int capV = (int)MathHelper.lerp(empty, 169.0, 141.0);
         int capHeight = (int)MathHelper.lerp(empty, 1.0, 29.0);
         context.drawTexture(METER, 120, capY, 212, capV, 28, capHeight);
         context.drawTexture(METER, 18, capY, 212, capV, 28, capHeight);
      } else {
         slideOut += frameTicks / SLIDE_OUT_TICKS;
         context.drawTexture(METER, 67, (int)MathHelper.lerp(Math.min(slideOut, 1.0F), 18.0F, -300.0F), 159, 8, 32, 160);
      }
      context.getMatrices().pop();

      // Key hints, drawn from buttons.png at the scale the 1.12.2 HUD used.
      context.getMatrices().push();
      context.getMatrices().scale(0.35F, 0.35F, 1.0F);
      if (value >= 1.0) {
         context.drawTexture(BUTTONS, 240, 160, 0, 108 + (finishing ? 54 : 0), 256, 52);
      }
      if (!finishing) {
         context.drawTexture(BUTTONS, (int)MathHelper.lerp(slideIn, -200.0F, 98.0F), 405, 0, SceneControls.isFastKeyDown() ? 54 : 0, 158, 54);
      }
      context.getMatrices().pop();

      // The button art shows the default keys; say which keys are bound right now.
      if (!finishing && slideIn >= 1.0F) {
         Text hint = value >= 1.0
            ? Text.translatable("sexmod.hud.finish", client.options.jumpKey.getBoundKeyLocalizedText())
            : Text.translatable("sexmod.hud.faster", client.options.sneakKey.getBoundKeyLocalizedText());
         context.drawTextWithShadow(client.textRenderer, hint, 8, 164, 0xFFFFFF);
      }
   }
}
