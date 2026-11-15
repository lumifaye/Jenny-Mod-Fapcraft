package com.trolmastercard.sexmod.client.gui;

import com.trolmastercard.sexmod.entity.AbstractGirlNpcEntity;
import com.trolmastercard.sexmod.entity.BaseGirlEntity;
import com.trolmastercard.sexmod.networking.ModPackets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * The menu opened by right-clicking a girl (1.12.2 {@code GirlInventoryScreen}):
 * her own actions, each with its price, followed by the companion commands.
 * The equipment page of the 1.12.2 screen is not ported.
 */
public class ActionMenuScreen extends Screen {
   private static final int BUTTON_WIDTH = 160;
   private static final int BUTTON_HEIGHT = 20;
   private static final int ROW_HEIGHT = 24;
   private static final double MAX_DISTANCE = 6.0;

   private final BaseGirlEntity girl;
   /** The girl's own actions, in menu order. */
   private final List<Entry> entries = new ArrayList<>();
   /** Every row of the current layout: {@link #entries} plus the companion commands. */
   private List<Entry> layoutRows = new ArrayList<>();
   private int firstRowY;

   public ActionMenuScreen(BaseGirlEntity girl, String[] options, ItemStack[] costs) {
      super(Text.literal(girl.getEffectiveDisplayName()));
      this.girl = girl;
      for (int i = 0; i < options.length; i++) {
         ItemStack cost = costs != null && i < costs.length && costs[i] != null ? costs[i] : ItemStack.EMPTY;
         this.entries.add(new Entry(options[i], cost));
      }
   }

   @Override
   protected void init() {
      List<Entry> rows = new ArrayList<>(this.entries);
      if (this.girl instanceof AbstractGirlNpcEntity) {
         if (this.girl.hasMaster()) {
            rows.add(new Entry("action.names.stopfollowme", ItemStack.EMPTY));
            rows.add(new Entry("action.names.gohome", ItemStack.EMPTY));
         } else {
            rows.add(new Entry("action.names.followme", ItemStack.EMPTY));
         }
         rows.add(new Entry("action.names.setnewhome", ItemStack.EMPTY));
      }
      int x = (this.width - BUTTON_WIDTH) / 2;
      this.firstRowY = Math.max(32, (this.height - rows.size() * ROW_HEIGHT) / 2);
      for (int i = 0; i < rows.size(); i++) {
         Entry entry = rows.get(i);
         entry.y = this.firstRowY + i * ROW_HEIGHT;
         ButtonWidget button = ButtonWidget.builder(Text.translatable(entry.action), pressed -> this.choose(entry))
            .dimensions(x, entry.y, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build();
         if (!entry.cost.isEmpty()) {
            boolean affordable = this.canAfford(entry.cost);
            button.active = affordable;
            Text price = Text.translatable("sexmod.menu.cost", Text.literal(entry.cost.getCount() + "x ").append(entry.cost.getName()));
            button.setTooltip(Tooltip.of(affordable ? price : Text.translatable("sexmod.menu.cannot_afford").append("\n").append(price)));
         }
         this.addDrawableChild(button);
      }
      this.layoutRows = rows;
   }

   /**
    * The price is taken from one stack, so one stack must hold enough. The
    * server applies the same rule when it removes the items.
    */
   private boolean canAfford(ItemStack cost) {
      PlayerEntity player = this.client == null ? null : this.client.player;
      if (player == null) {
         return false;
      }
      if (player.isCreative()) {
         return true;
      }
      for (ItemStack stack : player.getInventory().main) {
         if (stack.isOf(cost.getItem()) && stack.getCount() >= cost.getCount()) {
            return true;
         }
      }
      return false;
   }

   private void choose(Entry entry) {
      PlayerEntity player = this.client == null ? null : this.client.player;
      this.close();
      if (player == null || this.girl.isRemoved()) {
         return;
      }
      if (!entry.cost.isEmpty()) {
         if (!this.canAfford(entry.cost)) {
            return;
         }
         ModPackets.sendRemoveItems(entry.cost);
      }
      UUID playerUuid = player.getUuid();
      this.girl.doAction(entry.action, playerUuid);
   }

   @Override
   public void tick() {
      PlayerEntity player = this.client == null ? null : this.client.player;
      if (player == null || this.girl.isRemoved() || player.squaredDistanceTo(this.girl) > MAX_DISTANCE * MAX_DISTANCE) {
         this.close();
      }
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      this.renderBackground(context);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.firstRowY - 16, 0xFFFFFF);
      int iconX = (this.width + BUTTON_WIDTH) / 2 + 6;
      for (Entry entry : this.layoutRows) {
         if (!entry.cost.isEmpty()) {
            context.drawItem(entry.cost, iconX, entry.y + 2);
            context.drawItemInSlot(this.textRenderer, entry.cost, iconX, entry.y + 2);
         }
      }
      super.render(context, mouseX, mouseY, delta);
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   private static final class Entry {
      final String action;
      final ItemStack cost;
      int y;

      Entry(String action, ItemStack cost) {
         this.action = action;
         this.cost = cost;
      }
   }
}
