package com.trolmastercard.sexmod.item;

import com.trolmastercard.sexmod.SexMod;
import com.trolmastercard.sexmod.entity.SexModEntities;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Items. So far only the spawn eggs of the ported girls; the 1.12.2 items (staffs, tribe eggs,
 * the reskin tools) belong to characters and systems that are not ported yet.
 */
public final class ModItems {
   public static final Item JENNY_SPAWN_EGG = new SpawnEggItem(SexModEntities.JENNY, 0x3B5BA5, 0xF2C9A0, new Item.Settings());
   public static final Item ELLIE_SPAWN_EGG = new SpawnEggItem(SexModEntities.ELLIE, 0x161616, 0x980000, new Item.Settings());

   private ModItems() {
   }

   public static void registerAll() {
      Registry.register(Registries.ITEM, SexMod.id("jenny_spawn_egg"), JENNY_SPAWN_EGG);
      Registry.register(Registries.ITEM, SexMod.id("ellie_spawn_egg"), ELLIE_SPAWN_EGG);
      ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
         entries.add(JENNY_SPAWN_EGG);
         entries.add(ELLIE_SPAWN_EGG);
      });
   }
}
