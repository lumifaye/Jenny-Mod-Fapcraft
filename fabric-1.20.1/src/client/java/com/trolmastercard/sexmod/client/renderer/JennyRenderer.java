package com.trolmastercard.sexmod.client.renderer;

import com.trolmastercard.sexmod.client.model.JennyModel;
import com.trolmastercard.sexmod.entity.Action;
import com.trolmastercard.sexmod.entity.BaseGirlEntity;
import com.trolmastercard.sexmod.entity.JennyEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Renders Jenny. On top of {@link GirlRenderer} she shows the price being
 * handed over at the {@code itemRenderer} bone during the payment animation.
 */
public class JennyRenderer extends GirlRenderer<JennyEntity> {
   public JennyRenderer(EntityRendererFactory.Context context) {
      super(context, new JennyModel(), 140.0F, 50.0F, 140.0F, new Vec3d(0.0, -0.03, -0.2));
   }

   @Nullable
   @Override
   protected ItemStack getItemForBone(GeoBone bone, JennyEntity jenny) {
      if ("itemRenderer".equals(bone.getName())) {
         return jenny.getCurrentAction() == Action.PAYMENT ? paymentStack(jenny) : null;
      }
      return super.getItemForBone(bone, jenny);
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
   protected void renderItemAtBone(
      ItemLayer<JennyEntity> layer,
      MatrixStack matrices,
      GeoBone bone,
      ItemStack stack,
      JennyEntity jenny,
      VertexConsumerProvider buffers,
      float partialTick,
      int light,
      int overlay
   ) {
      if (!"itemRenderer".equals(bone.getName())) {
         super.renderItemAtBone(layer, matrices, bone, stack, jenny, buffers, partialTick, light, overlay);
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
         layer.draw(matrices, bone, single, jenny, buffers, partialTick, light, overlay);
         matrices.pop();
      }
   }
}
