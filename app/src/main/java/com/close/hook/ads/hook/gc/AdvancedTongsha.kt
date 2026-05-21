package com.close.hook.ads.hook.gc

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType

class AdvancedTongsha : IXposedHookLoadPackage {

    companion object {
        // ========== 目标范围配置 ==========
        // "all" = 对所有非系统应用生效；"list" = 仅对 TARGET_SET 中的应用生效
        private const val TARGET_MODE = "all"
        private val TARGET_SET = setOf("com.lightmemory.simon")

        private fun shouldSkip(packageName: String): Boolean {
            if (packageName.startsWith("com.android.") ||
                packageName.startsWith("com.google.") ||
                packageName == "android"
            ) return true
            return when (TARGET_MODE) {
                "all" -> false
                "list" -> packageName !in TARGET_SET
                else -> true
            }
        }

        // ========== 序列化注解全库 ==========
        private val ANNOTATION_CLASSES = listOf(
            "Lcom/google/gson/annotations/SerializedName;",      // Gson
            "Lcom/squareup/moshi/Json;",                          // Moshi
            "Lcom/fasterxml/jackson/annotation/JsonProperty;",    // Jackson
            "Lcom/alibaba/fastjson/annotation/JSONField;",        // Fastjson
            "Lcom/alibaba/fastjson2/annotation/JSONField;",       // Fastjson2
            "Lcom/google/gson/annotations/Expose;",               // Gson Expose（备选）
        )

        // ========== 字段名词典（40+ 覆盖）==========
        private val VIP_KEYWORDS = listOf(
            // --- 会员/VIP ---
            "is_vip", "isVip", "vip", "vip_flag", "vipFlag",
            "is_member", "isMember", "member", "member_type", "memberType",
            "is_pro", "isPro", "pro", "premium", "is_premium", "isPremium",
            // --- 购买/订阅 ---
            "has_purchased", "hasPurchased", "purchased", "is_purchased",
            "has_bought", "hasBought", "bought",
            "is_subscribed", "isSubscribed", "subscribed", "subscription",
            "subscribe_status", "subscribeStatus",
            // --- 授权/激活 ---
            "is_activated", "isActivated", "activated", "activation",
            "is_authorized", "isAuthorized", "authorized",
            "is_licensed", "isLicensed", "licensed",
            // --- 权限/状态 ---
            "is_forever", "isForever", "forever", "is_permanent", "isPermanent",
            "user_type", "userType", "user_level", "userLevel",
            "account_type", "accountType", "role", "user_role", "userRole",
            // --- 到期时间 ---
            "expire_time", "expireTime", "expires_at", "expiresAt",
            "vip_end_time", "vipEndTime", "end_time", "endTime",
            "valid_until", "validUntil", "deadline",
            // --- 令牌/密钥 ---
            "access_token", "accessToken", "token",
            "license_key", "licenseKey", "activation_code", "activationCode",
            // --- 积分/余额 ---
            "points", "balance", "credits", "coin", "coins",
        )

        // ========== 返回值策略 ==========
        private const val RETURN_INT_VIP = 1
        private const val RETURN_LONG_FOREVER = 9999999999L
        private const val RETURN_STRING_FOREVER = "2099-12-31 23:59:59"
        private const val RETURN_STRING_PREMIUM = "premium"

        private val CLASS_BLACKLIST = listOf(
            "android.", "androidx.", "kotlin.", "kotlinx.",
            "com.android.", "org.chromium.",
            "okhttp3", "retrofit2", "com.squareup.",
            "com.facebook.", "com.tencent.mmkv",
            "org.apache."
        )
        private fun isBlacklisted(className: String): Boolean =
            CLASS_BLACKLIST.any { className.startsWith(it) }
    }

