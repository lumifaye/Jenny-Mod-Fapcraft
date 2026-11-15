package com.trolmastercard.sexmod.client.model;

import com.trolmastercard.sexmod.SexMod;
import com.trolmastercard.sexmod.client.renderer.ScenePlayerSkins;
import com.trolmastercard.sexmod.entity.Action;
import com.trolmastercard.sexmod.entity.BaseGirlEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * Model shared by the girls: geometry by outfit, plus the per-frame bone
 * state that is not part of any animation.
 * <p>
 * Bones belong to the baked model, which every entity of a character shares,
 * and GeckoLib does not reset a bone's hidden flag between entities. So every
 * flag this class touches is written for every entity on every frame, never
 * only when it should change.
 */
public class GirlModel<T extends BaseGirlEntity> extends GeoModel<T> {
   /** The scene player's arms exist twice in the models, 4 px wide and 3 px wide. */
   private static final String[] WIDE_ARM_BONES = {"rightArmSteve", "rightLowerArmSteve", "leftArmSteve", "leftLowerArmSteve"};
   private static final String[] SLIM_ARM_BONES = {"rightArmAlex", "rightLowerArmAlex", "leftArmAlex", "leftLowerArmAlex"};
   protected static final float RADIANS_PER_DEGREE = (float)(Math.PI / 180.0);

   private final Identifier nudeGeometry;
   private final Identifier dressedGeometry;
   private final Identifier texture;
   private final Identifier animations;

   /** All paths are below {@code assets/sexmod}; {@code folder} is the character's asset folder. */
   public GirlModel(String folder, String nudeGeometry, String dressedGeometry, String texture, String animations) {
      this.nudeGeometry = SexMod.id("geo/" + folder + "/" + nudeGeometry + ".geo.json");
      this.dressedGeometry = SexMod.id("geo/" + folder + "/" + dressedGeometry + ".geo.json");
      this.texture = SexMod.id("textures/entity/" + folder + "/" + texture + ".png");
      this.animations = SexMod.id("animations/" + folder + "/" + animations + ".animation.json");
   }

   @Override
   public Identifier getModelResource(T girl) {
      return girl.getOutfitIndex() == 0 ? this.nudeGeometry : this.dressedGeometry;
   }

   @Override
   public Identifier getTextureResource(T girl) {
      return this.texture;
   }

   @Override
   public Identifier getAnimationResource(T girl) {
      return this.animations;
   }

   @Override
   public void setCustomAnimations(T girl, long instanceId, AnimationState<T> state) {
      Action action = girl.getCurrentAction();

      // "steve" is the scene player's body; it only exists while an action involves the player.
      this.setHidden("steve", !action.hasPlayer);
      boolean slim = ScenePlayerSkins.isSlim(girl.getInteractionPlayerUUID());
      for (String bone : WIDE_ARM_BONES) {
         this.setHidden(bone, slim);
      }
      for (String bone : SLIM_ARM_BONES) {
         this.setHidden(bone, !slim);
      }
      // The scene player's own head would fill the screen in first person.
      this.setHidden("Head2", girl.isControlledByLocalPlayer() && MinecraftClient.getInstance().options.getPerspective().isFirstPerson());

      // The dressed models carry armour pieces for the equipment slots. The
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

   protected void setHidden(String boneName, boolean hidden) {
      CoreGeoBone bone = this.getAnimationProcessor().getBone(boneName);
      if (bone != null) {
         bone.setHidden(hidden);
      }
   }
}
