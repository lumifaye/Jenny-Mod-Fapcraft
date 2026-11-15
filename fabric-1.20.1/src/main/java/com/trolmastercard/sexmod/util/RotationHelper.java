package com.trolmastercard.sexmod.util;

import net.minecraft.util.math.Vec3d;

public final class RotationHelper {
   private RotationHelper() {
   }

   /**
    * STEP lerp: one step of the remaining distance ({@code a + (b-a)/steps}).
    * The 40-tick scene-entry walk calls this with {@code steps = 40 - counter}.
    * Do not replace it with {@link #lerpVec3dDouble}: multiplying instead of
    * dividing flings the girl 40x the distance on the first tick.
    */
   public static Vec3d lerpVec3d(Vec3d from, Vec3d to, int steps) {
      if (steps == 0) {
         return to;
      }
      Vec3d delta = to.subtract(from);
      return from.add(delta.x / steps, delta.y / steps, delta.z / steps);
   }

   /** PROGRESS lerp: interpolates by a factor in 0..1. */
   public static Vec3d lerpVec3dDouble(Vec3d from, Vec3d to, double t) {
      Vec3d delta = to.subtract(from);
      return from.add(delta.x * t, delta.y * t, delta.z * t);
   }

   public static double lerpDouble(double from, double to, double t) {
      return from + (to - from) * t;
   }

   public static float lerp(float from, float to, float t) {
      return from + (to - from) * t;
   }
}
