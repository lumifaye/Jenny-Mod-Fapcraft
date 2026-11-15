package com.trolmastercard.sexmod.entity.api;

/**
 * A girl that walks up to the player before a scene starts. The name is the
 * 1.12.2 one (Ellie was the first to use it) and is kept so the remaining
 * characters port over without renaming.
 */
public interface IEllie {
   /** Starts the 40-tick scene-entry walk towards the interaction player (server side). */
   void setDismounted();
}
