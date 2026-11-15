package com.trolmastercard.sexmod.entity;

import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Bed search and bed-side selection for bed scenes. */
final class GirlBedPlacement {
   static final int[] SIDE_YAWS = {0, 180, -90, 90};
   /** Per side: [0] where the girl stands relative to the bed block, [1] the block that must be free. */
   static final Vec3d[][] SIDE_OFFSETS = {
      {new Vec3d(0.5, 0.0, -0.5), new Vec3d(0.0, 0.0, -1.0)},
      {new Vec3d(0.5, 0.0, 1.5), new Vec3d(0.0, 0.0, 1.0)},
      {new Vec3d(-0.5, 0.0, 0.5), new Vec3d(-1.0, 0.0, 0.0)},
      {new Vec3d(1.5, 0.0, 0.5), new Vec3d(1.0, 0.0, 0.0)}
   };

   private GirlBedPlacement() {
   }

   /** Index of the free bed side closest to the girl, or -1 if every side is blocked. */
   static int findFreeSide(BaseGirlEntity girl, Vec3d bedVec) {
      int bestIndex = -1;
      double bestDist = Double.MAX_VALUE;
      for (int i = 0; i < SIDE_OFFSETS.length; i++) {
         Vec3d openVec = bedVec.add(SIDE_OFFSETS[i][1]);
         if (!girl.getWorld().getBlockState(BlockPos.ofFloored(openVec)).isAir()) {
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
    * Square-spiral search for the nearest bed around {@code origin}, the same
    * walk as the 1.12.2 {@code GirlBlockScan}: rings up to {@code maxRadius},
    * checking {@code heightRange} blocks above and below.
    */
   static BlockPos findNearestBed(World world, BlockPos origin, int maxRadius, int heightRange) {
      int step = 1;
      int dir = -1;
      BlockPos pos = origin;
      while (step < maxRadius) {
         for (int i = 0; i < 2; i++) {
            dir *= -1;
            for (int j = 0; j < step; j++) {
               pos = pos.add(0, 0, dir);
               for (int h = -heightRange; h <= heightRange; h++) {
                  BlockPos candidate = pos.add(0, h, dir);
                  if (world.getBlockState(candidate).isIn(BlockTags.BEDS)) {
                     return candidate;
                  }
               }
            }
            for (int k = 0; k < step; k++) {
               pos = pos.add(dir, 0, 0);
               for (int h = -heightRange; h <= heightRange; h++) {
                  BlockPos candidate = pos.add(dir, h, 0);
                  if (world.getBlockState(candidate).isIn(BlockTags.BEDS)) {
                     return candidate;
                  }
               }
            }
            step++;
         }
      }
      return null;
   }
}
