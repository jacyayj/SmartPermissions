# 防止注解被混淆 / Keep annotations from being obfuscated
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations

# 保持 SmartPermissions 库的核心类及成员 / Keep the core classes and members of SmartPermissions
-keep class com.smart.permissions.** { *; }

# 保持所有使用了 @Permissions 注解的类和方法 / Keep all classes and methods marked with @Permissions
-keep @com.smart.permissions.Permissions class * {*;}
-keepclassmembers class * {
    @com.smart.permissions.Permissions <methods>;
}

# 保持 Kotlin 反射所需的元数据 / Keep Kotlin metadata for reflection
-keep class kotlin.reflect.jvm.internal.** { *; }
-keep class kotlin.Metadata { *; }
