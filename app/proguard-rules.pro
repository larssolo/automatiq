# Enum names are stored as strings in Room DB - renaming them breaks existing installs
-keep class com.vibeactions.domain.model.TriggerType { *; }
-keep class com.vibeactions.domain.model.MacroStatus { *; }
-keep class com.vibeactions.domain.model.AiSendMode { *; }

# kotlinx-serialization: keep generated $serializer companions (Gemini API client)
-keepattributes *Annotation*, InnerClasses
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion *;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$serializer INSTANCE;
}
-keepclassmembers class <2>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** Companion;
}
-keepclassmembers class <2>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
