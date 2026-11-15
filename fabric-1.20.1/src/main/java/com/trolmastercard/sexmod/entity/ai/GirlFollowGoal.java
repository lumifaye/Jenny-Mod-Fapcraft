package com.trolmastercard.sexmod.entity.ai;

import com.trolmastercard.sexmod.entity.AbstractGirlNpcEntity;
import com.trolmastercard.sexmod.entity.Action;
import com.trolmastercard.sexmod.entity.BaseGirlEntity;
import java.util.EnumSet;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Companion behaviour while a girl has a master: follow (teleporting when she
 * falls too far behind), idle around the master, and defend: melee with the
 * weapon in slot 0, or the bow in slot 1 at range.
 * <p>
 * Ported from the 1.12.2 {@code GirlFollowAiBase} + {@code GirlFollowGoal}.
 * Not ported yet: riding along on the master's mount and sweeping-edge
 * area damage.
 */
public class GirlFollowGoal extends Goal {
   private enum State {
      ATTACK,
      FOLLOW,
      IDLE,
      DOWNED
   }

   private final AbstractGirlNpcEntity girl;
   private PlayerEntity master;
   private LivingEntity target;
   private State state = State.IDLE;
   private int wanderTimer;
   private int attackCooldown;
   private int attackTimer;
   private int bowTimer;
   private int checkTimer;

   public GirlFollowGoal(AbstractGirlNpcEntity girl) {
      this.girl = girl;
      this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
   }

   @Override
   public boolean canStart() {
      return this.girl.getMasterPlayer() != null;
   }

   @Override
   public boolean shouldContinue() {
      return this.girl.getMasterPlayer() != null;
   }

   @Override
   public void start() {
      this.master = this.girl.getMasterPlayer();
   }

   @Override
   public void stop() {
      this.girl.getNavigation().stop();
      this.state = State.IDLE;
      this.target = null;
      this.master = null;
      if (Action.isAny(this.girl.getCurrentAction(), Action.ATTACK, Action.BOW, Action.DOWNED)) {
         this.girl.setCurrentAction(Action.NULL);
      }
      this.girl.getDataTracker().set(AbstractGirlNpcEntity.ATTACK_MODE, 0);
      this.girl.setWalkSpeed(BaseGirlEntity.BaseGirlEntityState.WALK);
   }

   @Override
   public boolean shouldRunEveryTick() {
      return true;
   }

   @Override
   public void tick() {
      this.master = this.girl.getMasterPlayer();
      if (this.master == null) {
         return;
      }
      this.state = this.pickState();
      switch (this.state) {
         case ATTACK:
            this.tickAttack();
            break;
         case FOLLOW:
            this.setAttackMode(0);
            if (this.girl.squaredDistanceTo(this.master) > 30.0 * 30.0) {
               this.teleportToMaster();
            } else if (!this.girl.downed) {
               this.girl.getNavigation().startMovingTo(this.master, this.followSpeed());
            }
            this.wanderTimer = 300;
            break;
         case IDLE:
            this.setAttackMode(0);
            if (!this.girl.downed && ++this.wanderTimer > 200 + this.girl.getRandom().nextInt(100)) {
               this.wanderTimer = 0;
               Vec3d pos = this.master.getPos();
               this.girl.getNavigation().startMovingTo(
                  pos.x + 1.0 + this.girl.getRandom().nextFloat() * 3.0F, pos.y, pos.z + 1.0 + this.girl.getRandom().nextFloat() * 3.0F, 0.5
               );
            }
            this.followSpeed();
            break;
         case DOWNED:
         default:
            this.girl.getNavigation().stop();
      }
   }

