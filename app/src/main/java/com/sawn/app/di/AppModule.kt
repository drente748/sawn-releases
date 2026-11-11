package com.sawn.app.di

import android.content.Context
import com.sawn.core.data.BlockRepository
import com.sawn.core.data.RoomProtectedDaysRepository
import com.sawn.core.data.RoomStatsRepository
import com.sawn.core.data.SawnDatabase
import com.sawn.core.data.SettingsStore
import com.sawn.domain.block.AccessController
import com.sawn.domain.block.BlockEngine
import com.sawn.domain.repo.ProtectedDaysRepository
import com.sawn.domain.repo.StatsRepository
import com.sawn.app.usage.UsageStatsHelper
import com.sawn.app.entitlement.EntitlementService
import com.sawn.app.entitlement.NoopEntitlementService
import com.sawn.app.ads.AdsController
import com.sawn.app.update.UpdateChecker
import com.sawn.app.update.DisabledUpdateChecker
import com.sawn.app.update.RemoteManifestUpdateChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The Hilt graph for app-scoped singletons — the only wiring path for Sawn. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext ctx: Context): SawnDatabase =
        SawnDatabase.get(ctx)

    @Provides
    @Singleton
    fun settings(
        @ApplicationContext ctx: Context,
        protectedDays: ProtectedDaysRepository,
    ): SettingsStore =
        SettingsStore(ctx, protectedDays)

    @Provides
    @Singleton
    fun repository(db: SawnDatabase): BlockRepository =
        BlockRepository(db.blockDao())

    @Provides
    @Singleton
    fun blockEngine(repo: BlockRepository, settings: SettingsStore): BlockEngine =
        BlockEngine(repo, settings)

    @Provides
    @Singleton
    fun accessController(
        repo: BlockRepository,
        engine: BlockEngine,
        settings: SettingsStore,
        @ApplicationContext ctx: Context,
    ): AccessController =
        AccessController(
            repo,
            engine,
            usageMinutesToday = { pkg -> UsageStatsHelper.todayUsageMinutesFor(ctx, pkg) },
            trustedNow = { settings.trustedNow() },
            pauseDefeatsToday = { settings.pauseDefeatsToday(settings.trustedNow()) },
            deepFocusState = { settings.deepFocusState() },
        )

    @Provides
    @Singleton
    fun protectedDays(db: SawnDatabase): ProtectedDaysRepository =
        RoomProtectedDaysRepository(db.protectedDaysDao())

    @Provides
    @Singleton
    fun stats(days: ProtectedDaysRepository): StatsRepository =
        RoomStatsRepository(days)

    // ===== البنية المعطلة مسبقًا (unused في 2.0.0) =====
    // تُجمع هنا فقط لتصبح "البنية جاهزة": تفعيل مستقبلًا = استبدال التنفيذ هنا فقط.

    @Provides
    @Singleton
    fun entitlementService(): EntitlementService = NoopEntitlementService

    @Provides
    @Singleton
    fun updateChecker(): UpdateChecker =
        if (com.sawn.app.BuildConfig.UPDATE_MANIFEST_URL.isBlank()) {
            DisabledUpdateChecker
        } else {
            RemoteManifestUpdateChecker(com.sawn.app.BuildConfig.UPDATE_MANIFEST_URL)
        }

    @Provides
    @Singleton
    fun adsController(): AdsController = com.sawn.app.ads.NoopAdsController
}
