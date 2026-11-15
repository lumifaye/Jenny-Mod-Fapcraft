package com.trolmastercard.sexmod.potion;

import com.trolmastercard.sexmod.SexMod;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * The horny effect and its potion (mundane potion + poppy).
 * <ul>
 * <li>On a girl: her menu actions are free while it lasts.</li>
 * <li>On an adult animal: it enters love mode, and the effect is used up.</li>
 * </ul>
 * In 1.12.2 the effect id was {@code "sexmod:horny potion"}; ids cannot hold
 * spaces any more, so both the effect and the potion are
 * {@code sexmod:horny_potion}. The 1.12.2 villager behaviour is not ported.
 */
public final class HornyPotion extends StatusEffect {
   public static final StatusEffect HORNY = new HornyPotion();
   public static final Potion POTION = new Potion(
      "horny_potion", new StatusEffectInstance(HORNY, 3600), new StatusEffectInstance(StatusEffects.NAUSEA, 200, 1)
   );

   private HornyPotion() {
      super(StatusEffectCategory.NEUTRAL, 0xFF6388);
   }

   public static void register() {
      Registry.register(Registries.STATUS_EFFECT, SexMod.id("horny_potion"), HORNY);
      Registry.register(Registries.POTION, SexMod.id("horny_potion"), POTION);
      FabricBrewingRecipeRegistry.registerPotionRecipe(Potions.MUNDANE, Ingredient.ofItems(Items.POPPY), POTION);
   }

   @Override
   public boolean canApplyUpdateEffect(int duration, int amplifier) {
      return true;
   }

   @Override
   public void applyUpdateEffect(LivingEntity entity, int amplifier) {
      if (entity.getWorld().isClient || !(entity instanceof AnimalEntity)) {
         return;
      }
      AnimalEntity animal = (AnimalEntity)entity;
      if (animal.getBreedingAge() == 0) {
         animal.lovePlayer(animal.getWorld().getClosestPlayer(animal, 30.0));
      }
      animal.removeStatusEffect(this);
   }
}