   private State pickState() {
      this.attackCooldown--;
      if (this.girl.downed || this.girl.getInteractionPlayerUUID() != null) {
         return State.DOWNED;
      }
      if (this.isValidTarget(this.target)) {
         return State.ATTACK;
      }
      LivingEntity candidate = this.girl.getAttacker();
      if (!this.isValidTarget(candidate)) {
         candidate = this.master.getAttacking();
         if (candidate != null && this.master.age - this.master.getLastAttackTime() >= 140) {
            candidate = null;
         }
      }
      if (!this.isValidTarget(candidate) && this.state != State.FOLLOW) {
         candidate = this.master.getAttacker();
         if (!this.isValidTarget(candidate)) {
            candidate = this.nearestHostile();
         }
      }
      if (this.isValidTarget(candidate)) {
         this.target = candidate;
         return State.ATTACK;
      }
      boolean tooFar = this.girl.distanceTo(this.master) > 5.0F;
      if (!tooFar && this.state == State.FOLLOW) {
         // Keep following for a few seconds after catching up, so she does not stop-start.
         if (++this.checkTimer > 60) {
            this.checkTimer = 0;
         } else {
            tooFar = true;
         }
      }
      if (tooFar && this.state == State.ATTACK) {
         this.attackCooldown = 60;
      }
      return tooFar ? State.FOLLOW : State.IDLE;
   }

   private boolean isValidTarget(LivingEntity candidate) {
      return candidate != null
         && !(candidate instanceof BaseGirlEntity)
         && this.attackCooldown <= 0
         && candidate != this.girl
         && candidate != this.master
         && candidate.isAlive()
         && candidate.getWorld() == this.girl.getWorld()
         && this.girl.distanceTo(this.master) < 15.0F
         && this.girl.distanceTo(candidate) < 20.0F;
   }

   private LivingEntity nearestHostile() {
      HostileEntity nearest = null;
      double nearestDistSq = Double.MAX_VALUE;
      for (HostileEntity mob : this.girl.getWorld().getNonSpectatingEntities(HostileEntity.class, this.girl.getBoundingBox().expand(5.0, 2.0, 5.0))) {
         // Creepers are left alone: hitting them only gets her blown up.
         if (mob instanceof CreeperEntity) {
            continue;
         }
         double distSq = mob.squaredDistanceTo(this.girl);
         if (distSq < nearestDistSq && this.isValidTarget(mob)) {
            nearestDistSq = distSq;
            nearest = mob;
         }
      }
      return nearest;
   }

   private void tickAttack() {
      this.girl.getLookControl().lookAt(this.target, 30.0F, 30.0F);
      double dist = this.girl.distanceTo(this.target);
      this.attackTimer--;
      if (dist < 1.9 && this.attackTimer <= 0) {
         this.girl.getNavigation().stop();
         this.meleeAttack();
         return;
      }
      boolean hasBow = this.girl.inventory.getStack(1).getItem() instanceof BowItem;
      if (hasBow && dist > 4.0 && this.girl.getVisibilityCache().canSee(this.target)) {
         this.girl.getNavigation().stop();
         this.setAttackMode(2);
         if (++this.bowTimer > 0) {
            this.girl.setCurrentAction(Action.BOW);
            if (this.bowTimer >= 32) {
               this.bowTimer = -20;
               this.shootArrow();
               this.girl.setCurrentAction(Action.NULL);
            }
         }
         return;
      }
      if (this.girl.getCurrentAction() == Action.BOW) {
         this.girl.setCurrentAction(Action.NULL);
      }
      this.setAttackMode(1);
      boolean close = dist < 2.0;
      this.girl.getNavigation().startMovingTo(this.target, close ? 0.5 : 0.7);
      this.girl.setWalkSpeed(close ? BaseGirlEntity.BaseGirlEntityState.WALK : BaseGirlEntity.BaseGirlEntityState.RUN);
   }

