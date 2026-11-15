package com.trolmastercard.sexmod.client.renderer;

import com.trolmastercard.sexmod.entity.AbstractGirlNpcEntity;
import com.trolmastercard.sexmod.entity.Action;
import com.trolmastercard.sexmod.entity.BaseGirlEntity;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.DynamicGeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.util.RenderUtils;

/**
 * Renderer shared by all girls. Three things go beyond a plain GeckoLib entity:
 * <ul>
 * <li>the {@code steve} bones (the scene player's body) are drawn with that
 * player's skin instead of the girl's texture;</li>
 * <li>a companion's weapon or bow is drawn at the empty {@code weapon} bone;</li>
 * <li>flat cubes are lit the way the 1.12.2 renderer lit them, see
 * {@link #renderCube}.</li>
 * </ul>
 * The item placement uses the angles of the 1.12.2 renderers. It has not been
 * checked in a running game, so it is the first place to look if an item
 * floats or points the wrong way.
 */
public class GirlRenderer<T extends BaseGirlEntity> extends DynamicGeoEntityRenderer<T> {
   private static final IntIntPair UV_SPACE = IntIntPair.of(64, 64);

   /** X rotation of the held item, as in 1.12.2. */
   private final float slashSwordRotation;
   private final float stabSwordRotation;
   private final float bowRotation;
   private final Vec3d stabSwordOffset;

   /** The matrices of the last flat cube, kept because all cubes of one model share them. */
   private final Matrix4f flatCubeModelMatrix = new Matrix4f().zero();
   private final Matrix3f modelToView = new Matrix3f();
   private final Matrix3f viewToModel = new Matrix3f();
   private boolean viewToModelValid;

   public GirlRenderer(
      EntityRendererFactory.Context context, GeoModel<T> model, float slashSwordRotation, float stabSwordRotation, float bowRotation, Vec3d stabSwordOffset
   ) {
      super(context, model);
      this.slashSwordRotation = slashSwordRotation;
      this.stabSwordRotation = stabSwordRotation;
      this.bowRotation = bowRotation;
      this.stabSwordOffset = stabSwordOffset;
      this.shadowRadius = 0.3F;
      this.addRenderLayer(new ItemLayer<>(this));
   }

   @Nullable
   @Override
   protected Identifier getTextureOverrideForBone(GeoBone bone, T girl, float partialTick) {
      return isScenePlayerBone(bone) ? ScenePlayerSkins.textureFor(girl.getInteractionPlayerUUID()) : null;
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
    * texture. Here every bone's UVs are fractions of a 64x64 layout (a girl's
    * texture is that layout at a higher resolution, a skin is that layout at
    * 1x), so no rescaling must happen: reporting one size for every texture
    * makes the ratio 1.
    */
   @Override
   protected IntIntPair computeTextureSize(Identifier texture) {
      return UV_SPACE;
   }

   /**
    * Same as GeckoLib's own method except for the normals of flat cubes.
    * <p>
    * A flat cube (hair strands, lashes, cloth: about four in ten cubes of these
    * models) is drawn from both sides, so its normal can point away from the
    * light although the visible side faces it. GeckoLib therefore mirrors
    * negative normal components of flat cubes. GeckoLib 4 does that after the
    * camera rotation is applied, so which components are negative depends on
    * where the camera is, and the shading of those parts changes as the view
    * turns. The 1.12.2 renderer mirrored them in the model's own space, where
    * the result is the same from every angle. This does it the 1.12.2 way.
    * <p>
    * Normals are also brought back to unit length, which 1.12.2 got from
    * {@code GL_RESCALE_NORMAL} and the current shaders do not do; animated bone
    * scaling would otherwise brighten or darken the scaled part.
    */
   @Override
   public void renderCube(
      MatrixStack matrices, GeoCube cube, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha
   ) {
      RenderUtils.translateToPivotPoint(matrices, cube);
      RenderUtils.rotateMatrixAroundCube(matrices, cube);
      RenderUtils.translateAwayFromPivotPoint(matrices, cube);

      Matrix3f normalMatrix = matrices.peek().getNormalMatrix();
      Matrix4f positionMatrix = new Matrix4f(matrices.peek().getPositionMatrix());
      boolean flatX = cube.size().x == 0.0;
      boolean flatY = cube.size().y == 0.0;
      boolean flatZ = cube.size().z == 0.0;
      boolean flat = flatX || flatY || flatZ;
      if (flat) {
         if (!this.flatCubeModelMatrix.equals(this.modelRenderTranslations)) {
            this.flatCubeModelMatrix.set(this.modelRenderTranslations);
            this.modelRenderTranslations.normal(this.modelToView);
            this.modelToView.invert(this.viewToModel);
            this.viewToModelValid = Float.isFinite(this.viewToModel.m00() + this.viewToModel.m11() + this.viewToModel.m22());
         }
         flat = this.viewToModelValid;
      }

      for (GeoQuad quad : cube.quads()) {
         if (quad == null) {
            continue;
         }
         Vector3f normal = normalMatrix.transform(new Vector3f(quad.normal()));
         if (flat) {
            this.viewToModel.transform(normal);
            if (normal.x < 0.0F && (flatY || flatZ)) {
               normal.x = -normal.x;
            }
            if (normal.y < 0.0F && (flatX || flatZ)) {
               normal.y = -normal.y;
            }
            if (normal.z < 0.0F && (flatX || flatY)) {
               normal.z = -normal.z;
            }
            this.modelToView.transform(normal);
         }
         float length = normal.length();
         if (length > 1.0E-5F && Float.isFinite(length)) {
            normal.div(length);
         } else {
            normal.set(0.0F, 1.0F, 0.0F);
         }
         this.createVerticesOfQuad(quad, positionMatrix, normal, buffer, packedLight, packedOverlay, red, green, blue, alpha);
      }
   }

