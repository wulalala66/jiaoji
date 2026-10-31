# 胶记 — R8 规则
# 无反射序列化、无 Room/KSP，默认规则即可。
# 清单里声明的组件（Activity/Service/Receiver）由 AGP 自动保留。

-dontwarn org.jetbrains.annotations.**

# 保留行号，崩溃栈可读（体积影响可忽略）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
