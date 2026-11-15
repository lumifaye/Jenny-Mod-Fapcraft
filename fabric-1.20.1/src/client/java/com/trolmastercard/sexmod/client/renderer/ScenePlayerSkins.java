package com.trolmastercard.sexmod.client.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.trolmastercard.sexmod.SexMod;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

/**
 * Textures for the scene player's body (the model's {@code steve} bones).
 * <p>
 * Those bones are UV-mapped onto a standard 64x64 player skin. The parts a
 * skin has no pixels for sample two swatches in the unused top-left corner
 * of the skin layout, so each skin gets a copy with those swatches painted in
 * (1.12.2 {@code SkinTextureCache.tint}). The copy is read back from the GPU,
 * which works for downloaded and built-in skins alike.
 */
public final class ScenePlayerSkins {
   private static final int CAPACITY = 64;
   /** ABGR, the byte order of {@link NativeImage}: rgb(245, 199, 165). */
   private static final int BASE_SKIN_COLOR = 0xFFA5C7F5;
   /** ABGR: rgb(245, 157, 169). */
   private static final int BLUSH_COLOR = 0xFFA99DF5;

   private static final Map<UUID, SceneSkin> CACHE = new LinkedHashMap<UUID, SceneSkin>(16, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<UUID, SceneSkin> eldest) {
         if (this.size() <= CAPACITY) {
            return false;
         }
         eldest.getValue().destroy();
         return true;
      }
   };

   private ScenePlayerSkins() {
   }

   @Nullable
   private static AbstractClientPlayerEntity resolve(@Nullable UUID playerUuid) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (playerUuid != null && client.world != null) {
         PlayerEntity player = client.world.getPlayerByUuid(playerUuid);
         if (player instanceof AbstractClientPlayerEntity) {
            return (AbstractClientPlayerEntity)player;
         }
      }
      return client.player;
   }

   /** {@code true} if the scene player's skin uses the 3 px wide arm model. */
   public static boolean isSlim(@Nullable UUID playerUuid) {
      AbstractClientPlayerEntity player = resolve(playerUuid);
      return player != null && "slim".equals(player.getModel());
   }

   /** Texture for the {@code steve} bones, or {@code null} to keep the model's own texture. */
   @Nullable
   public static Identifier textureFor(@Nullable UUID playerUuid) {
      AbstractClientPlayerEntity player = resolve(playerUuid);
      if (player == null) {
         return null;
      }
      Identifier source = player.getSkinTexture();
      SceneSkin entry = CACHE.get(player.getUuid());
      // The source changes when the real skin finishes downloading; rebuild then.
      if (entry != null && entry.source.equals(source)) {
         return entry.texture;
      }
      if (entry != null) {
         entry.destroy();
      }
      Identifier texture = createSceneSkin(player.getUuid(), source);
      CACHE.put(player.getUuid(), new SceneSkin(source, texture, !texture.equals(source)));
      return texture;
   }

   /** Returns {@code source} itself if the copy cannot be made. */
   private static Identifier createSceneSkin(UUID playerUuid, Identifier source) {
      MinecraftClient client = MinecraftClient.getInstance();
      NativeImage image = null;
      try {
         AbstractTexture skin = client.getTextureManager().getTexture(source);
         RenderSystem.bindTexture(skin.getGlId());
         int width = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
         int height = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
         if (width < 64 || height < 64 || width != height) {
            return source;
         }
         image = new NativeImage(width, height, false);
         image.loadFromTextureImage(0, false);
         int scale = width / 64;
         image.fillRect(0, 0, 4 * scale, 3 * scale, BASE_SKIN_COLOR);
         image.fillRect(4 * scale, 0, 3 * scale, 3 * scale, BLUSH_COLOR);
         Identifier id = SexMod.id("scene_skin/" + playerUuid.toString());
         client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
         return id;
      } catch (RuntimeException e) {
         if (image != null) {
            image.close();
         }
         SexMod.LOGGER.warn("Could not prepare the scene skin of {}: {}", playerUuid, e.toString());
         return source;
      }
   }

   public static void clear() {
      for (SceneSkin entry : CACHE.values()) {
         entry.destroy();
      }
      CACHE.clear();
   }

   private static final class SceneSkin {
      final Identifier source;
      final Identifier texture;
      final boolean owned;

      SceneSkin(Identifier source, Identifier texture, boolean owned) {
         this.source = source;
         this.texture = texture;
         this.owned = owned;
      }

      void destroy() {
         if (this.owned) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(this.texture);
         }
      }
   }
}
