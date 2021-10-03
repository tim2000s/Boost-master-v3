# AndroidAPS

* Check the wiki: https://androidaps.readthedocs.io
*  Everyone who’s been looping with AndroidAPS needs to fill out the form after 3 days of looping  https://docs.google.com/forms/d/14KcMjlINPMJHVt28MDRupa4sz4DDIooI4SrW0P3HSN8/viewform?c=0&w=1

[![Gitter](https://badges.gitter.im/MilosKozak/AndroidAPS.svg)](https://gitter.im/MilosKozak/AndroidAPS?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build status](https://travis-ci.org/nightscout/AndroidAPS.svg?branch=master)](https://travis-ci.org/nightscout/AndroidAPS)
[![Crowdin](https://d322cqt584bo4o.cloudfront.net/androidaps/localized.svg)](https://translations.androidaps.org/project/androidaps)
[![Documentation Status](https://readthedocs.org/projects/androidaps/badge/?version=latest)](https://androidaps.readthedocs.io/en/latest/?badge=latest)
[![codecov](https://codecov.io/gh/MilosKozak/AndroidAPS/branch/master/graph/badge.svg)](https://codecov.io/gh/MilosKozak/AndroidAPS)
dev: [![codecov](https://codecov.io/gh/MilosKozak/AndroidAPS/branch/dev/graph/badge.svg)](https://codecov.io/gh/MilosKozak/AndroidAPS)


![BTC](https://bitit.io/assets/coins/icon-btc-1e5a37bc0eb730ac83130d7aa859052bd4b53ac3f86f99966627801f7b0410be.svg) 3KawK8aQe48478s6fxJ8Ms6VTWkwjgr9f2

########################################
This version of AAPS contain two plugins, AIMI and Boost.

Both new plugins use Total Daily dose from the AAPS database to determine ISF.

If you change from U100 to U200 insulin, you *MUST* reset the databases, otherwise the calculations will produce a too strong sensitivity value that will have too low a number, which may cause hypos, and run on the OpenapsSMB plugin for a couple of days to gather the correct TDD data.

About AIMI :
Manual Bolus is necessary.
If you change your insulin from U100 for U200 concentration, you need to reset database and use openapsplugin for several days before to come back in AIMI, TDD calculation is looking the quantity of insulin on 7 days and the current day.
It's true too U200 to U100 concentration.
Here the part of the log who explain how it's working.

     console.log("Pump extrapolated TDD = "+tdd_pump);
     console.log("tdd7 using 7-day average "+tdd7);
     console.log("TDD 7 ="+tdd7+", TDD Pump ="+tdd_pump+" and TDD = "+TDD);
     console.log("Current sensitivity is " +variable_sens+" based on current bg"); =>  ISF calculate by TDD
     console.log("eRatio: "+eRatio); => IC calculate from TDD
     if (TriggerPredSMB >= 950 || iTime < 180 ){ => iTime is the last manual bolus age, it will autorize during 180 minutes bigger smb or basal
            console.log("--- if TriggerPredSMB >= 950 ou iTime < 180 -----");
                            console.log("TriggerPredSMB : "+TriggerPredSMB);
                            console.log("iTime : "+iTime);
                            console.log("target_bg from "+target_bg+" to "+hyper_target);
                            console.log("Sensitivity ratio set to "+sensitivityRatio+" based on temp target of "+target_bg);
                            console.log("Adjusting basal from "+profile_current_basal+" to "+basal);
                            console.log("maxBolusTT : "+maxBolusTT);
                            console.log("InsulinReqPCT : "+(insulinReqPCT * 100)+"%");
                            console.log("insulinReq : "+insulinReq);
                            console.log("microBolus : " +microBolus);
        console.log("------------------------------");

If some bugs or new function were required, open an issue in the project on gitlab and describe it.
enjoy :-)

BOOST Plugin:
This code is designed to be used with a Circadian profile and variable ISF rates throughout the day that align with the circadian profile.
The intention of htis code is to deliver an early, larger bolus when rises are detected to intiate UAM deviations and to allow the algorithm to be more aggressive. Other than Boost, it relies on oref1 adjusted to use the variable ISF function and some SMB scaling.
All of the additional code outside of the standard SMB calculation requires a time period to be specified within which it is active. The default time settings disable the code. The time period is specified in hours in the Boost preferences section.
When an initial rise is detected with a meal, delta, short_avgDelta and long_avgDelta are used to trigger the early bolus (assuming IOB is below a user defined amount). The early bolus value is a multiple of insulin required, calculated using a multiplier that is thebuser defined Boost Scale Value  * (target_bg/( eventualBG - target_bg )
If Boost Scale Value is less than 3, Boost is enabled.

The short and long average delta clauses disable boost once delta and the average deltas are aligned. There is a preferences setting (Boost Bolus Cap) that limits the maximum bolus that can be delivered by Boost outside of the standard UAMSMBBasalMinutes limit.

If glucose levels are predicted above 108 and there is a delta > 8, then 100% of insulin required is delivered.

If no other criteria are met, and glucose level is > 108, and delta > 3, then insulin required is scaled from insulin required PCT up to 100% at 180mg/dl.

If none of the conditions are met, standard SMB logic is used to size SMBs, with the insulin required PCT entered in prefernces.
Settings that have been added to the BOOST settings are:

Boost Scale Value - defaults to 1.0. Only increase multiplier once you have trialled. 
Boost Bolus Cap - defaults to 0.1
UAM Boost max IOB - defaults to 0.1
UAM Boost Start Time (in hours) - defaults to 7
UAM Boost end time (in hours) - defaults to 8
