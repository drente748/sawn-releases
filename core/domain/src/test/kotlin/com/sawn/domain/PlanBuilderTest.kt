package com.sawn.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanBuilderTest {

    @Test
    fun `strict commitment requires a PIN`() {
        val plan = PlanBuilder.build(
            OnboardingAnswers(goal = PlanBuilder.GOAL_PORN, commitment = PlanBuilder.COMMIT_STRICT),
        )
        assertTrue(plan.requiresPin)
    }

    @Test
    fun `porn content puts porn guard and safe search first`() {
        val plan = PlanBuilder.build(
            OnboardingAnswers(content = setOf(PlanBuilder.PORN_CONTENT)),
        )
        assertEquals(listOf(PlanFeature.PORN, PlanFeature.SAFE_SEARCH), plan.featureIds.take(2))
    }

    @Test
    fun `ads goal adds ad blocking and encrypted-dns blocking`() {
        val plan = PlanBuilder.build(
            OnboardingAnswers(goal = PlanBuilder.GOAL_ADS),
        )
        assertTrue(PlanFeature.ADS in plan.featureIds)
        assertTrue(PlanFeature.DOH in plan.featureIds)
    }

    @Test
    fun `feeds selection enables the in-app feed feature independently of app blocking`() {
        val plan = PlanBuilder.build(
            OnboardingAnswers(content = setOf(PlanBuilder.FEEDS_CONTENT)),
        )
        assertTrue(PlanFeature.FEEDS in plan.featureIds)
        assertFalse(PlanFeature.APPS in plan.featureIds)
    }

    @Test
    fun `family goal requires a parent PIN for strict protection`() {
        assertTrue(
            PlanBuilder.build(OnboardingAnswers(goal = PlanBuilder.GOAL_FAMILY)).requiresPin,
        )
    }

    @Test
    fun `distraction goal puts apps under control`() {
        val plan = PlanBuilder.build(
            OnboardingAnswers(goal = PlanBuilder.GOAL_DISTRACTION),
        )
        assertTrue(PlanFeature.APPS in plan.featureIds)
    }

    @Test
    fun `focus goal adds focus sessions`() {
        val plan = PlanBuilder.build(
            OnboardingAnswers(goal = PlanBuilder.GOAL_FOCUS),
        )
        assertTrue(PlanFeature.FOCUS in plan.featureIds)
    }

    @Test
    fun `empty answers yield the balanced default`() {
        val plan = PlanBuilder.build(OnboardingAnswers())
        assertEquals(
            listOf(
                PlanFeature.PORN,
                PlanFeature.SAFE_SEARCH,
                PlanFeature.ADS,
                PlanFeature.STRICT,
                PlanFeature.APPS,
                PlanFeature.FOCUS,
            ),
            plan.featureIds,
        )
        assertFalse(plan.requiresPin)
    }

    @Test
    fun `plan never exceeds six features and has no duplicates`() {
        val plan = PlanBuilder.build(
            OnboardingAnswers(
                goal = PlanBuilder.GOAL_PORN,
                commitment = PlanBuilder.COMMIT_STRICT,
                content = setOf(
                    PlanBuilder.PORN_CONTENT,
                    PlanBuilder.ADS_CONTENT,
                    PlanBuilder.FEEDS_CONTENT,
                    PlanBuilder.APPS_CONTENT,
                    PlanBuilder.SEARCH_CONTENT,
                ),
            ),
        )
        assertTrue(plan.featureIds.size <= 6)
        assertEquals(plan.featureIds.size, plan.featureIds.toSet().size)
    }

    @Test
    fun `answers round-trip through serialization`() {
        val original = OnboardingAnswers(
            source = "yt",
            goal = "goal_porn",
            commitment = "commit_strict",
            content = setOf("content_porn", "content_ads"),
            name = "أحمد",
            age = 24,
        )
        assertEquals(original, OnboardingAnswers.deserialize(original.serialize()))
        assertEquals(null, OnboardingAnswers.deserialize(null))
        assertEquals(null, OnboardingAnswers.deserialize("nonsense"))
    }
}
