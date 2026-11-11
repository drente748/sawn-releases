package com.sawn.domain

/** معرفات الميزات المستخدمة في الخطة والتتبع (لا تتغير بتغير الواجهة). */
object PlanFeature {
    const val PORN = "porn"
    const val SAFE_SEARCH = "safesearch"
    const val ADS = "ads"
    const val DOH = "doh"
    const val NOTIFICATIONS = "notifications"
    const val STRICT = "strict"
    const val FOCUS = "focus"
    const val APPS = "apps"
    const val FEEDS = "feeds"
}

/** خطة المستخدم المبنية من إجاباته — ما سيظهر في «خطتك الشخصية». */
data class Plan(
    val featureIds: List<String>,
    val requiresPin: Boolean,
)

/**
 * يبني «خطتك الشخصية» من إجابات الترحيب — دالة نقية بلا IO.
 * القواعد:
 * - «صارم» → الخطة تتطلب PIN (وضع الانضباط في نفس الخطة).
 * - «إباحية» → حجب الإباحية + البحث الآمن أولاً (أولوية المنتج).
 * - «إعلانات» → الإعلانات + حظر DNS المشفر للمواقع المموهة.
 * - هدف «تقليل التشتيت» → حجب التطبيقات؛ «حماية العائلة» → انضباط يحتاج PIN.
 * - اختيار خلاصات المحتوى يفعّل فلاتر Reels/Shorts بمعزل عن قائمة التطبيقات.
 * - هدف «تركيز» → جلسات التركيز حاضرة.
 * - بلا إجابات → التشكيلة المتوازنة (6 ميزات).
 */
object PlanBuilder {

    fun build(a: OnboardingAnswers): Plan {
        val features = mutableListOf<String>()

        if (a.goal == GOAL_PORN || PORN_CONTENT in a.content || a.commitment.isNotEmpty()) {
            features += PlanFeature.PORN
        }
        if (PORN_CONTENT in a.content || a.goal == GOAL_PORN) features += PlanFeature.SAFE_SEARCH
        if (ADS_CONTENT in a.content || a.goal == GOAL_ADS) features += PlanFeature.ADS
        if (ADS_CONTENT in a.content || a.goal == GOAL_ADS) features += PlanFeature.DOH
        if (FEEDS_CONTENT in a.content) features += PlanFeature.FEEDS
        if (APPS_CONTENT in a.content || a.goal == GOAL_DISTRACTION) features += PlanFeature.APPS
        if (a.goal == GOAL_FOCUS) features += PlanFeature.FOCUS
        if (a.commitment == COMMIT_STRICT || a.goal == GOAL_FAMILY) features += PlanFeature.STRICT
        if (a.commitment == COMMIT_SOFT || a.goal == GOAL_FOCUS) features += PlanFeature.FOCUS

        // fallback: التشكيلة المتوازنة عندما لا توجد إجابات أو لم تُستنتج ميزة
        if (features.isEmpty()) {
            features += listOf(
                PlanFeature.PORN,
                PlanFeature.SAFE_SEARCH,
                PlanFeature.ADS,
                PlanFeature.STRICT,
                PlanFeature.APPS,
                PlanFeature.FOCUS,
            )
        }

        return Plan(
            featureIds = features.distinct().take(6),
            requiresPin = a.commitment == COMMIT_STRICT || a.goal == GOAL_FAMILY,
        )
    }

    const val GOAL_PORN = "goal_porn"
    const val GOAL_DISTRACTION = "goal_distraction"
    const val GOAL_ADS = "goal_ads"
    const val GOAL_FOCUS = "goal_focus"
    const val GOAL_FAMILY = "goal_family"

    const val COMMIT_SOFT = "commit_soft"
    const val COMMIT_SERIOUS = "commit_serious"
    const val COMMIT_STRICT = "commit_strict"

    const val PORN_CONTENT = "content_porn"
    const val ADS_CONTENT = "content_ads"
    const val FEEDS_CONTENT = "content_feeds"
    const val APPS_CONTENT = "content_apps"
    const val SEARCH_CONTENT = "content_search"
}
