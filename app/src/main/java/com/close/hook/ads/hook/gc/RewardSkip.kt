package com.close.hook.ads.hook.gc

import android.content.Context
import android.content.SharedPreferences
import dalvik.system.DexFile
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.json.JSONArray
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

object RewardSkip {

    private const val TAG = "RewardSkip"
    private val Inject = AtomicBoolean(false)
    private var adParam: XC_MethodHook.MethodHookParam? = null
    private var SharedPref: SharedPreferences? = null

    fun handle(bridge: DexKitBridge, cl: ClassLoader) {}

    fun handleFallback(cl: ClassLoader) {}

    fun handleLoadPackage(lpp: Any) {
        if (Inject.getAndSet(true)) return
        try {
            val appClass = Class.forName("android.app.Application")
            val attachMethod = appClass.getDeclaredMethod("attach", Context::class.java)
            XposedBridge.hookMethod(attachMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val mContext = param.thisObject as Context
                    val mClassLoader = mContext.classLoader
                    SharedPref = mContext.getSharedPreferences("HookData", Context.MODE_PRIVATE)

                    // 启动广告 INTENT_KEY_AUTH
                    XposedHelpers.findAndHookMethod(
                        "android.content.Intent", mClassLoader, "getIntExtra",
                        String::class.java, Int::class.javaPrimitiveType,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val key = param.args[0] as String
                                val defaultValue = param.args[1] as Int
                                if ("KEY_EXTRA_AUTH" == key && defaultValue == -1) {
                                    param.result = 1
                                }
                            }
                        })

                    // 首页轮浮广告
                    XposedHelpers.findAndHookMethod(
                        "cn.kuwo.base.bean.quku.UserLabelInfo", mClassLoader,
                        "setContent", String::class.java,
                        XC_MethodReplacement.returnConstant(null))

                    // 音效
                    XposedHelpers.findAndHookMethod(
                        "cn.kuwo.peculiar.specialinfo.SpecialInfoUtil", mClassLoader,
                        "I", object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                super.afterHookedMethod(param)
                                param.result = true
                            }
                        })

                    // 激励广告核心 Hook
                    XposedHelpers.findAndHookMethod(
                        "com.tencentmusic.ad.integration.rewardvideo.TMERewardVideoAD",
                        mClassLoader, "showAD", Context::class.java,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                super.afterHookedMethod(param)
                                adParam = param
                                if (!isOk(mContext)) {
                                    startInit(mContext)
                                }
                            }
                        })

                    if (isOk(mContext)) {
                        loadRewardConfig(mContext)
                    }
                }
            })
        } catch (e: Exception) {
            XposedBridge.log("[$TAG] ${e.message}")
        }
    }

    private fun isOk(mContext: Context): Boolean {
        try {
            val raw = SharedPref?.getString("RewardConfig", "{}") ?: "{}"
            val js = JSONObject(raw)
            if (js.length() < 2) return false
            val pi = mContext.packageManager.getPackageInfo(mContext.packageName, 0)
            return pi.versionCode == js.getInt("VERSION")
        } catch (_: Throwable) {}
        return false
    }

    private fun isSpecificInterface(clazz: Class<*>, target: String): Boolean {
        return clazz.interfaces.any { it.name == target }
    }

    private fun getClassNames(mContext: Context): List<String> {
        val cls = ArrayList<String>()
        try {
            val df = DexFile(mContext.packageCodePath)
            val en = df.entries()
            while (en.hasMoreElements()) {
                cls.add(en.nextElement())
            }
        } catch (_: Throwable) {}
        return cls
    }

    private fun startInit(mContext: Context) {
        val classList = getClassNames(mContext)
        val js = JSONObject()
        val ju = JSONArray()
        try {
            js.put("Data", ju)
            val pi = mContext.packageManager.getPackageInfo(mContext.packageName, 0)
            js.put("VERSION", pi.versionCode)
        } catch (_: Throwable) {}

        for (clsName in classList) {
            try {
                val clazz = XposedHelpers.findClass(clsName, mContext.classLoader)
                if (clazz != null) {
                    if (isSpecificInterface(clazz, "com.tencentmusic.ad.integration.rewardvideo.RewardADListener")) {
                        ju.put(clazz.name)
                    }
                    setHasReward(clazz.getDeclaredMethod("onADShow"))
                    setAutoCloseAD(clazz.getDeclaredMethod("onADExpose"))
                }
            } catch (_: Throwable) {}
        }
        SharedPref?.edit()?.putString("RewardConfig", js.toString())?.apply()
    }

    private fun loadRewardConfig(mContext: Context) {
        val raw = SharedPref?.getString("RewardConfig", "[]") ?: "[]"
        val js = JSONObject(raw)
        val ju = js.getJSONArray("Data")
        for (i in 0 until ju.length()) {
            try {
                val clazz = XposedHelpers.findClass(ju.getString(i), mContext.classLoader)
                if (clazz != null) {
                    setHasReward(clazz.getDeclaredMethod("onADShow"))
                    setAutoCloseAD(clazz.getDeclaredMethod("onADExpose"))
                }
            } catch (_: Throwable) {}
        }
    }

    private fun setHasReward(method: Method) {
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                super.afterHookedMethod(param)
                val a = param.thisObject.javaClass
                try {
                    XposedHelpers.callMethod(param.thisObject, "onLandingPageReward")
                    XposedHelpers.callMethod(param.thisObject, "onExtraReward")
                    XposedHelpers.callMethod(param.thisObject, "onReward")
                    XposedBridge.log("[$TAG] ✅ 触发奖励回调")
                } catch (_: Throwable) {}
            }
        })
    }

    private fun setAutoCloseAD(method: Method) {
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                super.afterHookedMethod(param)
                adParam?.let {
                    try {
                        XposedHelpers.callMethod(it.thisObject, "closeAD")
                        XposedBridge.log("[$TAG] ✅ 自动关闭广告")
                    } catch (_: Throwable) {}
                }
            }
        })
    }
}