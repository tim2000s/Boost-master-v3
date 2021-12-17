package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.aps.fullUAM.DetermineBasalAdapterUAMJS
import info.nightscout.androidaps.plugins.aps.fullUAM.DetermineBasalResultUAM
import info.nightscout.androidaps.plugins.aps.Boost.DetermineBasalAdapterBoostJS
import info.nightscout.androidaps.plugins.aps.Boost.DetermineBasalResultBoost
import info.nightscout.androidaps.plugins.aps.logger.LoggerCallback
import info.nightscout.androidaps.plugins.aps.loop.APSResult
import info.nightscout.androidaps.plugins.aps.openAPSAMA.DetermineBasalAdapterAMAJS
import info.nightscout.androidaps.plugins.aps.openAPSAMA.DetermineBasalResultAMA
import info.nightscout.androidaps.plugins.aps.openAPSSMB.DetermineBasalAdapterSMBJS
import info.nightscout.androidaps.plugins.aps.openAPSSMB.DetermineBasalResultSMB
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobOref1Thread
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobThread

@Module
@Suppress("unused")
abstract class APSModule {

    @ContributesAndroidInjector abstract fun loggerCallbackInjector(): LoggerCallback
    @ContributesAndroidInjector abstract fun determineBasalResultSMBInjector(): DetermineBasalResultSMB
    @ContributesAndroidInjector abstract fun determineBasalResultAMAInjector(): DetermineBasalResultAMA
    @ContributesAndroidInjector abstract fun determineBasalResultUAMInjector(): DetermineBasalResultUAM
    @ContributesAndroidInjector abstract fun determineBasalResultBoostInjector(): DetermineBasalResultBoost
    @ContributesAndroidInjector abstract fun determineBasalAdapterAMAJSInjector(): DetermineBasalAdapterAMAJS
    @ContributesAndroidInjector abstract fun determineBasalAdapterSMBJSInjector(): DetermineBasalAdapterSMBJS
    @ContributesAndroidInjector abstract fun determineBasalAdapterUAMJSInjector(): DetermineBasalAdapterUAMJS
    @ContributesAndroidInjector abstract fun determineBasalAdapterBoostJSInjector(): DetermineBasalAdapterBoostJS
    @ContributesAndroidInjector abstract fun iobCobThreadInjector(): IobCobThread
    @ContributesAndroidInjector abstract fun iobCobOref1ThreadInjector(): IobCobOref1Thread
}