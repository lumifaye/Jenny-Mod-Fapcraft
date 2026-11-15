package com.trolmastercard.sexmod.entity;

import java.util.Optional;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Bed search and bed-side selection for bed scenes.
 * <p>
 * 1.12.2 walked a square spiral around the girl ({@code GirlBlockScan}) and
 * tested the block one step ahead of the walk. That never tests the block
 * she stands in, the four blocks next to it, or one of the diagonal ones, so
 * a bed whose two halves are both among those gave "no bed in sight...".
 * That is exactly the case when a scene starts with the player facing the
 * bed from close by, because the girl is then placed one block in front of
 * the player, onto the bed. The spiral also reached only about 10 blocks.
 * <p>
 * Here every block within {@link #HORIZONTAL_RANGE} blocks around her and
 * {@link #VERTICAL_RANGE} above and below is checked, nearest first.
 * <p>
 * A side of a bed counts as free if the block there has nothing she could
 * bump into above ankle height: air, plants, torches, carpet, snow layers.
 * 1.12.2 accepted air only.
 */
final class GirlBedPlacement {
   static final int HORIZONTAL_RANGE = 16;
   static final int VERTICAL_RANGE = 3;

   static final int[] SIDE_YAWS = {0, 180, -90, 90};
   /** Per side: [0] where the girl stands relative to the bed block, [1] the block that must be free. */
   static final Vec3d[][] SIDE_OFFSETS = {
      {new Vec3d(0.5, 0.0, -0.5), new Vec3d(0.0, 0.0, -1.0)},
      {new Vec3d(0.5, 0.0, 1.5), new Vec3d(0.0, 0.0, 1.0)},
      {new Vec3d(-0.5, 0.0, 0.5), new Vec3d(-1.0, 0.0, 0.0)},
      {new Vec3d(1.5, 0.0, 0.5), new Vec3d(1.0, 0.0, 0.0)}
   };
   /**
    * Per side, for a girl who sits on the end of the bed: [0] where she sits
    * relative to the bed block, [1] the block that must be free, [2] the block
    * that must be the other half of the bed.
    */
   private static final Vec3d[][] END_OFFSETS = {
      {new Vec3d(0.5, 0.0, -0.18), new Vec3d(0.0, 0.0, -1.0), new Vec3d(0.0, 0.0, 1.0)},
      {new Vec3d(0.5, 0.0, 1.18), new Vec3d(0.0, 0.0, 1.0), new Vec3d(0.0, 0.0, -1.0)},
      {new Vec3d(-0.18, 0.0, 0.5), new Vec3d(-1.0, 0.0, 0.0), new Vec3d(1.0, 0.0, 0.0)},
      {new Vec3d(1.18, 0.0, 0.5), new Vec3d(1.0, 0.0, 0.0), new Vec3d(-1.0, 0.0, 0.0)}
   };

   private GirlBedPlacement() {
   }

   /** A place at a bed: where the girl goes and which way she then faces. */
   static final class Spot {
      final Vec3d pos;
      final int yaw;

      Spot(Vec3d pos, int yaw) {
         this.pos = pos;
         this.yaw = yaw;
      }
   }

   /** {@code true} if the girl can stand in this block. */
   private static boolean isFree(World world, BlockPos pos) {
      VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
      return shape.isEmpty() || shape.getMax(Direction.Axis.Y) <= 0.25;
   }

   private static boolean isBed(World world, BlockPos pos) {
      return world.getBlockState(pos).isIn(BlockTags.BEDS);
   }

   /** The nearest bed block around the girl, or {@code null}. */
   @Nullable
   static BlockPos findNearestBed(BaseGirlEntity girl) {
      World world = girl.getWorld();
      return BlockPos.findClosest(girl.getBlockPos(), HORIZONTAL_RANGE, VERTICAL_RANGE, pos -> isBed(world, pos)).orElse(null);
   }

   /** The nearest bed block that has a free side to stand at ({@link #findFreeSide}), or {@code null}. */
   @Nullable
   static BlockPos findNearestBedWithFreeSide(BaseGirlEntity girl) {
      World world = girl.getWorld();
      Optional<BlockPos> bed = BlockPos.findClosest(
         girl.getBlockPos(), HORIZONTAL_RANGE, VERTICAL_RANGE, pos -> isBed(world, pos) && findFreeSide(girl, Vec3d.of(pos)) != -1
      );
      return bed.orElse(null);
   }

   /** Index of the free bed side closest to the girl, or -1 if every side is blocked. */
   static int findFreeSide(BaseGirlEntity girl, Vec3d bedVec) {
      int bestIndex = -1;
      double bestDist = Double.MAX_VALUE;
      for (int i = 0; i < SIDE_OFFSETS.length; i++) {
         Vec3d openVec = bedVec.add(SIDE_OFFSETS[i][1]);
         if (!isFree(girl.getWorld(), BlockPos.ofFloored(openVec))) {
            continue;
         }
         Vec3d sideVec = bedVec.add(SIDE_OFFSETS[i][0]);
         double dist = girl.getBlockPos().getSquaredDistance(sideVec.x, sideVec.y, sideVec.z);
         if (bestIndex == -1 || dist < bestDist) {
            bestIndex = i;
            bestDist = dist;
         }
      }
      return bestIndex;
   }

   /**
    * The nearest place to sit on the end of a bed: the bed's other half behind
    * her, a free block in front of her. {@code null} if no bed around the girl
    * has such an end.
    */
   @Nullable
   static Spot findBedEnd(BaseGirlEntity girl) {
      World world = girl.getWorld();
      Optional<BlockPos> bed = BlockPos.findClosest(
         girl.getBlockPos(), HORIZONTAL_RANGE, VERTICAL_RANGE, pos -> isBed(world, pos) && bedEndIndex(girl, pos) != -1
      );
      if (bed.isEmpty()) {
         return null;
      }
      int index = bedEndIndex(girl, bed.get());
      return new Spot(Vec3d.of(bed.get()).add(END_OFFSETS[index][0]), SIDE_YAWS[index]);
   }

   private static int bedEndIndex(BaseGirlEntity girl, BlockPos bedPos) {
      World world = girl.getWorld();
      Vec3d bedVec = Vec3d.of(bedPos);
      int bestIndex = -1;
      double bestDist = Double.MAX_VALUE;
      for (int i = 0; i < END_OFFSETS.length; i++) {
         if (!isFree(world, BlockPos.ofFloored(bedVec.add(END_OFFSETS[i][1])))
            || !isBed(world, BlockPos.ofFloored(bedVec.add(END_OFFSETS[i][2])))) {
            continue;
         }
         Vec3d sitVec = bedVec.add(END_OFFSETS[i][0]);
         double dist = girl.getBlockPos().getSquaredDistance(sitVec.x, sitVec.y, sitVec.z);
         if (bestIndex == -1 || dist < bestDist) {
            bestIndex = i;
            bestDist = dist;
         }
      }
      return bestIndex;
   }
}
