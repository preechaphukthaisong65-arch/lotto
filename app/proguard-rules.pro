# Keep Room schema metadata and entity fields used by generated adapters.
-keepattributes *Annotation*,Signature
-keep @androidx.room.Entity class * { *; }
-keep class com.lotto.app.data.local.entity.** { *; }

# SQLCipher native bindings and Room support factory.
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class net.zetetic.database.** { *; }
