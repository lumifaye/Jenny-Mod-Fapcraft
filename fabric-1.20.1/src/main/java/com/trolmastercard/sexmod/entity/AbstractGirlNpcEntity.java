package com.trolmastercard.sexmod.entity;

import com.trolmastercard.sexmod.entity.ai.GirlFollowGoal;
import com.trolmastercard.sexmod.networking.ModPackets;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A girl that can become a companion: she has an equipment inventory (slot 0
 * weapon, 1 bow, 2-5 helmet/chest/legs/boots, 6 spare), follows and defends her
 * master ({@link GirlFollowGoal}), and is downed instead of killed while she
 * has one.
 */
public abstract class AbstractGirlNpcEntity extends BaseGirlEntity {
   public static final TrackedData<Integer> ATTACK_MODE = DataTracker.registerData(AbstractGirlNpcEntity.class, TrackedDataHandlerRegistry.INTEGER);
   public static final TrackedData<ItemStack> WEAPON = DataTracker.registerData(AbstractGirlNpcEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
   public static final TrackedData<ItemStack> BOW = DataTracker.registerData(AbstractGirlNpcEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
   public static final TrackedData<ItemStack> HELMET_SLOT = DataTracker.registerData(AbstractGirlNpcEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
   public static final TrackedData<ItemStack> CHEST_SLOT = DataTracker.registerData(AbstractGirlNpcEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
   public static final TrackedData<ItemStack> LEGS_SLOT = DataTracker.registerData(AbstractGirlNpcEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
   public static final TrackedData<ItemStack> BOOTS_SLOT = DataTracker.registerData(AbstractGirlNpcEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);

   private static final List<TrackedData<ItemStack>> SLOT_KEYS = List.of(WEAPON, BOW, HELMET_SLOT, CHEST_SLOT, LEGS_SLOT, BOOTS_SLOT);

   /** The attack animations are 7 to 11 ticks long. */
   private static final int ATTACK_TIMEOUT_TICKS = 16;
   /** The pearl animation is 92 ticks long. */
   private static final int THROW_PEARL_TIMEOUT_TICKS = 110;

   /** Index of the next attack animation (0..2). */
   public int nextAttack = 1;
   public boolean downed;
   public final SimpleInventory inventory = new SimpleInventory(7);

   protected AbstractGirlNpcEntity(EntityType<? extends AbstractGirlNpcEntity> type, World world) {
      super(type, world);
      this.inventory.setStack(0, new ItemStack(Items.IRON_SWORD));
      this.inventory.setStack(1, new ItemStack(Items.BOW));
   }

   @Override
   protected void initDataTracker() {
      super.initDataTracker();
      this.dataTracker.startTracking(ATTACK_MODE, 0);
      for (TrackedData<ItemStack> key : SLOT_KEYS) {
         this.dataTracker.startTracking(key, ItemStack.EMPTY);
      }
   }

   @Override
   protected void initGoals() {
      super.initGoals();
      this.goalSelector.add(1, new GirlFollowGoal(this));
   }

   /** Client side: called when the player sets a new home through the menu. */
   public void onArriveHome() {
   }

   @Override
   protected void mobTick() {
      super.mobTick();
      if (this.age % 80 == 0 && this.getHealth() < this.getMaxHealth()) {
         if (!this.hasMaster()) {
            this.heal(1.0F);
         } else {
            List<HostileEntity> mobs = this.getWorld().getNonSpectatingEntities(HostileEntity.class, this.getBoundingBox().expand(7.0, 1.0, 7.0));
            int healAmount = mobs.isEmpty() ? 4 : 1;
            this.heal(healAmount);
            ((ServerWorld)this.getWorld()).spawnParticles(ParticleTypes.HEART, this.getX(), this.getY() + 1.5, this.getZ(), healAmount, 0.5, 0.5, 0.5, 0.0);
         }
      }
      if (this.downed && !this.hasMaster()) {
         this.downed = false;
      }
      this.tickActionTimeouts();
      // ItemStack has no equals(), so an unconditional set would resync every slot every tick.
      for (int slot = 0; slot < SLOT_KEYS.size(); slot++) {
         ItemStack stack = this.inventory.getStack(slot);
         if (!ItemStack.areEqual(this.dataTracker.get(SLOT_KEYS.get(slot)), stack)) {
            this.dataTracker.set(SLOT_KEYS.get(slot), stack.copy());
         }
      }
   }

   /**
    * These two actions normally end when a client reports the end of their
    * animation. If no client does (nobody is close enough to render her), the
    * server ends them itself shortly after the animation would have finished.
    */
   private void tickActionTimeouts() {
      Action action = this.getCurrentAction();
      if (action == Action.ATTACK && this.getActionTicks() > ATTACK_TIMEOUT_TICKS) {
         this.setCurrentAction(Action.NULL);
      } else if (action == Action.THROW_PEARL && this.getActionTicks() > THROW_PEARL_TIMEOUT_TICKS) {
         ModPackets.companionGoHome(this);
      }
   }

   @Override
   public void doAction(String actionName, UUID playerUuid) {
      if ("action.names.followme".equals(actionName)) {
         this.changeDataParameterFromClient("master", playerUuid.toString());
      } else if ("action.names.stopfollowme".equals(actionName)) {
         this.goHome();
      } else if ("action.names.gohome".equals(actionName)) {
         this.goHome();
         ModPackets.sendCompanionHome(this.getGirlId());
      } else if ("action.names.setnewhome".equals(actionName)) {
         this.onArriveHome();
         ModPackets.sendSetNewHome(this.getGirlId(), Vec3d.of(this.getBlockPos()));
      }
   }

   /** With a master she is downed at 1 HP instead of dying, and takes no damage while down. */
   @Override
   public boolean damage(DamageSource source, float amount) {
      if (!this.getWorld().isClient && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         if (this.downed) {
            return false;
         }
         if (this.hasMaster() && this.getHealth() - amount <= 0.0F) {
            this.downed = true;
            this.setCurrentAction(Action.DOWNED);
            this.getNavigation().stop();
            amount = this.getHealth() - 1.0F;
            if (amount <= 0.0F) {
               return false;
            }
         }
      }
      return super.damage(source, amount);
   }

   /** A full heal gets a downed girl back up. */
   @Override
   public void heal(float amount) {
      super.heal(amount);
      if (this.downed && this.getHealth() >= this.getMaxHealth()) {
         this.downed = false;
         this.setCurrentAction(Action.NULL);
      }
   }

   @Override
   protected void dropInventory() {
      super.dropInventory();
      for (int slot = 0; slot < 6; slot++) {
         ItemStack stack = this.inventory.removeStack(slot);
         if (!stack.isEmpty()) {
            this.dropStack(stack);
         }
      }
   }

   @Override
   public void writeCustomDataToNbt(NbtCompound nbt) {
      super.writeCustomDataToNbt(nbt);
      DefaultedList<ItemStack> stacks = DefaultedList.ofSize(this.inventory.size(), ItemStack.EMPTY);
      for (int slot = 0; slot < this.inventory.size(); slot++) {
         stacks.set(slot, this.inventory.getStack(slot));
      }
      NbtCompound inventoryNbt = new NbtCompound();
      Inventories.writeNbt(inventoryNbt, stacks);
      nbt.put("inventory", inventoryNbt);
      nbt.putBoolean("downed", this.downed);
   }

   @Override
   public void readCustomDataFromNbt(NbtCompound nbt) {
      super.readCustomDataFromNbt(nbt);
      if (nbt.contains("inventory")) {
         DefaultedList<ItemStack> stacks = DefaultedList.ofSize(this.inventory.size(), ItemStack.EMPTY);
         Inventories.readNbt(nbt.getCompound("inventory"), stacks);
         for (int slot = 0; slot < this.inventory.size(); slot++) {
            this.inventory.setStack(slot, stacks.get(slot));
         }
      }
      this.downed = nbt.getBoolean("downed");
   }
}