    override fun handleLoadPackage(lpp: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpp.packageName
        if (shouldSkip(packageName)) return

        System.loadLibrary("dexkit")

        val bridge = DexKitBridge.create(lpp.classLoader, true)
        if (bridge == null) {
            XposedBridge.log("[DexKit+] 策略0命中: Bridge创建失败，无法进行DexKit扫描")
            hookSharedPreferences(lpp)
            RewardSkip.handleFallback(lpp.classLoader)
            return
        }

        bridge.use {
            // ====== 策略0：直接字段名搜索（无注解兜底）======
            XposedBridge.log("[DexKit+] 策略0命中: 开始直接字段名扫描...")
            hookByFieldName(bridge, lpp)

            // ====== 策略1：全注解 + 全词典扫描 ======
            for (annotationClass in ANNOTATION_CLASSES) {
                try {
                    hookByAnnotation(bridge, lpp, annotationClass)
                } catch (_: Exception) {}
            }

            // ====== 策略2：方法名模式匹配 ======
            hookByMethodNamePattern(bridge, lpp)

            // ====== 策略3：SharedPreferences 欺骗 ======
            hookSharedPreferences(lpp)

            // ====== 策略4：免广告奖励 ======
            XposedBridge.log("[DexKit+] 策略4: 开始免广告通杀扫描...")
            RewardSkip.handle(bridge, lpp.classLoader)
        }
    }

