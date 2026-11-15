package com.trolmastercard.sexmod.entity;

import com.trolmastercard.sexmod.SexMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Entity types. Only Jenny is ported so far; {@code PORTING.md} lists the
 * rest. Natural spawning and the world-gen structures of 1.12.2 are not
 * ported either, so she is placed with her spawn egg or {@code /summon sexmod:jenny}.
 */
public final class SexModEntities {
   public static final EntityType<JennyEntity> JENNY = Registry.register(
      Registries.ENTITY_TYPE,
      SexMod.id("jenny"),
      FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, JennyEntity::new)
         .dimensions(EntityDimensions.fixed(0.49F, 1.95F))
         .trackRangeBlocks(80)
         .trackedUpdateRate(3)
         .build()
   );

   private SexModEntities() {
   }

   public static void registerEntities() {
      FabricDefaultAttributeRegistry.register(JENNY, BaseGirlEntity.createGirlAttributes());
   }
}
