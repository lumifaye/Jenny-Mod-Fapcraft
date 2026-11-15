package com.trolmastercard.sexmod.util;

import net.minecraft.util.math.Vec3d;

public final class VectorMath {
   private VectorMath() {
   }

   /** Rotates a vector around the Y axis using the Minecraft yaw convention (degrees). */
   public static Vec3d rotateByYaw(Vec3d vector, float yaw) {
      double rad = yaw * (Math.PI / 180.0);
      double radShifted = (yaw + 90.0F) * (Math.PI / 180.0);
      return new Vec3d(
         -Math.sin(radShifted) * vector.x - Math.sin(rad) * vector.z,
         vector.y,
         Math.cos(radShifted) * vector.x + Math.cos(rad) * vector.z
      );
   }
}
