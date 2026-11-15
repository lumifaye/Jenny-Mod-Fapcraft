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
 * Entity types. Jenny and Ellie are ported so far; {@code PORTING.md} lists
 * the rest. Natural spawning and the world-gen structures of 1.12.2 are not
 * ported either, so they are placed with their spawn eggs or
 * {@code /summon sexmod:jenny}, {@code /summon sexmod:ellie}.
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

   /** The 1.12.2 hitbox. She is drawn taller than it and ducks under two-block ceilings. */
   public static final EntityType<EllieEntity> ELLIE = Registry.register(
      Registries.ENTITY_TYPE,
      SexMod.id("ellie"),
      FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, EllieEntity::new)
         .dimensions(EntityDimensions.fixed(0.6F, 1.8F))
         .trackRangeBlocks(80)
         .trackedUpdateRate(3)
         .build()
   );

   private SexModEntities() {
   }

   public static void registerEntities() {
      FabricDefaultAttributeRegistry.register(JENNY, BaseGirlEntity.createGirlAttributes());
      FabricDefaultAttributeRegistry.register(ELLIE, BaseGirlEntity.createGirlAttributes());
   }
}