    // ========== 策略0：直接字段名 + 方法字符串反查（双通道兜底）==========
    private fun hookByFieldName(
        bridge: DexKitBridge,
        lpp: XC_LoadPackage.LoadPackageParam
    ) {
        // 通道 A：直接按字段名搜（一次性拉所有 boolean/int 字段，内存过滤）
        try {
            XposedBridge.log("[DexKit+] 策略0-A: 开始全量字段扫描...")
            val keywordSet = VIP_KEYWORDS.map { it.lowercase() }.toSet()

            // boolean 字段
            val boolFields = bridge.findField { matcher { type = "boolean" } }
            for (fd in boolFields) {
                if (fd.name.lowercase() in keywordSet && !isBlacklisted(fd.className)) {
                    XposedBridge.log("[DexKit+] 策略0-A(bool)命中: ${fd.className}#${fd.name}")
                    hookFieldGetters(bridge, lpp, fd.className, fd.name, ReturnType.BOOL)
                }
            }

            // int 字段
            val intFields = bridge.findField { matcher { type = "int" } }
            for (fd in intFields) {
                if (fd.name.lowercase() in keywordSet && !isBlacklisted(fd.className)) {
                    XposedBridge.log("[DexKit+] 策略0-A(int)命中: ${fd.className}#${fd.name}")
                    hookFieldGetters(bridge, lpp, fd.className, fd.name, ReturnType.INT)
                }
            }
        } catch (e: Exception) {
            XposedBridge.log("[DexKit+] 策略0-A 异常: ${e.message}")
        }

        // 通道 B：方法字符串反查（对齐 VipKillerEngine L2）
        val l2Keywords = listOf(
            "isVip", "isVIP", "is_vip", "isPremium", "isPro", "isMember", "isSvip",
            "checkVip", "hasVip", "getVipLevel", "getMemberType", "getVipExpireTime",
            "VipHelper", "VipManager", "VipModel", "VipPresenter",
            "MemberHelper", "MemberManager", "MemberModel",
            "PremiumHelper", "PremiumManager"
        )
        for (kw in l2Keywords) {
            try {
                val list = bridge.findMethod {
                    matcher { addUsingString(kw, StringMatchType.Contains) }
                }
                for (md in list) {
                    if (isBlacklisted(md.className)) continue
                    val name = md.name
                    if (name == "equals" || name == "hashCode" || name == "toString" || name == "getClass") continue
                    val rt = md.returnTypeName ?: continue
                    if (rt !in setOf("boolean", "int", "long", "java.lang.Boolean", "java.lang.Integer", "java.lang.Long")) continue
                    if (md.paramTypeNames?.isNotEmpty() == true) continue

                    val m = md.getMethodInstance(lpp.classLoader) ?: continue
                    val fakeReturn: Any = when (rt) {
                        "boolean", "java.lang.Boolean" -> true
                        "int", "java.lang.Integer" -> 999999
                        "long", "java.lang.Long" -> 9999999999L
                        else -> continue
                    }
                    XposedBridge.hookMethod(m, object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Any = fakeReturn
                    })
                    XposedBridge.log("[DexKit+] 策略0-B命中: $name → $fakeReturn | class=${md.className}")
                }
            } catch (_: Exception) {}
        }
    }

    // ========== 策略1：注解定位（对齐 DexKit v2 API）==========
    private fun hookByAnnotation(
        bridge: DexKitBridge,
        lpp: XC_LoadPackage.LoadPackageParam,
        annotationClass: String
    ) {
        // 按字段类型分别搜索：boolean / int / long / String
        val typeSpecs = listOf(
            "boolean" to ReturnType.BOOL,
            "int" to ReturnType.INT,
            "long" to ReturnType.LONG,
            "java.lang.String" to ReturnType.STRING,
        )
        for ((typeName, returnType) in typeSpecs) {
            for (keyword in VIP_KEYWORDS) {
                try {
                    val fields = bridge.findField {
                        matcher {
                            type = typeName
                            annotations {
                                add {
                                    type = annotationClass
                                    elements {
                                        add {
                                            name = "value"
                                            stringValue(keyword)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    for (fd in fields) {
                        if (isBlacklisted(fd.className)) continue
                        hookFieldGetters(bridge, lpp, fd.className, fd.name, returnType)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // ========== 从字段名推导 getter 并 hook ==========
    private fun hookFieldGetters(
        bridge: DexKitBridge,
        lpp: XC_LoadPackage.LoadPackageParam,
        className: String,
        fieldName: String,
        returnType: ReturnType
    ) {
        val getterNames = getterNamesForField(fieldName)
        for (getter in getterNames) {
            try {
                val methods = bridge.findMethod {
                    matcher {
                        declaredClass = className
                        name = getter
                    }
                }
                for (md in methods) {
                    val name = md.name
                    if (name == "equals" || name == "hashCode" || name == "toString") continue
                    val m = md.getMethodInstance(lpp.classLoader) ?: continue
                    val fakeReturn: Any = when (returnType) {
                        ReturnType.BOOL -> true
                        ReturnType.INT -> RETURN_INT_VIP
                        ReturnType.LONG -> RETURN_LONG_FOREVER
                        ReturnType.STRING -> {
                            val desc = fieldName.lowercase()
                            if (listOf("time", "date", "expire", "deadline", "end").any { it in desc })
                                RETURN_STRING_FOREVER
                            else
                                RETURN_STRING_PREMIUM
                        }
                    }
                    XposedBridge.hookMethod(m, object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Any = fakeReturn
                    })
                    XposedBridge.log("[DexKit+] 策略1命中: $name → $fakeReturn | field=$fieldName | class=$className")
                }
            } catch (_: Exception) {}
        }
    }

    private fun getterNamesForField(fieldName: String): List<String> {
        val result = mutableListOf<String>()
        if (fieldName.startsWith("is") && fieldName.length > 2 && fieldName[2].isUpperCase()) {
            result.add(fieldName)
        }
        val capitalized = fieldName.replaceFirstChar { it.uppercase() }
        result.add("get$capitalized")
        val camel = fieldName.split("_").joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
        if (camel != capitalized) {
            result.add("get$camel")
            if (fieldName.startsWith("is")) {
                result.add(camel.replaceFirstChar { it.lowercase() })
            }
        }
        if (fieldName.startsWith("is_")) {
            val suffix = fieldName.removePrefix("is_")
            val suffixCamel = suffix.replaceFirstChar { it.uppercase() }
            result.add("is$suffixCamel")
            result.add("getIs$suffixCamel")
            result.add("get$suffixCamel")
        }
        return result.distinct()
    }

    // ========== 策略2：方法名模式匹配（无注解兜底）==========
    private fun hookByMethodNamePattern(
        bridge: DexKitBridge,
        lpp: XC_LoadPackage.LoadPackageParam
    ) {
        val getterPatterns = listOf(
            "isVip", "is_vip", "getIsVip", "getVip",
            "isMember", "is_member", "getIsMember", "getMember",
            "isPro", "is_pro", "getIsPro", "getPro",
            "isPremium", "is_premium", "getIsPremium", "getPremium",
            "hasPurchased", "has_purchased", "getHasPurchased",
            "isSubscribed", "is_subscribed", "getIsSubscribed",
            "isActivated", "is_activated", "getIsActivated",
        )

        for (pattern in getterPatterns) {
            try {
                bridge.findMethod {
                    matcher {
                        name = pattern
                    }
                }.forEach { md ->
                    val name = md.name
                    if (name == "equals" || name == "hashCode") return@forEach
                    val rt = md.returnTypeName
                    if (rt != "boolean" && rt != "java.lang.Boolean") return@forEach
                    if (isBlacklisted(md.className)) return@forEach
                    if (md.paramTypeNames?.isNotEmpty() == true) return@forEach
                    val m = md.getMethodInstance(lpp.classLoader) ?: return@forEach
                    XposedBridge.hookMethod(m, object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Any = true
                    })
                    XposedBridge.log("[DexKit+] 策略2命中: $name → true")
                }
            } catch (_: Exception) {}
        }
    }

    // ========== 策略3：SharedPreferences 欺骗 ==========
    private fun hookSharedPreferences(lpp: XC_LoadPackage.LoadPackageParam) {
        try {
            val spClass = XposedHelpers.findClass(
                "android.app.SharedPreferencesImpl", lpp.classLoader
            )

            // getBoolean 拦截
            XposedHelpers.findAndHookMethod(
                spClass, "getBoolean",
                String::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        val key = param.args[0] as String
                        for (kw in VIP_KEYWORDS) {
                            if (key.equals(kw, ignoreCase = true) ||
                                key.contains(kw, ignoreCase = true)
                            ) {
                                XposedBridge.log("[DexKit+] SP getBoolean: $key → true")
                                return true
                            }
                        }
                        return XposedBridge.invokeOriginalMethod(
                            param.method, param.thisObject, param.args
                        )
                    }
                }
            )

            // getInt 拦截
            XposedHelpers.findAndHookMethod(
                spClass, "getInt",
                String::class.java, Int::class.javaPrimitiveType,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        val key = param.args[0] as String
                        val intKeys = listOf("vip_level", "user_type", "member_level", "level")
                        if (intKeys.any { key.equals(it, ignoreCase = true) }) {
                            XposedBridge.log("[DexKit+] SP getInt: $key → $RETURN_INT_VIP")
                            return RETURN_INT_VIP
                        }
                        return XposedBridge.invokeOriginalMethod(
                            param.method, param.thisObject, param.args
                        )
                    }
                }
            )

            // getLong 拦截
            XposedHelpers.findAndHookMethod(
                spClass, "getLong",
                String::class.java, Long::class.javaPrimitiveType,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        val key = param.args[0] as String
                        val longKeys = listOf("expire_time", "expire", "vip_end", "deadline", "end_time")
                        if (longKeys.any { key.contains(it, ignoreCase = true) }) {
                            XposedBridge.log("[DexKit+] SP getLong: $key → 永久")
                            return RETURN_LONG_FOREVER
                        }
                        return XposedBridge.invokeOriginalMethod(
                            param.method, param.thisObject, param.args
                        )
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("[DexKit+] SP hook failed: ${e.message}")
        }
    }

    // ========== 通用 getter hook ==========
    private enum class ReturnType { BOOL, INT, LONG, STRING }
}