   // ------------------------------------------------------------------ items

   /** The item drawn at {@code bone}, or {@code null}. The base class knows the companion's {@code weapon} bone. */
   @Nullable
   protected ItemStack getItemForBone(GeoBone bone, T girl) {
      if (!"weapon".equals(bone.getName()) || !(girl instanceof AbstractGirlNpcEntity)) {
         return null;
      }
      int attackMode = girl.getDataTracker().get(AbstractGirlNpcEntity.ATTACK_MODE);
      if (attackMode == 1) {
         return girl.getDataTracker().get(AbstractGirlNpcEntity.WEAPON);
      }
      return attackMode == 2 ? girl.getDataTracker().get(AbstractGirlNpcEntity.BOW) : null;
   }

   /** Draws {@code stack} at {@code bone}; {@code layer.draw} does the actual drawing at the current matrix. */
   protected void renderItemAtBone(
      ItemLayer<T> layer, MatrixStack matrices, GeoBone bone, ItemStack stack, T girl, VertexConsumerProvider buffers, float partialTick, int light, int overlay
   ) {
      float rotation;
      if (stack.getItem() instanceof BowItem) {
         rotation = this.bowRotation;
      } else if (girl.getCurrentAction() == Action.ATTACK && girl instanceof AbstractGirlNpcEntity && ((AbstractGirlNpcEntity)girl).nextAttack == 0) {
         matrices.translate(this.stabSwordOffset.x, this.stabSwordOffset.y, this.stabSwordOffset.z);
         rotation = this.stabSwordRotation;
      } else {
         rotation = this.slashSwordRotation;
      }
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotation));
      layer.draw(matrices, bone, stack, girl, buffers, partialTick, light, overlay);
   }

   /** GeckoLib's item layer, with the decisions handed to the renderer so subclasses can add their own bones. */
   protected static final class ItemLayer<T extends BaseGirlEntity> extends BlockAndItemGeoLayer<T> {
      private final GirlRenderer<T> renderer;

      ItemLayer(GirlRenderer<T> renderer) {
         super(renderer);
         this.renderer = renderer;
      }

      @Nullable
      @Override
      protected ItemStack getStackForBone(GeoBone bone, T girl) {
         ItemStack stack = this.renderer.getItemForBone(bone, girl);
         return stack == null || stack.isEmpty() ? null : stack;
      }

      @Override
      protected ModelTransformationMode getTransformTypeForStack(GeoBone bone, ItemStack stack, T girl) {
         return ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
      }

      @Override
      protected void renderStackForBone(
         MatrixStack matrices, GeoBone bone, ItemStack stack, T girl, VertexConsumerProvider buffers, float partialTick, int light, int overlay
      ) {
         this.renderer.renderItemAtBone(this, matrices, bone, stack, girl, buffers, partialTick, light, overlay);
      }

      public void draw(MatrixStack matrices, GeoBone bone, ItemStack stack, T girl, VertexConsumerProvider buffers, float partialTick, int light, int overlay) {
         super.renderStackForBone(matrices, bone, stack, girl, buffers, partialTick, light, overlay);
      }
   }
}
