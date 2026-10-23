package com.aujunpeak.app.dynamicui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.aujunpeak.app.activities.AboutActivity
import com.aujunpeak.app.activities.CrosshairActivity
import com.aujunpeak.app.activities.FeedbackActivity
import com.aujunpeak.app.activities.ForceUpdateActivity
import com.aujunpeak.app.activities.GameBoostActivity
import com.aujunpeak.app.activities.MainActivity
import com.aujunpeak.app.activities.OptimizerSuperActivity
import com.aujunpeak.app.activities.PolicyActivity
import com.aujunpeak.app.activities.SocialLinksActivity
import com.aujunpeak.app.activities.SplashActivity
import com.aujunpeak.app.activities.UpdateActivity
import com.aujunpeak.app.fragments.FunctionFragment
import com.aujunpeak.app.fragments.GameFragment
import com.aujunpeak.app.fragments.HomeFragment
import com.aujunpeak.app.fragments.InfoFragment
import com.aujunpeak.app.fragments.LoginFragment
import com.aujunpeak.app.fragments.SettingsFragment
import java.util.Collections
import java.util.WeakHashMap

object DynamicUiRuntime {
    private val activities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())
    private val fragmentCallbacksInstalled = Collections.newSetFromMap(WeakHashMap<FragmentActivity, Boolean>())
    private var application: Application? = null

    fun install(app: Application) {
        if (application != null) return
        application = app
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                activities.add(activity)
                if (activity is FragmentActivity && fragmentCallbacksInstalled.add(activity)) {
                    activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                        object : FragmentManager.FragmentLifecycleCallbacks() {
                            override fun onFragmentViewCreated(
                                fm: FragmentManager,
                                fragment: Fragment,
                                view: View,
                                savedInstanceState: Bundle?
                            ) {
                                DynamicUiBinder.apply(activity, view, screenKeyForFragment(fragment))
                            }
                        },
                        true
                    )
                }
            }

            override fun onActivityResumed(activity: Activity) = applyActivity(activity)

            override fun onActivityDestroyed(activity: Activity) {
                activities.remove(activity)
                if (activity is FragmentActivity) fragmentCallbacksInstalled.remove(activity)
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        })
    }

    fun reapplyAll() {
        val app = application ?: return
        android.os.Handler(app.mainLooper).post {
            activities.toList().forEach { activity ->
                if (!activity.isFinishing && !activity.isDestroyed) applyActivity(activity)
            }
        }
    }

    private fun applyActivity(activity: Activity) {
        val root = activity.window?.decorView ?: return
        DynamicUiBinder.apply(activity, root, screenKeyForActivity(activity))
        if (activity is FragmentActivity) {
            activity.supportFragmentManager.fragments.forEach { applyFragmentRecursive(activity, it) }
        }
    }

    private fun applyFragmentRecursive(activity: FragmentActivity, fragment: Fragment) {
        fragment.view?.let { DynamicUiBinder.apply(activity, it, screenKeyForFragment(fragment)) }
        fragment.childFragmentManager.fragments.forEach { applyFragmentRecursive(activity, it) }
    }

    private fun screenKeyForActivity(activity: Activity): String = when (activity) {
        is MainActivity -> "main"
        is SplashActivity -> "splash"
        is GameBoostActivity -> "gameBoost"
        is CrosshairActivity -> "crosshair"
        is OptimizerSuperActivity -> "optimizer"
        is UpdateActivity -> "update"
        is ForceUpdateActivity -> "forceUpdate"
        is SocialLinksActivity -> "social"
        is AboutActivity -> "about"
        is FeedbackActivity -> "feedback"
        is PolicyActivity -> "policy"
        else -> "activity"
    }

    private fun screenKeyForFragment(fragment: Fragment): String = when (fragment) {
        is HomeFragment -> "home"
        is GameFragment -> "game"
        is FunctionFragment -> "function"
        is SettingsFragment -> "settings"
        is InfoFragment -> "profile"
        is LoginFragment -> "login"
        else -> "fragment"
    }
}
