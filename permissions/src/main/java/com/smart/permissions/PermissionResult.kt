package com.smart.permissions

/**
 * 权限结果回调 DSL 构建器 / DSL Builder for permission request results.
 *
 * 通过此构建器，开发者可以灵活地定义权限申请后的不同场景处理逻辑。
 * Developers can define logic for different scenarios after a permission request.
 */
class PermissionResultBuilder {
    private var onGranted: (() -> Unit)? = null
    private var onDenied: ((List<String>) -> Unit)? = null
    private var onPermanentlyDenied: ((List<String>) -> Unit)? = null
    private var onSpecialPermission: ((List<String>) -> Unit)? = null

    /**
     * 成功回调：所有申请的权限均已获得时触发。
     * Success callback: Triggered when all requested permissions are granted.
     */
    fun onGranted(block: () -> Unit) {
        this.onGranted = block
    }

    /**
     * 普通拒绝回调：权限被用户拒绝，但并未勾选“不再询问”时触发。
     * Denied callback: Triggered when user denies but hasn't checked "Don't ask again".
     * @param block 接收被拒绝的权限列表 / List of denied permissions.
     */
    fun onDenied(block: (List<String>) -> Unit) {
        this.onDenied = block
    }

    /**
     * 永久拒绝回调：权限被拒绝且勾选了“不再询问”时触发。
     * Permanently denied callback: Triggered when user denies and checked "Don't ask again".
     * @param block 接收被永久拒绝的权限列表 / List of permanently denied permissions.
     */
    fun onPermanentlyDenied(block: (List<String>) -> Unit) {
        this.onPermanentlyDenied = block
    }

    /**
     * 特殊权限回调：针对悬浮窗、修改系统设置等需手动跳转开启的权限。
     * Special permission callback: For permissions like SYSTEM_ALERT_WINDOW that require manual setting.
     * @param block 接收需要手动开启的特殊权限列表 / List of special permissions needing manual action.
     */
    fun onSpecialPermission(block: (List<String>) -> Unit) {
        this.onSpecialPermission = block
    }

    internal fun getOnGranted() = onGranted
    internal fun getOnDenied() = onDenied
    internal fun getOnPermanentlyDenied() = onPermanentlyDenied
    internal fun getOnSpecialPermission() = onSpecialPermission
}
