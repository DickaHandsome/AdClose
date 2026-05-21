package com.close.hook.ads.hook.ha

import android.util.Log
import com.close.hook.ads.preference.HookPrefs
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType

import java.util.concurrent.ConcurrentHashMap

/**
 * DexKit 通杀会员引擎 — 6 层递进扫描
 *
 * L2: 方法体内字符串引用反查 — addUsingString + Contains
 * L3: 方法名模式直接匹配 — bridge.findMethod { name = pattern }
 * L4: 社区共享预置
 * L5: 注解字段反查 — @SerializedName/@JSONField 等注解值匹配
 * L6: 字段名过滤扫描 — 全量搜字段 → 内存 VIP 关键词过滤 → 推导 getter
 *     这是最精准的策略，不依赖方法体字符串、不依赖方法名，
 *     只依赖字段名（混淆后往往保留更多语义）
 *
 * Hook 分两个通道：
 *   hook()        — 即时 Hook（需在目标应用 Xposed 进程中）
 *   hookDeferred() — 延迟 Hook（保存到 HookPrefs，目标应用加载时由 HookLogic 自动执行）
 */
class VipKillerEngine(
    private val classLoader: ClassLoader,
    private val bridge: DexKitBridge
) : AutoCloseable {

    // ═══════════════ 延迟 Hook 数据模型 ═══════════════
    @Serializable
    data class PendingHook(
        val className: String,
        val methodName: String,
        val returnType: String,
        val paramsCount: Int,
        val hookType: String = "replace",
        val enabled: Boolean = true,
        val replacementValue: String = "",
        val description: String = "",
        val hookMethodTypeName: String = "",
        val fieldName: String = "",
        val fieldValue: String = "",
        val searchStrings: String = "",
        val paramTypes: String = "",
        val hookPoint: String = "before"
    )

    companion object {
        private const val TAG = "VipKiller"
        private const val FILE_PREFIX_PENDING = "vipkiller_pending_"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        private val L2_KEYWORDS = listOf(
            // --- 会员/VIP（常见命名）---
            "isVip", "isVIP", "is_vip", "isPremium", "isPro", "isMember", "isSvip",
            "is_member", "member", "is_premium", "is_pro", "is_svip",
            "checkVip", "hasVip", "getVipLevel", "getMemberType", "getVipExpireTime",
            "VipHelper", "VipManager", "VipModel", "VipPresenter",
            "MemberHelper", "MemberManager", "MemberModel",
            "PremiumHelper", "PremiumManager",
            // --- 购买/订阅（很多 App 用这些）---
            "isSubscribed", "is_subscribed", "subscribed", "subscription",
            "subscribeStatus", "subscribe_status",
            "hasPurchased", "has_purchased", "purchased", "is_purchased",
            "hasBought", "has_bought", "bought",
            // --- 授权/激活 ---
            "isActivated", "is_activated", "activated",
            "isAuthorized", "is_authorized", "authorized",
            "isLicensed", "is_licensed", "licensed",
            // --- 永久/到期 ---
            "isForever", "is_forever", "forever", "isPermanent", "is_permanent",
            "expireTime", "expire_time", "expiresAt", "expires_at",
            "vipEndTime", "vip_end_time", "endTime", "end_time",
            // --- 用户等级/类型（int 型 VIP 常用）---
            "userType", "user_type", "userLevel", "user_level",
            "accountType", "account_type", "role", "userRole", "user_role",
            "memberLevel", "member_level", "vipLevel", "vip_level",
            // --- 令牌/激活码 ---
            "accessToken", "access_token", "licenseKey", "license_key",
            "activationCode", "activation_code",
            // --- 积分/余额 ---
            "points", "balance", "credits", "coin", "coins",
            // --- 下划线混合风格 ---
            "getIs_vip", "setIs_vip", "getVip_level", "setVip_level",
            "getVip_validity", "setVip_validity", "invited_reward_vip",
            "vip_level", "vip_validity", "is_vip",
            // --- 登录相关 ---
            "isLogin", "is_login", "getLogin", "getLogin_type",
            "getLoginType", "checkLogin", "getUserStatus", "getUserState",
            "isLoggedIn", "isGuest", "isVisitor", "isOnline",
            // --- 广告相关 ---
            "getSdk_ad_id", "sdk_ad_id", "ad_id",
            "getAdId", "get_ad_id", "getAdvertisingId",
            "isAdFree", "is_ad_free", "hasAds", "has_ads"
        )

        // 字段名关键词（用于 scanFieldGetters 内存过滤）
        private val FIELD_KEYWORDS_SET = (L2_KEYWORDS + listOf(
            "vip", "svip", "pro", "premium", "member", "subscribed", "purchased",
            "activated", "authorized", "licensed", "forever", "permanent", "trial",
            "expired", "subscribed", "user_type", "userType", "account_type",
            "vip_flag", "vip_flag", "is_vip", "isVip"
        )).flatMap { listOf(it, it.lowercase(), it.replace("_", "")) }.toSet()
        private val CLASS_BLACKLIST = listOf(
            "android.", "androidx.", "kotlin.", "kotlinx.",
            "com.android.", "org.chromium.",
            "okhttp3", "retrofit2", "com.squareup.",
            "com.facebook.", "com.tencent.mmkv",
            "org.apache."
        )
        private fun isBlacklisted(className: String): Boolean =
            CLASS_BLACKLIST.any { className.startsWith(it) }

        // L3 方法名模式：直接按方法名匹配（不依赖方法体字符串引用）
        private val L3_METHOD_PATTERNS = listOf(
            // --- 驼峰: isVip / getIsVip ---
            "isVip", "is_vip", "getIsVip", "getVip",
            "isMember", "is_member", "getIsMember", "getMember",
            "isPro", "is_pro", "getIsPro", "getPro",
            "isPremium", "is_premium", "getIsPremium", "getPremium",
            "isSvip", "is_svip", "getIsSvip", "getSvip",
            "hasPurchased", "has_purchased", "getHasPurchased",
            "isSubscribed", "is_subscribed", "getIsSubscribed", "getSubscribed",
            "isActivated", "is_activated", "getIsActivated", "getActivated",
            "isAuthorized", "is_authorized", "getIsAuthorized", "getAuthorized",
            "isForever", "is_forever", "getIsForever", "getForever",
            "isPermanent", "is_permanent", "getIsPermanent", "getPermanent",
            "isExpired", "is_expired", "getIsExpired", "getExpired",
            "isTrial", "is_trial", "getIsTrial", "getTrial",
            "checkVip", "checkPremium", "checkMember",
            "getVipLevel", "getVipType", "getUserLevel", "getUserType",
            "getMemberType", "getMemberLevel",
            "getAccountType", "getUserRole",
            "getVipExpireTime", "getExpireTime", "getExpiresAt",
            // --- 登录相关 ---
            "isLogin", "is_login", "getIsLogin", "getLogin",
            "isLoggedIn", "is_logged_in", "getIsLoggedIn", "getLoggedIn",
            "isLogined", "is_logined", "getIsLogined",
            "hasLogin", "has_login", "getHasLogin",
            "checkLogin", "check_login",
            "getLoginType", "getLogin_type", "getLoginStatus", "getLogin_status",
            "getUserStatus", "getUser_status", "getUserState", "getUser_state",
            "isGuest", "is_guest", "getIsGuest",
            "isVisitor", "is_visitor", "getIsVisitor",
            "isOnline", "is_online", "getIsOnline",
            // --- 下划线风格 ---
            "getIs_vip", "setIs_vip",
            "getVip_level", "setVip_level",
            "getVip_validity", "setVip_validity",
            "getInvited_reward_vip", "setInvited_reward_vip",
            "is_purchased", "getIs_purchased",
            "is_forever", "getIs_forever",
            "is_trial", "getIs_trial",
            "is_login", "getIs_login", "setIs_login",
            "vip_level", "vip_validity",
            "getVip_type", "setVip_type",
            "getUser_type", "setUser_type",
            "getUser_level", "setUser_level",
            "getMember_type", "setMember_type",
            "getAccount_type", "setAccount_type",
            "getLogin_type", "setLogin_type",
            "expire_time", "getExpire_time", "setExpire_time",
            // 纯 getter/setter 前缀模式也覆盖
            "getIs_", "getVip", "getUser", "getMember", "getLogin",
            // --- 广告相关方法名 ---
"getSdk_ad_id", "sdk_ad_id",
            "getAdId", "get_ad_id", "setAdId", "set_ad_id",
            "getAdvertisingId", "setAdvertisingId",
            "isAdFree", "is_ad_free", "getIsAdFree",
            "hasAds", "has_ads", "getHasAds",
            "sdk_ad_id",
            // --- 下载/播放权限（酷我等音乐App核心拦截点）---
            "isCanDownload", "isCanOnlinePlay", "isCanPlay",
            "isPayCanDownload", "isPayCanPlay",
            "isDownloadFree", "isDownloadVip", "isDownLoadVip",
            "isListenVip", "isUserIsVip", "isUserPay", "isUserPayInt",
            "isPayDownload", "isFreeSongOpen", "isPaySongOpen",
            "isLimitFree", "isSVIPUnlocked", "isSaleQuality",
            "isMusicpay", "isSongBuy", "isSongPay",
            "isPay", "isPlayable", "checkPay", "checkDownload",
            "isTrial", "isVipSongOpen", "isVipShow",
            "isHasMusicpay", "isPlayFree", "isPayQuality"
        )

        // 全库序列化注解（对齐 AdvancedTongsha）
        private val ANNOTATION_CLASSES = listOf(
            "Lcom/google/gson/annotations/SerializedName;",      // Gson
            "Lcom/squareup/moshi/Json;",                          // Moshi
            "Lcom/fasterxml/jackson/annotation/JsonProperty;",    // Jackson
            "Lcom/alibaba/fastjson/annotation/JSONField;",        // Fastjson
            "Lcom/alibaba/fastjson2/annotation/JSONField;",       // Fastjson2
            "Lcom/google/gson/annotations/Expose;",               // Gson Expose（备选）
        )

        private val VIP_ANNOTATION_VALUES = listOf(
            // --- 会员/VIP ---
            "isVip", "is_vip", "vip", "VIP", "isVIP",
            "isPremium", "premium", "isPremiumUser",
            "is_member", "isMember",
            "isSvip", "is_svip", "svip",
            "isPro", "is_pro", "pro",
            // --- 购买/订阅 ---
            "has_purchased", "hasPurchased", "purchased",
            "is_subscribed", "isSubscribed", "subscribed",
            // --- 授权/激活 ---
            "is_activated", "isActivated", "activated",
            "is_authorized", "isAuthorized", "authorized",
            // --- 标志/状态 ---
            "vip_flag", "vipFlag", "is_forever", "isForever",
            "user_type", "userType", "user_level", "userLevel",
            "member_type", "memberType",
            // --- 到期 ---
            "expire_time", "expireTime", "expires_at", "expiresAt",
        )

        // 内置社区共享规则 — 知名 App 的已验证 Hook 方案
        // 格式：app=应用名, className=完整类名, methodName=方法名, returnType=返回类型, returnValue=替换值
        private val BUILTIN_SHARED_PRESETS: List<SharedPreset> by lazy {
            listOf(
                // ── 爱奇艺 ──
                SharedPreset("爱奇艺", "com.iqiyi.videoplayer.playerbase.account.AccountManager", "isVipOrQinKa", "boolean", true),
                SharedPreset("爱奇艺", "com.qiyi.videoapi.business.b", "a", "boolean", true),
                // ── 优酷 ──
                SharedPreset("优酷", "com.youku.user.passport.model.UserInfo", "isVip", "boolean", true),
                // ── 哔哩哔哩 ──
                SharedPreset("哔哩哔哩", "tv.danmaku.bili.ui.video.album.AlbumUserInfo", "isVip", "boolean", true),
                // ── 腾讯视频 ──
                SharedPreset("腾讯视频", "com.tencent.qqlive.modules.vb.loginbusiness.VBLoginBusinessService", "isVip", "boolean", true),
                // ── 芒果TV ──
                SharedPreset("芒果TV", "com.hunantv.imgo.entity.UserInfo", "isVip", "boolean", true),
                // ── 网易云音乐 ──
                SharedPreset("网易云音乐", "com.netease.cloudmusic.music.user.User", "isVip", "boolean", true),
                // ── QQ音乐 ──
                SharedPreset("QQ音乐", "com.tencent.qqmusic.business.user.UserInfo", "isVip", "boolean", true),
                // ── 酷狗音乐 ──
                SharedPreset("酷狗音乐", "com.kugou.common.user.UserInfo", "isVip", "boolean", true),
                // ── 酷我音乐 ──
                SharedPreset("酷我音乐", "cn.kuwo.base.user.UserInfo", "isVip", "boolean", true),
                // ── 喜马拉雅 ──
                SharedPreset("喜马拉雅", "com.ximalaya.ting.android.model.UserInfo", "isVip", "boolean", true),
                // ── 知乎 ──
                SharedPreset("知乎", "com.zhihu.android.app.model.UserInfo", "isVip", "boolean", true),
                // ── WPS ──
                SharedPreset("WPS", "cn.wps.moffice.entering.login.LoginUserInfo", "isVip", "boolean", true),
                // ── 百度网盘 ──
                SharedPreset("百度网盘", "com.baidu.netdisk.account.model.UserInfo", "isVip", "boolean", true),
                // ── 迅雷 ──
                SharedPreset("迅雷", "com.xunlei.downloadlib.bean.UserInfo", "isVip", "boolean", true),
                // ── 美图秀秀 ──
                SharedPreset("美图秀秀", "com.meitu.meiyancamera.bean.UserInfo", "isVip", "boolean", true),
                // ── 剪映 ──
                SharedPreset("剪映", "com.lemon.lv.creation.model.UserInfo", "isVip", "boolean", true),
            )
        }
        fun isXposedAvailable(): Boolean = try {
            Class.forName("de.robv.android.xposed.XposedHelpers")
            true
        } catch (_: ClassNotFoundException) {
            false
        }

        fun loadPendingHooks(targetPackage: String): List<PendingHook> {
            val raw = HookPrefs.getString("vipkiller_pending_$targetPackage", null)
            if (raw.isNullOrBlank()) {
                Log.w(TAG, "loadPendingHooks: no pending hooks for $targetPackage")
                return emptyList()
            }
            Log.d(TAG, "loadPendingHooks: read ${raw.length} chars for $targetPackage")
            return try {
                json.decodeFromString<List<PendingHook>>(raw)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse pending hooks: ${e.message}")
                emptyList()
            }
        }

        private fun savePendingHooks(targetPackage: String, hooks: List<PendingHook>) {
            val content = json.encodeToString(hooks)
            HookPrefs.setString("vipkiller_pending_$targetPackage", content)
        }

        fun updatePendingHook(targetPackage: String, updated: PendingHook) {
            val hooks = loadPendingHooks(targetPackage).toMutableList()
            val idx = hooks.indexOfFirst { it.className == updated.className && it.methodName == updated.methodName }
            if (idx >= 0) {
                hooks[idx] = updated
            } else {
                hooks.add(updated)
            }
            savePendingHooks(targetPackage, hooks)
        }

        fun removePendingHook(targetPackage: String, hook: PendingHook) {
            val hooks = loadPendingHooks(targetPackage).toMutableList()
            hooks.removeAll { it.className == hook.className && it.methodName == hook.methodName }
            savePendingHooks(targetPackage, hooks)
        }

        fun togglePendingHook(targetPackage: String, hook: PendingHook, enabled: Boolean) {
            val updated = hook.copy(enabled = enabled)
            updatePendingHook(targetPackage, updated)
        }

        fun toggleAllPendingHooks(targetPackage: String, enabled: Boolean) {
            val hooks = loadPendingHooks(targetPackage).map { it.copy(enabled = enabled) }
            savePendingHooks(targetPackage, hooks)
        }

        fun clearPendingHooks(targetPackage: String) {
            savePendingHooks(targetPackage, emptyList())
        }
    }

    private val hookedKeys = ConcurrentHashMap.newKeySet<String>()

    data class VipCandidate(
        val className: String,
        val methodName: String,
        val returnType: String,
        val confidence: Confidence,
        val source: String,
        val paramsCount: Int = 0,
        @Volatile var isHooked: Boolean = false,
        @Volatile var hookMessage: String = ""
    )

    enum class Confidence(val label: String, val color: Long) {
        HIGH("高", 0xFF00E676),
        MEDIUM("中", 0xFFFFAB00),
        LOW("低", 0xFF888888),
        SHARED("共享", 0xFF448AFF)
    }

    data class SharedPreset(
        val app: String,
        val className: String,
        val methodName: String,
        val returnType: String,
        val returnValue: Any
    )

    data class MethodSignature(
        val className: String, val methodName: String,
        val returnType: String, val paramsCount: Int
    )
    data class HookResult(val success: Boolean, val message: String)

    /** DexKit 查询：双通道搜索 (方法名精确 + 字符串引用) */
    fun searchMethods(keyword: String, maxResults: Int = 20): List<MethodSignature> {
        val result = mutableListOf<MethodSignature>()
        val seen = mutableSetOf<String>()
        try {
            // 通道 A：直接按方法名搜（最精准）
            for (md in bridge.findMethod { matcher { name = keyword } }) {
                val key = "${md.className}#${md.name}"
                if (key in seen) continue
                val rt = md.returnTypeName
                if (rt !in setOf("boolean", "int", "long", "float", "double",
                        "java.lang.String", "String", "java.lang.Boolean",
                        "java.lang.Integer", "java.lang.Long")) continue
                if (md.name in setOf("equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait")) continue
                seen += key
                result += MethodSignature(md.className, md.name, rt, md.paramTypeNames.size)
                if (result.size >= maxResults) break
            }
        } catch (_: Exception) {}
        if (result.size < maxResults) {
            try {
                // 通道 B：方法体内字符串引用反查（兜底）
                for (md in bridge.findMethod { matcher { addUsingString(keyword, StringMatchType.Contains) } }) {
                    val key = "${md.className}#${md.name}"
                    if (key in seen) continue
                    val rt = md.returnTypeName
                    if (rt !in setOf("boolean", "int", "long", "float", "double",
                            "java.lang.String", "String", "java.lang.Boolean",
                            "java.lang.Integer", "java.lang.Long")) continue
                    if (md.name in setOf("equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait")) continue
                    seen += key
                    result += MethodSignature(md.className, md.name, rt, md.paramTypeNames.size)
                    if (result.size >= maxResults) break
                }
            } catch (_: Exception) {}
        }
        return result
    }

    fun scanAll(
        enableL2: Boolean = true,
        enableL3: Boolean = true,
        enableL4: Boolean = false, enableL5: Boolean = true,
        sharedPresets: List<SharedPreset> = emptyList()
    ): List<VipCandidate> {
        val merged = mutableListOf<VipCandidate>()
        if (enableL2) merged += scanL2()
        if (enableL3) merged += scanL3()
        if (enableL4) merged += scanL4(sharedPresets)
        if (enableL5) merged += scanL5()
        return merged.distinctBy { "${it.className}#${it.methodName}" }
            .sortedByDescending { it.confidence.ordinal }
    }

    internal fun scanL2(onProgress: ((Int, Int) -> Unit)? = null): List<VipCandidate> {
        val result = mutableListOf<VipCandidate>()
        val total = L2_KEYWORDS.size
        for ((i, kw) in L2_KEYWORDS.withIndex()) {
            try {
                val list = bridge.findMethod { matcher { addUsingString(kw, StringMatchType.Contains) } }
                for (md in list) {
                    val rt = md.returnTypeName
                    if (rt !in setOf("boolean", "java.lang.Boolean", "int", "java.lang.Integer",
                            "long", "java.lang.Long", "float", "java.lang.Float",
                            "double", "java.lang.Double",
                            "java.lang.String", "String")) continue
                    if (isBlacklisted(md.className)) continue
                    if (md.name in setOf("equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait")) continue
                    val paramCount = md.paramTypeNames.size
                    val confidence = if (paramCount == 0) Confidence.HIGH else Confidence.MEDIUM
                    result += VipCandidate(className = md.className, methodName = md.name, returnType = rt, confidence = confidence, source = "L2-模糊:$kw", paramsCount = paramCount)
                }
            } catch (e: Exception) { Log.w(TAG, "L2 '$kw': ${e.message}") }
            onProgress?.invoke(i + 1, total)
        }
        return result.distinctBy { "${it.className}#${it.methodName}" }
    }

    internal fun scanL3(onProgress: ((Int, Int) -> Unit)? = null): List<VipCandidate> {
        val result = mutableListOf<VipCandidate>()
        val total = L3_METHOD_PATTERNS.size
        for ((i, pattern) in L3_METHOD_PATTERNS.withIndex()) {
            try {
                // 通道 A：直接按方法名搜（不依赖字符串引用）
                val byName = bridge.findMethod { matcher { name = pattern } }
                for (md in byName) {
                    val rt = md.returnTypeName
                    if (rt !in setOf("boolean", "java.lang.Boolean", "int", "java.lang.Integer",
                            "long", "java.lang.Long", "float", "java.lang.Float",
                            "double", "java.lang.Double",
                            "java.lang.String", "String")) continue
                    if (isBlacklisted(md.className)) continue
                    if (md.name in setOf("equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait")) continue
                    val paramCount = md.paramTypeNames.size
                    result += VipCandidate(className = md.className, methodName = md.name, returnType = rt,
                        confidence = Confidence.HIGH, source = "L3-方法名:$pattern", paramsCount = paramCount)
                }
                // 通道 B：字符串引用反查（搜方法体内引用了该方法名关键词的方法）
                val byString = bridge.findMethod { matcher { addUsingString(pattern, StringMatchType.Contains) } }
                for (md in byString) {
                    val rt = md.returnTypeName
                    if (rt !in setOf("boolean", "java.lang.Boolean", "int", "java.lang.Integer",
                            "long", "java.lang.Long", "float", "java.lang.Float",
                            "double", "java.lang.Double",
                            "java.lang.String", "String")) continue
                    if (isBlacklisted(md.className)) continue
                    if (md.name in setOf("equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait")) continue
                    val paramCount = md.paramTypeNames.size
                    result += VipCandidate(className = md.className, methodName = md.name, returnType = rt,
                        confidence = Confidence.MEDIUM, source = "L3-字符串:$pattern", paramsCount = paramCount)
                }
            } catch (e: Exception) { Log.w(TAG, "L3 '$pattern': ${e.message}") }
            onProgress?.invoke(i + 1, total)
        }
        return result.distinctBy { "${it.className}#${it.methodName}" }
    }

    /** L4：社区共享规则 — 直接注入已验证的 Hook 方案，置信度 SHARED。
     * 数据来源：内置预设 + 可选云端/本地 JSON 加载。
     * @param presets 外部共享规则集，与内置规则合并后注入 */
    internal fun scanL4(presets: List<SharedPreset> = emptyList()): List<VipCandidate> {
        val all = (BUILTIN_SHARED_PRESETS + presets).distinctBy { "${it.className}#${it.methodName}" }
        return all.mapNotNull { preset ->
            try {
                // 在当前作用域 APK 中验证该类名+方法名是否真实存在
                val found = bridge.findMethod {
                    matcher { declaredClass = preset.className; name = preset.methodName }
                }
                if (found.isNotEmpty())
                    VipCandidate(preset.className, preset.methodName, preset.returnType,
                        Confidence.SHARED, "L4-共享:${preset.app}")
                else null
            } catch (_: Exception) { null }
        }
    }

    internal fun scanL5(onProgress: ((Int, Int) -> Unit)? = null): List<VipCandidate> {
        val result = mutableListOf<VipCandidate>()
        val typeSpecs = listOf("boolean", "int", "long", "java.lang.String")
        val total = VIP_ANNOTATION_VALUES.size
        var done = 0
        for (annotValue in VIP_ANNOTATION_VALUES) {
            for (annotationClass in ANNOTATION_CLASSES) {
                for (typeName in typeSpecs) {
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
                                                stringValue(annotValue)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        for (fd in fields) {
                            if (isBlacklisted(fd.className)) continue
                            val getterCandidates = getterNamesForField(fd.name)
                            for (gc in getterCandidates) {
                                try {
                                    val methods = bridge.findMethod {
                                        matcher {
                                            declaredClass = fd.className
                                            name = gc
                                        }
                                    }
                                    for (md in methods) {
                                        val rt = md.returnTypeName
                                        if (rt !in setOf("boolean", "int", "long", "float", "double",
                                                "java.lang.Boolean", "java.lang.Integer", "java.lang.Long",
                                                "java.lang.String", "String")) continue
                                        result += VipCandidate(md.className, md.name, rt,
                                            Confidence.HIGH, "L5-字段:${fd.name}($typeName)", md.paramTypeNames.size)
                                    }
                                } catch (_: Exception) {}
                            }
                            // 如果没搜到 getter，至少把字段名作为一个候选项
                            if (getterCandidates.isEmpty() || result.none { it.className == fd.className && it.source.startsWith("L5-字段:${fd.name}") }) {
                                result += VipCandidate(fd.className, fd.name, 
                                    when (typeName) { "boolean" -> "boolean"; "int" -> "int"; "long" -> "long"; else -> "java.lang.String" },
                                    Confidence.MEDIUM, "L5-字段:${fd.name}(反射·$typeName)", 0)
                            }
                        }
                    } catch (e: Exception) { Log.w(TAG, "L5 field '$annotValue' $typeName: ${e.message}") }
                }
            }
            done++
            onProgress?.invoke(done, total)
        }
        return result.distinctBy { "${it.className}#${it.methodName}" }
    }

    /**
     * 字段名过滤扫描：先全量搜 boolean/int/long 字段，内存过滤字段名含 VIP 关键词的，
     * 然后推导 getter。这是最精准的策略——不依赖方法体字符串、不依赖方法名，
     * 只依赖字段名（混淆后往往保留更多语义）。
     */
    fun scanFieldGetters(maxResults: Int = 200, onProgress: ((Int, Int) -> Unit)? = null): List<VipCandidate> {
        val result = mutableListOf<VipCandidate>()
        val typeList = listOf("boolean" to "boolean", "int" to "int", "long" to "long")
        var processed = 0
        var total = 0
        for ((typeName, label) in typeList) {
            try {
                val fields = bridge.findField { matcher { type = typeName } }
                total += fields.size
            } catch (_: Exception) {}
        }
        if (total == 0) total = 1
        for ((typeName, label) in typeList) {
            try {
                val fields = bridge.findField { matcher { type = typeName } }
                for (fd in fields) {
                    processed++
                    if (isBlacklisted(fd.className)) continue
                    val fieldNameLower = fd.name.lowercase()
                    val fieldNameNoUnderscore = fd.name.replace("_", "")
                    if (!FIELD_KEYWORDS_SET.any { kw ->
                            fieldNameLower.contains(kw) || fieldNameNoUnderscore.contains(kw)
                        }) continue
                    val getters = getterNamesForField(fd.name)
                    var found = false
                    for (gc in getters) {
                        try {
                            val methods = bridge.findMethod {
                                matcher {
                                    declaredClass = fd.className
                                    name = gc
                                }
                            }
                            for (md in methods) {
                                if (md.name in setOf("equals", "hashCode", "toString", "getClass")) continue
                                result += VipCandidate(
                                    className = md.className,
                                    methodName = md.name,
                                    returnType = label,
                                    confidence = Confidence.HIGH,
                                    source = "字段-${fd.name}",
                                    paramsCount = md.paramTypeNames.size
                                )
                                found = true
                                if (result.size >= maxResults) {
                                    onProgress?.invoke(processed, total)
                                    return result
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    if (!found) {
                        result += VipCandidate(
                            className = fd.className,
                            methodName = fd.name,
                            returnType = label,
                            confidence = Confidence.MEDIUM,
                            source = "字段-${fd.name}(无getter)",
                            paramsCount = 0
                        )
                        if (result.size >= maxResults) {
                            onProgress?.invoke(processed, total)
                            return result
                        }
                    }
                    if (processed % 50 == 0) onProgress?.invoke(processed, total)
                }
            } catch (e: Exception) { Log.w(TAG, "scanFieldGetters $typeName: ${e.message}") }
        }
        onProgress?.invoke(total, total)
        return result
    }

    private fun getterNamesForField(fieldName: String): List<String> {
        // isVip → ["isVip"]; vip → ["isVip", "getVip"]; is_vip → ["isVip", "getIsVip"]
        val result = mutableListOf<String>()
        // 如果字段名以 "is" 开头，直接作为 getter
        if (fieldName.startsWith("is") && fieldName.length > 2 && fieldName[2].isUpperCase()) {
            result.add(fieldName)
        }
        // 构造 getXxx 形式
        val capitalized = fieldName.replaceFirstChar { it.uppercase() }
        result.add("get$capitalized")
        // 去掉下划线的驼峰形式
        val camel = fieldName.split("_").joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
        if (camel != capitalized) {
            result.add("get$camel")
            if (fieldName.startsWith("is")) {
                result.add(camel.replaceFirstChar { it.lowercase() })
            }
        }
        // 去掉 is_ 前缀的变体：is_vip → isVip, getIsVip, getVip
        if (fieldName.startsWith("is_")) {
            val suffix = fieldName.removePrefix("is_")
            val suffixCamel = suffix.replaceFirstChar { it.uppercase() }
            result.add("is$suffixCamel")
            result.add("getIs$suffixCamel")
            result.add("get$suffixCamel")
        }
        return result.distinct()
    }

    /**
     * 根据方法名语义智能推断 int 类型返回值。
     *
     * 安全优先策略：只有明确是"等级比较"或"大值类"才用非1值，
     * 其余全部返回 1（兼容最常见的会员判断逻辑，如 vipType==1 / role>=1 等）。
     *
     * 规则：
     * ① level/grade/rank/role → 999（App 用 if(vipLevel>0) 等 >= 比较）
     * ② status/state/flag/mode → 999（位掩码/枚举递增，大值覆盖所有状态）
     * ③ expire/end/remain/count/times/balance/point/credit/coin → 999999（时间/数量）
     * ④ 其他一切 → 1
     */
    fun inferIntValue(methodName: String): Int {
        val l = methodName.lowercase()
        return when {
            // ── 等级/评级/角色（用 >= 比较）──
            l.matches(Regex(".*(level|grade|rank|role)")) -> 999
            // ── 状态/标志（位掩码/枚举递增）──
            l.matches(Regex(".*(status|state|flag|mode)")) -> 999
            // ── 到期/剩余/次数/余额 → 极大值 ──
            l.contains("expire") || l.contains("end") || l.contains("remain")
                || l.contains("count") || l.contains("times") || l.contains("balance")
                || l.contains("point") || l.contains("credit") || l.contains("coin") -> 999999
            // ── 其他全部 → 1 ──
            else -> 1
        }
    }

    private fun inferReplacementValue(returnType: String, methodName: String = ""): String {
        val rt = returnType.replace("java.lang.", "").lowercase()
        return when {
            rt in setOf("boolean") -> "true"
            rt in setOf("long") -> "3495751810"
            rt in setOf("int", "integer") -> inferIntValue(methodName).toString()
            rt in setOf("float") -> "999999.0"
            rt in setOf("double") -> "999999.0"
            rt in setOf("string") -> "premium"
            else -> "true"
        }
    }


    fun hook(candidate: VipCandidate): HookResult {
        if (!isXposedAvailable()) return HookResult(false, "⚠️需目标应用进程（已暂存，下次启动目标应用时自动生效）")
        return try {
            val key = "${candidate.className}#${candidate.methodName}"
            if (key in hookedKeys) return HookResult(false, "已Hook")
            val clazz = XposedHelpers.findClassIfExists(candidate.className, classLoader) ?: return HookResult(false, "Class not found")
            val method = clazz.declaredMethods.firstOrNull { m -> m.name == candidate.methodName && m.parameterTypes.size == candidate.paramsCount } ?: return HookResult(false, "Method not found")
            val replacement = when (candidate.returnType.replace("java.lang.", "")) {
                "Boolean", "boolean" -> XC_MethodReplacement.returnConstant(true)
                "Integer", "int" -> XC_MethodReplacement.returnConstant(inferIntValue(candidate.methodName))
                "Long", "long" -> XC_MethodReplacement.returnConstant(3495751810L)
                "Float", "float" -> XC_MethodReplacement.returnConstant(999999.0f)
                "Double", "double" -> XC_MethodReplacement.returnConstant(999999.0)
                "String" -> XC_MethodReplacement.returnConstant("premium")
                else -> return HookResult(false, "Unsupported: ${candidate.returnType}")
            }
            XposedBridge.hookMethod(method, replacement)
            hookedKeys += key; candidate.isHooked = true; candidate.hookMessage = "success"
            HookResult(true, "OK")
        } catch (e: Throwable) { Log.e(TAG, "Hook failed: ${candidate.className}#${candidate.methodName}", e); HookResult(false, e.message ?: "unknown") }
    }

    fun hookDeferred(candidate: VipCandidate, targetPackage: String) {
        val existing = loadPendingHooks(targetPackage).toMutableList()
        val pending = PendingHook(
            className = candidate.className,
            methodName = candidate.methodName,
            returnType = candidate.returnType,
            paramsCount = candidate.paramsCount,
            replacementValue = inferReplacementValue(candidate.returnType, candidate.methodName)
        )
        if (existing.none { it.className == pending.className && it.methodName == pending.methodName }) {
            existing.add(pending)
            savePendingHooks(targetPackage, existing)
        }
    }

    fun hookAll(candidates: List<VipCandidate>): Int = candidates.count { hook(it).success }
    fun hookAllDeferred(candidates: List<VipCandidate>, targetPackage: String): Int = candidates.count { hookDeferred(it, targetPackage); true }

    /**
     * 诊断：无过滤查看 DexKit 从这个 APK 找出了什么。
     */
    fun diagnose(): String {
        val sb = StringBuilder("=== DexKit 诊断 ===\n")
        try { sb.appendLine("boolean字段: ${bridge.findField { matcher { type = "boolean" } }.size}") } catch (e: Exception) { sb.appendLine("bf: ${e.message}") }
        try { sb.appendLine("int字段: ${bridge.findField { matcher { type = "int" } }.size}") } catch (e: Exception) { sb.appendLine("if: ${e.message}") }
        try {
            val m = bridge.findMethod { matcher { returnType("boolean"); paramCount = 0 } }
            sb.appendLine("boolean无参方法: ${m.size}")
            m.filter { val n = it.name.lowercase(); n.contains("vip") || n.contains("member") || n.contains("premium") || n.contains("sub") || n.contains("purch") }
                .take(20).forEach { sb.appendLine("  ${it.className}#${it.name}") }
        } catch (e: Exception) { sb.appendLine("bm: ${e.message}") }
        try { val t = bridge.findMethod { matcher { addUsingString("true", StringMatchType.Contains) } }; sb.appendLine("'true'引用: ${t.size}") } catch (e: Exception) { sb.appendLine("t: ${e.message}") }
        try { val t = bridge.findMethod { matcher { addUsingString("isVip", StringMatchType.Contains) } }; sb.appendLine("'isVip'引用: ${t.size}"); t.take(5).forEach { sb.appendLine("  ${it.className}#${it.name}") } } catch (e: Exception) { sb.appendLine("iv: ${e.message}") }
        try { val t = bridge.findMethod { matcher { name = "isVip" } }; sb.appendLine("方法名isVip: ${t.size}") } catch (e: Exception) { sb.appendLine("mn: ${e.message}") }
        Log.i(TAG, sb.toString()); return sb.toString()
    }

    override fun close() { Log.i(TAG, "Engine closed. Total hooked: ${hookedKeys.size}") }
}