   private void meleeAttack() {
      this.girl.setCurrentAction(Action.ATTACK);
      this.setAttackMode(1);
      ItemStack weapon = this.girl.inventory.getStack(0);
      float damage = 1.0F;
      float speed = 0.0F;
      for (EntityAttributeModifier modifier : weapon.getAttributeModifiers(EquipmentSlot.MAINHAND).get(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
         damage += (float)modifier.getValue();
      }
      for (EntityAttributeModifier modifier : weapon.getAttributeModifiers(EquipmentSlot.MAINHAND).get(EntityAttributes.GENERIC_ATTACK_SPEED)) {
         speed = (float)modifier.getValue();
      }
      speed = Math.max(Math.abs(speed), 0.5F);
      damage += EnchantmentHelper.getAttackDamage(weapon, this.target.getGroup());
      int knockback = EnchantmentHelper.getLevel(Enchantments.KNOCKBACK, weapon);
      int fireAspect = EnchantmentHelper.getLevel(Enchantments.FIRE_ASPECT, weapon);
      if (this.target.damage(this.girl.getDamageSources().mobAttack(this.girl), damage)) {
         float yaw = this.girl.getYaw() * (float)(Math.PI / 180.0);
         this.target.takeKnockback(0.4F + knockback * 0.5F, MathHelper.sin(yaw), -MathHelper.cos(yaw));
         if (fireAspect > 0) {
            this.target.setOnFireFor(fireAspect * 4);
         }
      }
      this.attackTimer = Math.round(speed / 3.373494F * 20.0F);
   }

   private void shootArrow() {
      ItemStack bow = this.girl.inventory.getStack(1);
      ArrowEntity arrow = new ArrowEntity(this.girl.getWorld(), this.girl);
      double dx = this.target.getX() - this.girl.getX();
      double dy = this.target.getBodyY(1.0 / 3.0) - arrow.getY();
      double dz = this.target.getZ() - this.girl.getZ();
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      arrow.setVelocity(dx, dy + horizontal * 0.2, dz, 1.6F, 2.0F);
      arrow.setDamage(4.5);
      int power = EnchantmentHelper.getLevel(Enchantments.POWER, bow);
      if (power > 0) {
         arrow.setDamage(arrow.getDamage() + power * 0.5 + 0.5);
      }
      int punch = EnchantmentHelper.getLevel(Enchantments.PUNCH, bow);
      if (punch > 0) {
         arrow.setPunch(punch);
      }
      if (EnchantmentHelper.getLevel(Enchantments.FLAME, bow) > 0) {
         arrow.setOnFireFor(100);
      }
      this.girl.playSound(SoundEvents.ENTITY_SKELETON_SHOOT, 1.0F, 1.0F / (this.girl.getRandom().nextFloat() * 0.4F + 0.8F));
      this.girl.getWorld().spawnEntity(arrow);
   }

   /** Sets navigation speed and the synced walk animation from how far behind she is. */
   private double followSpeed() {
      double speed;
      BaseGirlEntity.BaseGirlEntityState walk;
      if (this.master.isSprinting()) {
         speed = 0.7;
         walk = BaseGirlEntity.BaseGirlEntityState.RUN;
      } else {
         speed = 0.5;
         walk = BaseGirlEntity.BaseGirlEntityState.WALK;
      }
      speed += Math.floor(this.girl.distanceTo(this.master) / 5.0F) * 0.2;
      if (this.girl.isTouchingWater()) {
         walk = BaseGirlEntity.BaseGirlEntityState.WALK;
      }
      this.girl.getNavigation().setSpeed(speed);
      this.girl.setWalkSpeed(walk);
      return speed;
   }

   private void teleportToMaster() {
      for (int attempt = 0; attempt < 20; attempt++) {
         double x = this.master.getX() + this.girl.getRandom().nextInt(7) - 3;
         double z = this.master.getZ() + this.girl.getRandom().nextInt(7) - 3;
         if (this.girl.teleport(x, this.master.getY(), z, false)) {
            this.girl.getNavigation().stop();
            return;
         }
      }
      this.girl.requestTeleport(this.master.getX(), this.master.getY(), this.master.getZ());
      this.girl.getNavigation().stop();
   }

   private void setAttackMode(int mode) {
      if (this.girl.getDataTracker().get(AbstractGirlNpcEntity.ATTACK_MODE) != mode) {
         this.girl.getDataTracker().set(AbstractGirlNpcEntity.ATTACK_MODE, mode);
      }
   }

   /** Used by the renderer check in tests and by other goals to see what she is fighting. */
   public Entity getTarget() {
      return this.target;
   }
}
