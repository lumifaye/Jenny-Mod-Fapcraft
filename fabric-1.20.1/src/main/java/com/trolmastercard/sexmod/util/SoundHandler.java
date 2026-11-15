package com.trolmastercard.sexmod.util;

import com.trolmastercard.sexmod.SexMod;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Sound events. Each array field {@code A_B_C} with N slots maps to the
 * sounds.json entries {@code a.b.c.c0 .. a.b.c.c(N-1)} (two-part names
 * {@code A_B} map to {@code a.b.b0 ..}), exactly as in the 1.12.2 mod, so the
 * shared sounds.json is used unchanged.
 * <p>
 * Only the arrays used by the ported characters are declared; the remaining
 * 1.12.2 arrays come back together with their characters.
 */
public final class SoundHandler {
   public static final SoundEvent[] MISC_PLOB = new SoundEvent[1];
   public static final SoundEvent[] MISC_BELLJINGLE = new SoundEvent[1];
   public static final SoundEvent[] MISC_BEDRUSTLE = new SoundEvent[2];
   public static final SoundEvent[] MISC_SLAP = new SoundEvent[2];
   public static final SoundEvent[] MISC_TOUCH = new SoundEvent[2];
   public static final SoundEvent[] MISC_POUNDING = new SoundEvent[35];
   public static final SoundEvent[] MISC_SMALLINSERTS = new SoundEvent[5];
   public static final SoundEvent[] MISC_INSERTS = new SoundEvent[5];
   public static final SoundEvent[] MISC_CUMINFLATION = new SoundEvent[1];
   public static final SoundEvent[] GIRLS_JENNY_AFTERSESSIONMOAN = new SoundEvent[5];
   public static final SoundEvent[] GIRLS_JENNY_AHH = new SoundEvent[10];
   public static final SoundEvent[] GIRLS_JENNY_BJMOAN = new SoundEvent[13];
   public static final SoundEvent[] GIRLS_JENNY_GIGGLE = new SoundEvent[5];
   public static final SoundEvent[] GIRLS_JENNY_HAPPYOH = new SoundEvent[3];
   public static final SoundEvent[] GIRLS_JENNY_HEAVYBREATHING = new SoundEvent[8];
   public static final SoundEvent[] GIRLS_JENNY_HMPH = new SoundEvent[4];
   public static final SoundEvent[] GIRLS_JENNY_HUH = new SoundEvent[2];
   public static final SoundEvent[] GIRLS_JENNY_LIGHTBREATHING = new SoundEvent[12];
   public static final SoundEvent[] GIRLS_JENNY_LIPSOUND = new SoundEvent[10];
   public static final SoundEvent[] GIRLS_JENNY_MMM = new SoundEvent[9];
   public static final SoundEvent[] GIRLS_JENNY_MOAN = new SoundEvent[8];
   public static final SoundEvent[] GIRLS_JENNY_SADOH = new SoundEvent[2];
   public static final SoundEvent[] GIRLS_JENNY_SIGH = new SoundEvent[2];
   public static final SoundEvent[] GIRLS_ELLIE_AFTERSESSIONMOAN = new SoundEvent[5];
   public static final SoundEvent[] GIRLS_ELLIE_AHH = new SoundEvent[10];
   public static final SoundEvent[] GIRLS_ELLIE_BJMOAN = new SoundEvent[13];
   public static final SoundEvent[] GIRLS_ELLIE_GIGGLE = new SoundEvent[5];
   public static final SoundEvent[] GIRLS_ELLIE_HAPPYOH = new SoundEvent[3];
   public static final SoundEvent[] GIRLS_ELLIE_HEAVYBREATHING = new SoundEvent[8];
   public static final SoundEvent[] GIRLS_ELLIE_HMPH = new SoundEvent[4];
   public static final SoundEvent[] GIRLS_ELLIE_HUH = new SoundEvent[2];
   public static final SoundEvent[] GIRLS_ELLIE_LIGHTBREATHING = new SoundEvent[8];
   public static final SoundEvent[] GIRLS_ELLIE_LIPSOUND = new SoundEvent[10];
   public static final SoundEvent[] GIRLS_ELLIE_MMM = new SoundEvent[9];
   public static final SoundEvent[] GIRLS_ELLIE_MOAN = new SoundEvent[9];
   public static final SoundEvent[] GIRLS_ELLIE_SADOH = new SoundEvent[2];
   public static final SoundEvent[] GIRLS_ELLIE_SIGH = new SoundEvent[2];
   public static final SoundEvent[] GIRLS_ELLIE_COMETOMOMMY = new SoundEvent[2];
   public static final SoundEvent[] GIRLS_ELLIE_GOODBOY = new SoundEvent[2];
   public static final SoundEvent[] GIRLS_ELLIE_MOMMYHORNY = new SoundEvent[2];

   private static final Random RANDOM = new Random();
   private static final HashMap<SoundEvent, Integer> LAST_RANDOM_SOUND = new HashMap<>();

   private SoundHandler() {
   }

   public static void registerSounds() {
      for (Field field : SoundHandler.class.getDeclaredFields()) {
         if (field.getType() != SoundEvent[].class) {
            continue;
         }
         SoundEvent[] soundEvents;
         try {
            soundEvents = (SoundEvent[])field.get(null);
         } catch (IllegalAccessException e) {
            SexMod.LOGGER.error("Error registering sound: " + e.getMessage());
            continue;
         }
         String soundName = field.getName().toLowerCase(Locale.ROOT).replace("_", ".");
         String[] parts = soundName.split("\\.");
         String category = parts.length > 2 ? parts[2] : parts[1];
         for (int i = 0; i < soundEvents.length; i++) {
            soundEvents[i] = registerSound(soundName + "." + category + i);
         }
      }
   }

   private static SoundEvent registerSound(String soundName) {
      Identifier id = SexMod.id(soundName);
      return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
   }

   /** Picks a random sound of the group, avoiding an immediate repeat. */
   public static SoundEvent randomSound(SoundEvent[] sounds) {
      int last = LAST_RANDOM_SOUND.getOrDefault(sounds[0], -1);
      int attempts = 0;
      int index;
      do {
         index = RANDOM.nextInt(sounds.length);
      } while (++attempts < 10 && index == last);
      LAST_RANDOM_SOUND.put(sounds[0], index);
      return sounds[index];
   }
}
