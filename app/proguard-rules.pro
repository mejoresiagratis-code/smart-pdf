# PdfBox-Android
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.mejoresiagratis.rellenador.**$$serializer { *; }
-keepclassmembers class com.mejoresiagratis.rellenador.** {
    *** Companion;
}
-keepclasseswithmembers class com.mejoresiagratis.rellenador.** {
    kotlinx.serialization.KSerializer serializer(...);
}
