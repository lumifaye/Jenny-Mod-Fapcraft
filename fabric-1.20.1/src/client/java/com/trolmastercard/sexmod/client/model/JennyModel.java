package com.trolmastercard.sexmod.client.model;

import com.trolmastercard.sexmod.SexMod;
import com.trolmastercard.sexmod.client.renderer.ScenePlayerSkins;
import com.trolmastercard.sexmod.entity.Action;
import com.trolmastercard.sexmod.entity.JennyEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * Jenny's model: geometry by outfit, plus the per-frame bone state that is
 * not part of any animation.
 * <p>
 * Bones belong to the baked model, which every Jenny shares, and GeckoLib
 * does not reset a bone's hidden flag between entities. So every flag this
 * class touches is written for every entity on every frame, never only when
 * it should change.
 */
public class JennyModel extends GeoModel<JennyEntity> {
   private static final Identifier NUDE = SexMod.id("geo/jenny/jennynude.geo.json");
   private static final Identifier DRESSED = SexMod.id("geo/jenny/jennydressed.geo.json");
   private static final Identifier TEXTURE = SexMod.id("textures/entity/jenny/jenny.png");
   private static final Identifier ANIMATIONS = SexMod.id("animations/jenny/jenny.animation.json");

   /** The scene player's arms exist twice in the model, 4 px wide and 3 px wide. */
   private static final String[] WIDE_ARM_BONES = {"rightArmSteve", "rightLowerArmSteve", "leftArmSteve", "leftLowerArmSteve"};
   private static final String[] SLIM_ARM_BONES = {"rightArmAlex", "rightLowerArmAlex", "leftArmAlex", "leftLowerArmAlex"};
   private static final float RADIANS_PER_DEGREE = (float)(Math.PI / 180.0);

   @Override
   public Identifier getModelResource(JennyEntity jenny) {
      return jenny.getOutfitIndex() == 0 ? NUDE : DRESSED;
   }

   @Override
   public Identifier getTextureResource(JennyEntity jenny) {
      return TEXTURE;
   }

   @Override
   public Identifier getAnimationResource(JennyEntity jenny) {
      return ANIMATIONS;
   }

   @Override
   public void setCustomAnimations(JennyEntity jenny, long instanceId, AnimationState<JennyEntity> state) {
      Action action = jenny.getCurrentAction();

      // "steve" is the scene player's body; it only exists while an action involves the player.
      this.setHidden("steve", !action.hasPlayer);
      boolean slim = ScenePlayerSkins.isSlim(jenny.getInteractionPlayerUUID());
      for (String bone : WIDE_ARM_BONES) {
         this.setHidden(bone, slim);
      }
      for (String bone : SLIM_ARM_BONES) {
         this.setHidden(bone, !slim);
      }

      // The dressed model carries armour pieces for the equipment slots. The
      // equipment screen is not ported, so nothing can be worn and they stay hidden.
      for (CoreGeoBone bone : this.getAnimationProcessor().getRegisteredBones()) {
         if (bone.getName().startsWith("armor")) {
            bone.setHidden(true);
         }
      }

      if (action == Action.NULL || action == Action.ATTACK || action == Action.BOW) {
         EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
         CoreGeoBone head = this.getAnimationProcessor().getBone("head");
         if (data != null && head != null) {
            head.setRotX(data.headPitch() * RADIANS_PER_DEGREE);
            head.setRotY(data.netHeadYaw() * RADIANS_PER_DEGREE);
         }
      }
   }

   private void setHidden(String boneName, boolean hidden) {
      CoreGeoBone bone = this.getAnimationProcessor().getBone(boneName);
      if (bone != null) {
         bone.setHidden(hidden);
      }
   }
}
