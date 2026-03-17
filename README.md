# SmartPermissions[![](https://jitpack.io/v/jacyayj/SmartPermissions.svg)](https://jitpack.io/#jacyayj/SmartPermissions)

一个基于 Kotlin 注解与 DSL 设计的 Android 权限请求框架。支持 **零侵入** 初始化、自动版本适配、以及类与方法级别的权限拦截。

---

## 🚀 核心特性

- **零侵入设计**：基于 `LifecycleCallbacks` 自动注入，无需继承 Base 类。
- **双模式支持**：支持 **@Permissions 注解模式** 和 **直接传参模式**。
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

### 2. 权限拦截机制
- **类级别注解**：在引擎 `attach` 宿主时，反射解析类上的 `@Permissions` 注解并立即触发申请。
- **方法级别注解**：在调用无参 `runWithPermissions { }` 时，通过分析堆栈动态定位调用方方法上的注解。
- **直接传参模式**：在调用带参 `runWithPermissions(vararg permissions) { }` 时，直接跳过反射流程，由引擎立即执行适配与申请逻辑。

### 3. 异步流转控制
将传统的异步回调包装在自定义的 `PermissionResultBuilder` (DSL) 中。通过 `pendingAction` 暂存业务逻辑，在权限申请结果返回后再根据状态分发执行。

---

## 📋 业务流程

1. **初始化**：`Application` 注册监听，全局感知 Activity/Fragment 创建。
2. **感知权限需求**：
   - **类注解** -> 检查类上的 `@Permissions`。
   - **方法注解** -> 调用 `runWithPermissions { }` (无参)，通过堆栈定位注解。
   - **直接传参** -> 调用 `runWithPermissions("权限名")` (带参)，直接获取权限列表。
3. **适配处理**：检查系统版本，若是 Android 13+，自动将存储权限映射为媒体权限组。
4. **特殊权限拦截**：识别 `SYSTEM_ALERT_WINDOW` 等特殊权限，触发 `onSpecialPermission`。
5. **结果分发**：允许则执行 `onGranted`，否则触发 `onDenied` 或 `onPermanentlyDenied`。

---

## 📖 使用指南

### 1. 添加依赖

首先，在项目根目录的 `settings.gradle.kts` 或 `build.gradle` 中添加 JitPack 仓库：
```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}
```

接着，在 App 模块的 `build.gradle.kts` 中添加依赖：
```kotlin
dependencies {
    implementation("com.github.jacyayj:SmartPermissions:1.0.1")
}
```

### 2. 初始化
在自定义的 `Application` 中初始化：
```kotlin
SmartPermissions.init(this)
```

### 3. 类级别使用 (进入即申请)
```kotlin
@Permissions([Manifest.permission.CAMERA])
class MainActivity : ComponentActivity() { }
```

### 4. 直接传参使用 (无注解模式 - 推荐 🚀)
无需在方法上声明注解，直接在逻辑代码中传入权限，**性能更好且更灵活**：
```kotlin
fun openAlbum() {
    runWithPermissions(Manifest.permission.READ_EXTERNAL_STORAGE) {
        onGranted {
            // 权限已获得，执行相册打开逻辑
        }
    }
}
```

### 5. 方法级别使用 (注解模式)
```kotlin
@Permissions([Manifest.permission.RECORD_AUDIO])
fun startRecord() = runWithPermissions {
    onGranted { /* 录音逻辑 */ }
}
```

### 6. 特殊权限处理 (DSL)
```kotlin
runWithPermissions(Manifest.permission.SYSTEM_ALERT_WINDOW) {
    onSpecialPermission { specials ->
        // 引导用户跳转到设置页开启悬浮窗
    }
}
```

---

## 🛡 混淆配置
由于本库使用了反射解析注解，若开启混淆，请确保已包含以下规则（库已内置 `consumerProguardFiles`）：
```proguard
-keepattributes *Annotation*
-keep class com.smart.permissions.** { *; }

# 保持所有被 @Permissions 标记的方法和类
-keep @com.smart.permissions.Permissions class * {*;}
-keepclassmembers class * { @com.smart.permissions.Permissions <methods>; }
```

---

## ⚠️ 注意事项
1. **必须初始化**：必须在 `Application` 中调用 `init()`，否则会抛出 `IllegalStateException`。
2. **清单声明**：所有权限必须在 `AndroidManifest.xml` 中声明。
3. **直接传参 vs 注解**：推荐优先使用直接传参模式，它规避了反射堆栈查找，运行效率更高。
