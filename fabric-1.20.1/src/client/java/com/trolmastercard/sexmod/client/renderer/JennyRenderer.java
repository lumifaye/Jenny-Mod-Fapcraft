package com.trolmastercard.sexmod.client.renderer;

import com.trolmastercard.sexmod.client.model.JennyModel;
import com.trolmastercard.sexmod.entity.AbstractGirlNpcEntity;
import com.trolmastercard.sexmod.entity.Action;
import com.trolmastercard.sexmod.entity.BaseGirlEntity;
import com.trolmastercard.sexmod.entity.JennyEntity;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.DynamicGeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Renders Jenny. Two things go beyond a plain GeckoLib entity:
 * <ul>
 * <li>the {@code steve} bones (the scene player's body) are drawn with that
 * player's skin instead of Jenny's texture;</li>
 * <li>items are drawn at two empty marker bones: her weapon or bow at
 * {@code weapon}, and the price being handed over at {@code itemRenderer}
 * during the payment animation.</li>
 * </ul>
 * The item placement (angles, fan-out of the payment items) uses the numbers
 * of the 1.12.2 renderer. It has not been checked in a running game, so it is
 * the first place to look if something floats or points the wrong way.
 */
public class JennyRenderer extends DynamicGeoEntityRenderer<JennyEntity> {
   private static final IntIntPair UV_SPACE = IntIntPair.of(64, 64);
   /** X rotation of the held item, as in 1.12.2. */
   private static final float SLASH_SWORD_ROTATION = 140.0F;
   private static final float STAB_SWORD_ROTATION = 50.0F;
   private static final float BOW_ROTATION = 140.0F;

   public JennyRenderer(EntityRendererFactory.Context context) {
      super(context, new JennyModel());
      this.shadowRadius = 0.3F;
      this.addRenderLayer(new HeldItemLayer(this));
   }

   @Nullable
   @Override
   protected Identifier getTextureOverrideForBone(GeoBone bone, JennyEntity jenny, float partialTick) {
      return isScenePlayerBone(bone) ? ScenePlayerSkins.textureFor(jenny.getInteractionPlayerUUID()) : null;
   }

   private static boolean isScenePlayerBone(GeoBone bone) {
      for (GeoBone current = bone; current != null; current = current.getParent()) {
         if ("steve".equals(current.getName())) {
            return true;
         }
      }
      return false;
   }

   /**
    * The base class rescales UVs of an overridden bone by the ratio of the two
    * texture sizes, assuming the bone's UVs are in pixels of the override
    * texture. Here every bone's UVs are fractions of a 64x64 layout (Jenny's
    * 4096x4096 texture is that layout at 64x, a skin is that layout at 1x), so
    * no rescaling must happen: reporting one size for every texture makes the
    * ratio 1.
    */
   @Override
   protected IntIntPair computeTextureSize(Identifier texture) {
      return UV_SPACE;
   }

   private static final class HeldItemLayer extends BlockAndItemGeoLayer<JennyEntity> {
      HeldItemLayer(JennyRenderer renderer) {
         super(renderer);
      }

      @Nullable
      @Override
      protected ItemStack getStackForBone(GeoBone bone, JennyEntity jenny) {
         ItemStack stack = null;
         if ("weapon".equals(bone.getName())) {
            int attackMode = jenny.getDataTracker().get(AbstractGirlNpcEntity.ATTACK_MODE);
            if (attackMode == 1) {
               stack = jenny.getDataTracker().get(AbstractGirlNpcEntity.WEAPON);
            } else if (attackMode == 2) {
               stack = jenny.getDataTracker().get(AbstractGirlNpcEntity.BOW);
            }
         } else if ("itemRenderer".equals(bone.getName()) && jenny.getCurrentAction() == Action.PAYMENT) {
            stack = paymentStack(jenny);
         }
         return stack == null || stack.isEmpty() ? null : stack;
      }

      /** What the player hands over for the scene picked in the menu. */
      @Nullable
      private static ItemStack paymentStack(JennyEntity jenny) {
         switch (jenny.getDataTracker().get(BaseGirlEntity.GIRL_HAND_STATES)) {
            case "blowjob":
               return new ItemStack(Items.EMERALD, 3);
            case "boobjob":
               return new ItemStack(Items.ENDER_PEARL, 2);
            case "doggy":
               return new ItemStack(Items.DIAMOND, 2);
            case "strip":
               return new ItemStack(Items.GOLD_INGOT, 1);
            default:
               return null;
         }
      }

      @Override
      protected ModelTransformationMode getTransformTypeForStack(GeoBone bone, ItemStack stack, JennyEntity jenny) {
         return ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
      }

      @Override
      protected void renderStackForBone(
         MatrixStack matrices, GeoBone bone, ItemStack stack, JennyEntity jenny, VertexConsumerProvider buffers, float partialTick, int light, int overlay
      ) {
         if ("weapon".equals(bone.getName())) {
            float rotation;
            if (stack.getItem() instanceof BowItem) {
               rotation = BOW_ROTATION;
            } else if (jenny.getCurrentAction() == Action.ATTACK && jenny.nextAttack == 0) {
               matrices.translate(0.0, -0.03, -0.2);
               rotation = STAB_SWORD_ROTATION;
            } else {
               rotation = SLASH_SWORD_ROTATION;
            }
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotation));
            super.renderStackForBone(matrices, bone, stack, jenny, buffers, partialTick, light, overlay);
            return;
         }
         // Payment: one item per unit of the price, fanned out like a hand of cards.
         ItemStack single = stack.copyWithCount(1);
         for (int i = 0; i < stack.getCount(); i++) {
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(2.5F));
            if (i == 1) {
               matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-15.0F));
               matrices.translate(0.0, 0.0, -0.025);
            } else if (i == 2) {
               matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(15.0F));
               matrices.translate(0.0, 0.0, 0.025);
            }
            super.renderStackForBone(matrices, bone, single, jenny, buffers, partialTick, light, overlay);
            matrices.pop();
         }
      }
   }
}
