# 保留强智/凌育字段名，混淆时避免解析层反射失效
-keep class com.hutb.timetable.data.model.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod

# Tink / security-crypto 的注解依赖只在编译期存在
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
