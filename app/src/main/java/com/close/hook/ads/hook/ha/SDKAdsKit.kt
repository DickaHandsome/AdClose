package com.close.hook.ads.hook.ha

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import com.close.hook.ads.hook.util.ContextUtil
import com.close.hook.ads.hook.util.DexKitUtil
import com.close.hook.ads.hook.util.HookUtil.findAndHookMethod
import com.close.hook.ads.hook.util.HookUtil.hookAllMethods
import com.close.hook.ads.hook.util.HookUtil.hookMethod
import org.luckypray.dexkit.result.MethodData

object SDKAdsKit {

    private const val LOG_PREFIX = "[SDKAdsKit]"

    private val packageName: String by lazy { DexKitUtil.context.packageName }

    fun hookAds() {
        ContextUtil.addOnApplicationContextInitializedCallback {
            handlePangolinInit()
            handleGdtInit()
            handleAnyThinkSDK()
            handleIQIYI()
            blockFirebaseWithString()
            blockAdsWithString()
            blockAdsWithPackageName()
        }
    }

    fun hookMethodsByStringMatch(cacheKey: String, strings: List<String>, action: (Method) -> Unit) {
        DexKitUtil.withBridge { bridge ->
            DexKitUtil.getCachedOrFindMethods(cacheKey) {
                bridge.findMethod {
                    matcher { usingStrings = strings }
                }
            }?.forEach { methodData ->
                if (isValidMethodData(methodData)) {
                    val method = methodData.getMethodInstance(DexKitUtil.context.classLoader)
                    action(method)
                    XposedBridge.log("$LOG_PREFIX Hooked method: ${methodData}")
                }
            }
        }
    }

    private fun isValidMethodData(methodData: MethodData): Boolean {
        return methodData.methodName != "<init>"
    }

    fun handlePangolinInit() {
        hookAllMethods(
            "com.bytedance.sdk.openadsdk.TTAdSdk",
            "init",
            "before",
            { param ->
                param.result = when ((param.method as Method).returnType) {
                    Void.TYPE -> null
                    java.lang.Boolean.TYPE -> false
                    else -> null
                }
            },
            DexKitUtil.context.classLoader
        )
    }

    fun handleGdtInit() {
        hookMethodsByStringMatch(
            "$packageName:handleGdtInit",
            listOf("SDK 尚未初始化，请在 Application 中调用 GDTAdSdk.initWithoutStart() 初始化")
        ) { method ->
            hookMethod(method, "before") { param ->
                param.result = false
            }
        }
    }

    fun handleAnyThinkSDK() {
        hookMethodsByStringMatch(
            "$packageName:handleAnyThinkSDK",
            listOf("anythink_sdk")
        ) { method ->
            val result = when (method.returnType) {
                Void.TYPE -> null
                java.lang.Boolean.TYPE -> false
                else -> null
            }
            hookMethod(method, "before") { param ->
                param.result = result
            }
        }
    }

    fun handleIQIYI() {
        hookMethodsByStringMatch(
            "$packageName:handleIQIYI",
            listOf("smartUpgradeResponse")
        ) { method ->
            hookMethod(method, "before") { param ->
                param.result = null
            }
        }
    }

    fun blockFirebaseWithString() {
        hookMethodsByStringMatch(
            "$packageName:blockFirebaseWithString",
            listOf("Device unlocked: initializing all Firebase APIs for app ")
        ) { method ->
            hookMethod(method, "before") { param ->
                param.result = null
            }
        }
    }

    fun blockAdsWithString() {
        hookMethodsByStringMatch(
            "$packageName:blockAdsWithString",
            listOf("Flags.initialize() was not called!")
        ) { method ->
            hookMethod(method, "before") { param ->
                param.result = true
            }
        }
    }

    private val validAdMethods = setOf(
        "loadAd", "loadAds", "load", "show", "fetchAd",
        "initSDK", "initialize", "initializeSdk"
    )

    fun blockAdsWithPackageName() {
        val adPackages = listOf(
            "com.applovin",
            "com.anythink",
            "com.facebook.ads",
            "com.fyber.inneractive.sdk",
            "com.google.android.gms.ads",
            "com.mbridge.msdk",
            "com.inmobi.ads",
            "com.miniclip.ads",
            "com.smaato.sdk",
            "com.tp.adx",
            "com.tradplus.ads",
            "com.unity3d.services",
            "com.unity3d.ads",
            "com.vungle.warren",
            "com.bytedance.sdk"
        )

        DexKitUtil.withBridge { bridge ->
            DexKitUtil.getCachedOrFindMethods("$packageName:blockAdsWithPackageName") {
                bridge.findMethod {
                    searchPackages(adPackages)
                    matcher {
                        modifiers = Modifier.PUBLIC
                        returnType(Void.TYPE)
                    }
                }?.filter(::isValidAdMethod)
            }?.forEach {
                val method = it.getMethodInstance(DexKitUtil.context.classLoader)
                XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING)
                XposedBridge.log("$LOG_PREFIX Hooked method: ${it}")
            }
        }
    }

    private fun isValidAdMethod(methodData: MethodData): Boolean {
        return !Modifier.isAbstract(methodData.modifiers) && methodData.methodName in validAdMethods
    }
}
