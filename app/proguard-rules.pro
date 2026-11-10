# Keep Room schema metadata and entity fields used by generated adapters.
-keepattributes *Annotation*,Signature
-keep @androidx.room.Entity class * { *; }
-keep class com.lotto.app.data.local.entity.** { *; }
