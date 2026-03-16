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
 */
internal class PermissionEngine(private val host: Any) {

    private lateinit var launcher: ActivityResultLauncher<Array<String>>
    private var resultBuilder: PermissionResultBuilder? = null

    private val specialPermissions = listOf(
        Manifest.permission.SYSTEM_ALERT_WINDOW,
        Manifest.permission.WRITE_SETTINGS,
        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        Manifest.permission.REQUEST_INSTALL_PACKAGES
    )

    private val context: Context?
        get() = when (host) {
            is ComponentActivity -> host
            is Fragment -> host.context
            else -> null
        }

    fun attach(caller: ActivityResultCaller) {
        launcher = caller.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val denied = results.filter { !it.value }.keys.toList()
            if (denied.isEmpty()) {
                resultBuilder?.getOnGranted()?.invoke()
            } else {
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
        
        host.javaClass.getAnnotation(Permissions::class.java)?.let {
            requestInternal(it.value.toList(), PermissionResultBuilder())
        }
    }

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

    private fun adaptPermissions(permissions: List<String>): List<String> {
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
     * 修改为 internal，支持直接传入权限列表请求
     */
    internal fun requestInternal(permissions: List<String>, builder: PermissionResultBuilder) {
        val adapted = adaptPermissions(permissions)
        
        val specialRequired = adapted.filter { it in specialPermissions && !checkPermission(it) }
        if (specialRequired.isNotEmpty()) {
            builder.getOnSpecialPermission()?.invoke(specialRequired)
            if (adapted.size == specialRequired.size) return
        }

        val normalDenied = adapted.filter { it !in specialPermissions && !checkPermission(it) }.toTypedArray()

        if (normalDenied.isEmpty()) {
            builder.getOnGranted()?.invoke()
        } else {
            this.resultBuilder = builder
            launcher.launch(normalDenied)
        }
    }

    fun runWithAnnotation(builder: PermissionResultBuilder, source: Any) {
        val stackTrace = Thread.currentThread().stackTrace
        val annotatedMethod = source.javaClass.declaredMethods.find { method ->
            method.isAnnotationPresent(Permissions::class.java) && 
            stackTrace.any { it.methodName == method.name }
        }
        val annotation = annotatedMethod?.getAnnotation(Permissions::class.java)
        if (annotation != null) {
            requestInternal(annotation.value.toList(), builder)
        } else {
            builder.getOnGranted()?.invoke()
        }
    }
}
