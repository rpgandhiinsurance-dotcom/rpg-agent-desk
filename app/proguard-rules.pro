# kotlinx.serialization: keep generated serializers for the cached Brief model.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.rpginsurance.brief.** {
    *** Companion;
}
-keepclasseswithmembers class com.rpginsurance.brief.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.rpginsurance.brief.**$$serializer { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
