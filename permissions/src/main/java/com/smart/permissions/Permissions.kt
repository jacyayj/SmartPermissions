package com.smart.permissions

/**
 * 权限请求自定义注解 / Custom annotation for permission requests.
 *
 * 用于标记需要申请权限的类或方法。 / Used to mark classes or methods that require permission requests.
 * 支持作用于：
 * 1. 类 (Activity/Fragment): 在进入页面时自动申请权限。
 * 2. 方法: 在调用该方法前自动拦截并申请权限。
 *
 * @param value 需要申请的权限数组，例如 [android.Manifest.permission.CAMERA]
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Permissions(val value: Array<String>)
