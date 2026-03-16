package com.smart.permissions

import android.app.Application

class SmartApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 全局初始化，自动管理所有 Activity 的生命周期和权限申请
        SmartPermissions.init(this)
    }
}
