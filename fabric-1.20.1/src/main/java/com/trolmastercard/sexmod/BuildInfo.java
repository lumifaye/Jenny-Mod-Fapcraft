package com.trolmastercard.sexmod;

import java.io.InputStream;
import java.util.Properties;

/** Mod version, read from {@code sexmod-build.properties} (filled in by Gradle). */
public final class BuildInfo {
   public static final String DEFAULT_VERSION = "1.1.3-fabric.0";
   private static final String RESOURCE = "/sexmod-build.properties";
   public static final String VERSION = load();

   private BuildInfo() {
   }

   private static String load() {
      try (InputStream in = BuildInfo.class.getResourceAsStream(RESOURCE)) {
         if (in == null) {
            return DEFAULT_VERSION;
         }
         Properties props = new Properties();
         props.load(in);
         String v = props.getProperty("version");
         if (v == null) {
            return DEFAULT_VERSION;
         }
         v = v.trim();
         return v.isEmpty() || v.contains("${") ? DEFAULT_VERSION : v;
      } catch (Throwable t) {
         return DEFAULT_VERSION;
      }
   }
}
