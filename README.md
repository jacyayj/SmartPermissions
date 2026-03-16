# SmartPermissions

一个基于 Kotlin 注解与 DSL 设计的 Android 权限请求框架。支持 **零侵入** 初始化、自动版本适配、以及类与方法级别的权限拦截。

---

## 🚀 核心特性

- **零侵入设计**：基于 `LifecycleCallbacks` 自动注入，无需继承 Base 类。
- **注解驱动**：支持 `@Permissions` 作用于 Activity/Fragment 类或具体方法。
- **DSL 回调**：优雅的 `runWithPermissions` DSL，支持权限成功、拒绝、永久拒绝及特殊权限处理。
- **版本自动适配**：内部自动处理 Android 13+ (API 33) 存储权限转换。
- **特殊权限支持**：支持悬浮窗、修改系统设置、全文件管理等需手动开启的权限。
- **全架构支持**：完美兼容 `AppCompatActivity`、`ComponentActivity` (Compose) 以及 `Fragment`。

---

## 🛠 实现原理

### 1. 生命周期自动绑定
利用 `Application.ActivityLifecycleCallbacks` 和 `FragmentManager.FragmentLifecycleCallbacks` 监听宿主的创建。
- 在 `onCreated` 阶段，利用 **Activity Result API** (`registerForActivityResult`) 注册权限启动器。
- 使用 `WeakHashMap` 存储每个宿主对应的权限引擎，确保随生命周期自动释放，防止内存泄漏。

### 2. 注解拦截机制
- **类级别**：在引擎 `attach` 宿主时，反射解析类上的 `@Permissions` 注解并立即触发申请。
- **方法级别**：在调用 `runWithPermissions` 时，通过分析 `Thread.currentThread().stackTrace` 动态定位调用方方法名，并匹配其上的注解参数。

### 3. 异步流转控制
将传统的异步回调包装在自定义的 `PermissionResultBuilder` (DSL) 中。通过 `pendingAction` 暂存业务逻辑，在权限申请结果返回后再根据状态分发执行。

---

## 📋 业务流程

1. **初始化**：`Application` 注册监听，全局感知 Activity/Fragment 创建。
2. **感知权限需求**：
   - **进入页面** -> 检查类注解 -> 发现权限需求 -> 发起申请。
   - **点击触发** -> 执行扩展函数 -> 堆栈定位方法注解 -> 拦截业务逻辑 -> 发起申请。
3. **适配处理**：检查系统版本，若是 Android 13+，自动将 `READ_EXTERNAL_STORAGE` 映射为媒体权限组。
4. **特殊权限拦截**：识别 `SYSTEM_ALERT_WINDOW` 等特殊权限，若未开启则中断常规流程，触发 `onSpecialPermission`。
5. **结果分发**：
   - 允许 -> 执行业务闭包。
   - 拒绝 -> 触发 `onDenied`。
   - 永久拒绝 -> 触发 `onPermanentlyDenied`。

---

## 📦 集成准备

在 `:app` 模块的 `build.gradle.kts` 中添加依赖：

```kotlin
dependencies {
    implementation(project(":permissions"))
    // 必须添加 Kotlin 反射库支持
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.22") 
}
```

---

## 📖 使用指南

### 1. 初始化
在自定义的 `Application` 中初始化：

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SmartPermissions.init(this)
    }
}
```

### 2. 类级别使用 (进入即申请)
```kotlin
@Permissions([Manifest.permission.CAMERA])
class MainActivity : ComponentActivity() {
    // 页面启动会自动弹出相机权限申请
}
```

### 3. 方法级别使用 (调用时拦截)
```kotlin
class MyFragment : Fragment() {

    @Permissions([Manifest.permission.RECORD_AUDIO])
    fun startRecord() = runWithPermissions {
        onGranted {
            // 权限通过，执行业务逻辑
        }
        onDenied { denied ->
            // 用户拒绝
        }
        onPermanentlyDenied { denied ->
            // 用户勾选“不再询问”，建议在此弹出 Dialog 引导去设置
        }
    }
}
```

### 4. 特殊权限处理 (DSL)
```kotlin
@Permissions([Manifest.permission.SYSTEM_ALERT_WINDOW])
fun showOverlay() = runWithPermissions {
    onSpecialPermission { specials ->
        // specials 包含 android.permission.SYSTEM_ALERT_WINDOW
        // 引导用户跳转到设置页开启悬浮窗
    }
}
```

---

## 🛡 混淆配置

如果你的项目开启了 R8/Proguard 混淆，请参考以下规则。
**注意**：由于本库在 `:permissions` 模块中配置了 `consumerProguardFiles`，通常情况下集成方无需手动添加，库的混淆规则会自动合并。

```proguard
# 防止注解被混淆
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations

# 保持 SmartPermissions 库的核心类
-keep class com.smart.permissions.** { *; }

# 保持所有被 @Permissions 标记的方法和类
-keep @com.smart.permissions.Permissions class * {*;}
-keepclassmembers class * {
    @com.smart.permissions.Permissions <methods>;
}

# 保持 Kotlin 反射所需的元数据
-keep class kotlin.reflect.jvm.internal.** { *; }
-keep class kotlin.Metadata { *; }
```

---

## ⚠️ 注意事项
1. **清单声明**：所有在注解中使用的权限，必须在 `AndroidManifest.xml` 中同步声明。
2. **方法引用**：使用方法注解时，请确保在 `runWithPermissions` 闭包外层正确标记 `@Permissions`。
