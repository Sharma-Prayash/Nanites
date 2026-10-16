# Serialized engine/model classes: keep names so scoring_config.json and stored snapshots decode in release.
-keep,includedescriptorclasses class com.team.riskauditor.model.** { *; }
-keep,includedescriptorclasses class com.team.riskauditor.security.** { *; }
-keepclassmembers class com.team.riskauditor.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
