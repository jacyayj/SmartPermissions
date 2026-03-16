package com.smart.permissions

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment

// 情况 1: 类注解。Activity 启动时会自动申请 CAMERA 权限，无需在子类写任何代码
@Permissions([Manifest.permission.CAMERA])
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Button(onClick = { startWork() }) {
                    Text("调用受限方法 (系统弹窗)")
                }

                Button(onClick = {
                    runWithPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE) {
                        // 成功回调
                        onGranted {
                            Log.d("SmartPermissions", "存储权限已获得，开始执行业务")
                        }

                        // 普通拒绝（用户点了一次拒绝）
                        onDenied { deniedPermissions ->
                            Log.d("SmartPermissions", "用户拒绝了: $deniedPermissions")
                        }

                        // 永久拒绝（用户勾选了不再询问）
                        onPermanentlyDenied { deniedPermissions ->
                            // 这里你可以弹出自定义的对话框，引导用户去设置
                            Log.d("SmartPermissions", "用户永久拒绝了: $deniedPermissions")
                        }
                    }
                }) {
                    Text("调用受限方法 (存储)")
                }
            }
        }
    }

    @Permissions([Manifest.permission.SYSTEM_ALERT_WINDOW])
    fun startWork() = runWithPermissions {
        onGranted {
            Log.d("SmartPermissions", "系统弹窗权限已获得，开始执行业务")
        }

        onSpecialPermission { specials ->
            // 包含了特殊权限（如悬浮窗），需要你引导跳转
            Log.d("SmartPermissions", "${specials}特殊权限，需手动开启")
        }
    }
}
