package com.smart.permissions.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.smart.permissions.Permissions
import com.smart.permissions.PermissionResultBuilder

/**
 * 权限核心引擎 / Core Permission Engine.
 *
 * 负责解析注解、版本适配、特殊权限检查以及执行申请。
 * Responsible for parsing annotations, version adaptation, special permission checks, and execution.
 *
 * @param host 宿主对象 (Activity 或 Fragment) / The host object (Activity or Fragment).
 */
internal class PermissionEngine(private val host: Any) {

    // 启动器：现代 Android 权限申请的标准方式 / Standard Activity Result Launcher.
    private lateinit var launcher: ActivityResultLauncher<Array<String>>
    // 当前请求的回调结果构建器 / The current result builder for callbacks.
    private var resultBuilder: PermissionResultBuilder? = null

    // 特殊权限：无法通过常规弹窗直接申请的权限 / Special permissions needing manual settings.
    private val specialPermissions = listOf(
        Manifest.permission.SYSTEM_ALERT_WINDOW,
        Manifest.permission.WRITE_SETTINGS,
        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        Manifest.permission.REQUEST_INSTALL_PACKAGES
    )

    // 获取宿主关联的 Context / Get the context associated with the host.
    private val context: Context?
        get() = when (host) {
            is ComponentActivity -> host
            is Fragment -> host.context
            else -> null
        }

    /**
     * 绑定宿主并初始化权限启动器。
     * Attach host and initialize the permission launcher.
     * @param caller 实现了 ActivityResultCaller 的对象 / Objects implementing ActivityResultCaller.
     */
    fun attach(caller: ActivityResultCaller) {
        launcher = caller.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val denied = results.filter { !it.value }.keys.toList()
            if (denied.isEmpty()) {
                // 全部权限均已获得 / All permissions granted.
                resultBuilder?.getOnGranted()?.invoke()
            } else {
                // 检查是否永久拒绝 (用户勾选了“不再询问”) / Check for permanent denial.
                val isPermanentlyDenied = denied.any { permission ->
                    when (host) {
                        is ComponentActivity -> !ActivityCompat.shouldShowRequestPermissionRationale(host, permission)
                        is Fragment -> !host.shouldShowRequestPermissionRationale(permission)
                        else -> false
                    }
                }
                if (isPermanentlyDenied) {
                    resultBuilder?.getOnPermanentlyDenied()?.invoke(denied)
                } else {
                    resultBuilder?.getOnDenied()?.invoke(denied)
                }
            }
            resultBuilder = null
        }
        
        // 解析类注解并触发自动申请 / Parse class annotations and trigger auto-request.
        host.javaClass.getAnnotation(Permissions::class.java)?.let {
            requestInternal(it.value, PermissionResultBuilder())
        }
    }

    /**
     * 检查单项权限状态。
     * Check single permission status.
     * 包含特殊权限 (canDrawOverlays 等) 和常规权限。
     * Covers both special (e.g., DrawOverlays) and normal permissions.
     */
    private fun checkPermission(permission: String): Boolean {
        val ctx = context ?: return false
        return when (permission) {
            Manifest.permission.SYSTEM_ALERT_WINDOW -> Settings.canDrawOverlays(ctx)
            Manifest.permission.WRITE_SETTINGS -> Settings.System.canWrite(ctx)
            Manifest.permission.MANAGE_EXTERNAL_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.os.Environment.isExternalStorageManager()
                } else true
            }
            else -> ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 根据 Android 系统版本适配权限列表。
     * Adapt permission list based on Android OS version.
     * 例如：Android 13+ 中将 READ_EXTERNAL_STORAGE 转换为媒体权限。
     * e.g., Android 13+ converts READ_EXTERNAL_STORAGE into media permissions.
     */
    private fun adaptPermissions(permissions: Array<String>): List<String> {
        val adapted = mutableListOf<String>()
        permissions.forEach { permission ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                when (permission) {
                    Manifest.permission.READ_EXTERNAL_STORAGE -> {
                        adapted.add(Manifest.permission.READ_MEDIA_IMAGES)
                        adapted.add(Manifest.permission.READ_MEDIA_VIDEO)
                        adapted.add(Manifest.permission.READ_MEDIA_AUDIO)
                    }
                    else -> adapted.add(permission)
                }
            } else {
                adapted.add(permission)
            }
        }
        return adapted.distinct()
    }

    /**
     * 内部核心申请逻辑。
     * Core internal request logic.
     * 流程：适配版本 -> 识别特殊权限 -> 启动常规申请。
     * Process: Adapt versions -> Detect special -> Launch normal request.
     */
    private fun requestInternal(permissions: Array<String>, builder: PermissionResultBuilder) {
        val adapted = adaptPermissions(permissions)
        
        // 1. 过滤特殊权限 / Filter special permissions.
        val specialRequired = adapted.filter { it in specialPermissions && !checkPermission(it) }
        if (specialRequired.isNotEmpty()) {
            builder.getOnSpecialPermission()?.invoke(specialRequired)
            // 如果只有特殊权限则退出流程 / Exit if only special permissions remain.
            if (adapted.size == specialRequired.size) return
        }

        // 2. 过滤常规权限 / Filter normal permissions.
        val normalDenied = adapted.filter { it !in specialPermissions && !checkPermission(it) }.toTypedArray()

        if (normalDenied.isEmpty()) {
            builder.getOnGranted()?.invoke()
        } else {
            this.resultBuilder = builder
            launcher.launch(normalDenied)
        }
    }

    /**
     * 方法注解拦截核心逻辑。
     * Core logic for intercepting method annotations.
     * @param builder 回调构建器 / The callback result builder.
     * @param source 调用来源对象 / The source object for annotation lookup.
     */
    fun runWithAnnotation(builder: PermissionResultBuilder, source: Any) {
        val stackTrace = Thread.currentThread().stackTrace
        // 遍历堆栈，找到被 @Permissions 标记且在宿主类中定义的调用方法。
        // Find the calling method defined in host and marked with @Permissions in stackTrace.
        val annotatedMethod = source.javaClass.declaredMethods.find { method ->
            method.isAnnotationPresent(Permissions::class.java) && 
            stackTrace.any { it.methodName == method.name }
        }
        val annotation = annotatedMethod?.getAnnotation(Permissions::class.java)
        if (annotation != null) {
            requestInternal(annotation.value, builder)
        } else {
            builder.getOnGranted()?.invoke()
        }
    }
}
