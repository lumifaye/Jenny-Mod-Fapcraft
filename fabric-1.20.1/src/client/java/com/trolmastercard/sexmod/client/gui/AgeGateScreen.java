package com.trolmastercard.sexmod.client.gui;

import com.trolmastercard.sexmod.SexMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.MultilineText;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * The adult content warning (1.12.2 {@code DebugWindow2}, a Swing window
 * there). It is shown over the title screen, and again before any world if
 * the title screen was skipped, until the player confirms being 18 or older.
 * <p>
 * "Don't ask again" writes the same marker file as 1.12.2
 * ({@code sexmod/dontAskAgain} in the game directory), so an existing answer
 * carries over. Answering "below 18" closes the game; unlike 1.12.2 it does
 * not delete the mod's jar.
 */
public class AgeGateScreen extends Screen {
   private static boolean confirmed;
   private static boolean markerChecked;

   @Nullable
   private final Screen parent;
   private MultilineText message = MultilineText.EMPTY;
   private CheckboxWidget dontAskAgain;

   public AgeGateScreen(@Nullable Screen parent) {
      super(Text.translatable("window.pornwarning.title"));
      this.parent = parent;
   }

   private static Path markerFile() {
      return FabricLoader.getInstance().getGameDir().resolve("sexmod").resolve("dontAskAgain");
   }

   /** Called every client tick: opens the warning when it still has to be answered. */
   public static void showIfNeeded(MinecraftClient client) {
      if (confirmed) {
         return;
      }
      if (!markerChecked) {
         markerChecked = true;
         if (Files.exists(markerFile())) {
            confirmed = true;
            return;
         }
      }
      Screen current = client.currentScreen;
      if (current instanceof TitleScreen || current == null && client.world != null) {
         client.setScreen(new AgeGateScreen(current));
      }
   }

   @Override
   protected void init() {
      // The 1.12.2 text was written for a Swing label and contains HTML line breaks.
      String text = I18n.translate("window.pornwarning.text").replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "");
      int textWidth = Math.min(this.width - 40, 360);
      this.message = MultilineText.create(this.textRenderer, Text.literal(text), textWidth);
      int textBottom = 40 + this.message.count() * 9;
      int centerX = this.width / 2;
      int y = Math.min(textBottom + 12, this.height - 76);

      Text dontAskText = Text.translatable("window.pornwarning.dontaskagain");
      int checkboxWidth = 24 + this.textRenderer.getWidth(dontAskText);
      this.dontAskAgain = new CheckboxWidget(centerX - checkboxWidth / 2, y, checkboxWidth, 20, dontAskText, false);
      this.addDrawableChild(this.dontAskAgain);
      this.addDrawableChild(
         ButtonWidget.builder(Text.translatable("window.pornwarning.am18"), button -> this.confirm()).dimensions(centerX - 155, y + 28, 150, 20).build()
      );
      this.addDrawableChild(
         ButtonWidget.builder(Text.translatable("window.pornwarning.not18"), button -> this.decline()).dimensions(centerX + 5, y + 28, 150, 20).build()
      );
   }

   private void confirm() {
      confirmed = true;
      if (this.dontAskAgain.isChecked()) {
         try {
            Path marker = markerFile();
            Files.createDirectories(marker.getParent());
            if (!Files.exists(marker)) {
               Files.createFile(marker);
            }
         } catch (IOException e) {
            SexMod.LOGGER.warn("Could not remember the adult content confirmation: {}", e.toString());
         }
      }
      if (this.client != null) {
         this.client.setScreen(this.parent);
      }
   }

   private void decline() {
      if (this.client != null) {
         this.client.scheduleStop();
      }
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      this.renderBackgroundTexture(context);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFF5555);
      this.message.drawCenterWithShadow(context, this.width / 2, 40);
      super.render(context, mouseX, mouseY, delta);
   }

   @Override
   public boolean shouldCloseOnEsc() {
      return false;
   }

   @Override
   public boolean shouldPause() {
      return true;
   }
}
