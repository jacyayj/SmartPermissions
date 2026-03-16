package com.smart.permissions

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.smart.permissions.core.PermissionEngine
import java.util.WeakHashMap

/**
 * SmartPermissions 库全局配置与入口 / Global configuration and entry point.
 *
 * 核心设计：通过生命周期监听实现“零侵入”权限注入。
 * Core design: Achieve "non-invasive" permission injection through lifecycle callbacks.
 */
object SmartPermissions {
    // 标记库是否已执行初始化 / Flag indicating whether the library has been initialized.
    private var isInitialized = false

    // 内存安全容器：使用 WeakHashMap 存储引擎，随宿主销毁自动释放。
    // Memory-safe containers: Automatically released when hosts are destroyed.
    private val activityEngines = WeakHashMap<Activity, PermissionEngine>()
    private val fragmentEngines = WeakHashMap<Fragment, PermissionEngine>()

    /**
     * 全局初始化：建议在 Application.onCreate() 中调用。
     * Global initialization: Suggest calling this in Application.onCreate().
     *
     * 自动拦截所有 Activity 和 Fragment 的生命周期以注入权限引擎。
     * Automatically intercept Activity and Fragment lifecycles to inject permission engines.
     *
     * @param application 全局上下文 / Global application instance.
     */
    fun init(application: Application) {
        if (isInitialized) return
        isInitialized = true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // 1. 处理 Activity (支持 ComponentActivity 及 AppCompatActivity)
                if (activity is ComponentActivity) {
                    val engine = PermissionEngine(activity)
                    activityEngines[activity] = engine
                    engine.attach(activity)
                }

                // 2. 注册 Fragment 监听 (仅针对 FragmentActivity) / Register Fragment callbacks.
                if (activity is FragmentActivity) {
                    activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                        object : FragmentManager.FragmentLifecycleCallbacks() {
                            override fun onFragmentCreated(fm: FragmentManager, f: Fragment, savedInstanceState: Bundle?) {
                                val engine = PermissionEngine(f)
                                fragmentEngines[f] = engine
                                engine.attach(f)
                            }
                            override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
                                fragmentEngines.remove(f)
                            }
                        }, true
                    )
                }
            }

            override fun onActivityDestroyed(activity: Activity) {
                activityEngines.remove(activity)
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }

    /**
     * 内部检查初始化状态 / Internal check for initialization state.
     * 如果未初始化则抛出异常，防止静默失败导致权限绕过假象。
     */
    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException(
                "SmartPermissions is not initialized. \n" +
                "Please call 'SmartPermissions.init(this)' in your Application class before using 'runWithPermissions'."
            )
        }
    }

    internal fun getEngine(activity: Activity): PermissionEngine? {
        checkInitialized()
        return activityEngines[activity]
    }

    internal fun getEngine(fragment: Fragment): PermissionEngine? {
        checkInitialized()
        return fragmentEngines[fragment]
    }
}

/**
 * 针对 Activity 的 DSL 扩展函数。
 * DSL extension function for Activity.
 * @param block 开发者定义的权限结果回调逻辑 / Callback logic defined by developer.
 */
fun ComponentActivity.runWithPermissions(block: PermissionResultBuilder.() -> Unit) {
    val builder = PermissionResultBuilder().apply(block)
    // 移除 ?: 默认成功逻辑。未初始化时 getEngine 内部会抛出异常告知开发者。
    SmartPermissions.getEngine(this)?.runWithAnnotation(builder, this)
}

/**
 * 针对 Fragment 的 DSL 扩展函数。
 * DSL extension function for Fragment.
 * @param block 开发者定义的权限结果回调逻辑 / Callback logic defined by developer.
 */
fun Fragment.runWithPermissions(block: PermissionResultBuilder.() -> Unit) {
    val builder = PermissionResultBuilder().apply(block)
    SmartPermissions.getEngine(this)?.runWithAnnotation(builder, this)
}
