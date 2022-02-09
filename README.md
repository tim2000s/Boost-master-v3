# AndroidAPS

* Check the wiki: https://androidaps.readthedocs.io
*  Everyone who’s been looping with AndroidAPS needs to fill out the form after 3 days of looping  https://docs.google.com/forms/d/14KcMjlINPMJHVt28MDRupa4sz4DDIooI4SrW0P3HSN8/viewform?c=0&w=1

[![Support Server](https://img.shields.io/discord/629952586895851530.svg?label=Discord&logo=Discord&colorB=7289da&style=for-the-badge)](https://discord.gg/4fQUWHZ4Mw)

[![Build status](https://travis-ci.org/nightscout/AndroidAPS.svg?branch=master)](https://travis-ci.org/nightscout/AndroidAPS)
[![Crowdin](https://d322cqt584bo4o.cloudfront.net/androidaps/localized.svg)](https://translations.androidaps.org/project/androidaps)
[![Documentation Status](https://readthedocs.org/projects/androidaps/badge/?version=latest)](https://androidaps.readthedocs.io/en/latest/?badge=latest)
[![codecov](https://codecov.io/gh/MilosKozak/AndroidAPS/branch/master/graph/badge.svg)](https://codecov.io/gh/MilosKozak/AndroidAPS)
dev: [![codecov](https://codecov.io/gh/MilosKozak/AndroidAPS/branch/dev/graph/badge.svg)](https://codecov.io/gh/MilosKozak/AndroidAPS)


![BTC](https://bitit.io/assets/coins/icon-btc-1e5a37bc0eb730ac83130d7aa859052bd4b53ac3f86f99966627801f7b0410be.svg) 3KawK8aQe48478s6fxJ8Ms6VTWkwjgr9f2


Plugin AIMI V10 22/11/2021 :

This plugin was designed to manage the rise during the meal and the post meal time.
To be safe a time windows is creating in two situation :
- manual bolus before the meal
- entry a quantity of carbs > 30
=> the script will check the value of iTime in the settings of AIMI, who will be the end of the
window and start the window with one of the both previous event (manual bolus or carbs entry)

during this Time window, the smb size is calculating differently :
during the first half of iTime window you will have InsulinTDD = (TDD * 0.4) / 24
smb => insulinReq = insulinReq + InsulinTDD => microbolus = insulinReq * smbRatio * insulinReqPCT
 => you always have the detail in the AIMI log in this case. insulinTDD will send small quantity
 of insulin when during the first 30 minutes insulinreq is not enough, limiting the size of the
 rise.
the second half iTime window microBolus = Math.min(insulinReq*smb_ratio*insulinReqPCT, maxBolus)

insulinReqPCT this one is the insulinre % in the settings => lesser than 100 % it will reduce the
 power of the answer during the rise. 100% no variation of the power. more than 100, that's means
  this days you need more power, because you are in holidays for exemple and you drink or eat
  more than usually. the % of insulin delivred is now managed by smb ratio. Explaination is later
   in this readme.

maxbolus is define during this window by iTime_Max_minutes, who give you the option to have a
maxUAMminutesbasal normal the rest of the time.

if you start your meal with a manual Bolus, you will recieve an autobolus (value iTime_Bolus in
the settings) at 26 minutes. Generaly you will observe the start rising event around this 26
minutes, this is why i created this possibility. If the value in the settings is 0, no action.

if you start your meal with carbs entry > 30, at 15 minutes you will recieve an autobolus, who is
in this case a calculation : microBolus = (COB / eRatio)/2 => !!!!!consider if you entry more than
 30 carbs, this calculation will happen !!!!!! it's a testing code, not sure i will keep it in
 the next version, but the purpose is to send a bolus to fight the rise when he is starting and
 let the script manage the second wave. This idea give you the option to indicate by the
 quantity if the meal will be small, medium or very large. a small meal will be lesser than 30,
 medium around 40, large meal bigger than 60
 it's not necessary to be really precise.

 Now come the TDD subject :
 Initially TDD was calculated by using an average on the last 7 days and the current daily dose
 of insulin. But in some rare case TDD can be higher and if you start for the first time the aaps
  v3 or beta dev actually, no TDD possible. Then i think about this pb and decide to test a
  projection of the current daily insulin. my last two days were working great. i will make it
  better but this version is working nicely and base on the current day only.
  theoricaly(i will test it on a new phone soon) you can start this plugin if no data.

 Out of the iTime window you have :
 -Automated target management
 -smbRatio function => this one will change the % of insulin required, start at 50% until 100%,
 for the variability, and you have a fixe % varaible too in the settings.
 -smb_max_range_extension who is  a scale of maxuamminutesbasale

AIMI V12 05/12/2021
-Fixed iTime bug
-Fixed autobolus with the new logical. When the trigger for iTime is a manual Bolus, autobolus 
will come if the conditions are true : iTime < iTimeProfile, iob < 2 * iTime_starting_bolus, 
delta > 5 and long delta > 0
-iTime_startings-bolus is a new variable in the settings, it's the limit to start iTime.
Now iTime exist if trigger is manual bolus and iob > iTime_starting_bolus and bg + glucose_status.delta > profile.min_bg (min target in the profile) + profile.smb_delivery_ratio_bg_range (in advanced option)
-merge 2.8.2.14 from Milos

AAPS-V3-RC3-AIMI V14 17/01/2022
this version is base on V3 RC2 from milos.
Was added in the v13, style true in the v14, the possibility to entry the carbs instead of 
making a prebolus or doing nothing.
if you make the choice to entry a quantity of carbs, the code will be activate only if carbs > 30
in this case, the quantity will be divided by 3 and three bolus will be define and delivred if 
the condition are good enough. 
In the V13, IC in the profile was use, but i decide, like ISF come from TDD to let the code 
define the IC in function of the ISF evolution. 
The first SMBcarbs will come at 5 minutes, the second one between 10 and 20, the third one only 
if the conditions are true. IOB is check for each SMBCarbs, to be sure the quantity of insulin 
follow the need. 
Since the IC evolve, the result is for me amazing and no hypo after lunch or dinner. 
Of course, the prebolus way is style working great, i just add a new possibility with carbs entry.

AAPS-V3-RC3-AIMI V14 21/01/2022
Small change for better behavior
iTime will start in this condition : 
The size of the manual bolus is >= the variable iTime_Starting_Bolus and not anymore base one the value of total IOB. 
The quantity of carbs > 30. This time, i create an independant variable from the database, it will take the last carb entry. That means too, if you entry 45 carbs, and 60 minutes later 
10 carbs, it will stop iTime, because the last entry is 10 and not 45 carbs. For me it's not nécessary to entry 10 carbs when iTime is running with carbs, it will manage it without add 10.



Boost v3.1.1

Boost v3 uses an updated version of the TDD ISF calculation, with a weighting of 20% to future Bg
 and 80% to current BG when delta is >6, current bg when delta 0 < 6 and future bg when delta is
 0 or negative.

 You can use Boost when announcing carbs or without announcing carbs. There is no special code
 that differentiaties behaviour when doing either. Similarly, if you prefer to manually bolus, it
  fully supports that as well.

 It also has variable insulin percentage determined by the user, and while boost time is valid,
 the algorithm can bolus up to a maximum bolus defined by the user in preferences.

The intention of this code is to deliver an early, larger bolus when rises are detected to
intiate UAM deviations and to allow the algorithm to be more aggressive. Other than Boost, it
relies on oref1 adjusted to use the variable ISF function based on TDD.

All of the additional code outside of the standard SMB calculation requires a time period to be
specified within which it is active. The default time settings disable the code. The time period
is specified in hours using a 24 hour clock in the Boost preferences section.

COB: With Carbs on Board, Boost has a 15 minute window to deliver a larger bolus than would normally be expected, up to InsulinRequired calculated by the oref1 algorithm, taking carbs into account, and limited by the user set "insulin Required percent". In the following 40 mins after the carbs are added, it can do additional larger boluses, as long as there is a delta >5 and COB > 25.

When an initial rise is detected with a meal, delta, short_avgDelta and long_avgDelta are used to
 trigger the early bolus (assuming IOB is below a user defined amount). The early bolus value is
 one hour of basal requirement and is based on the current period basal rate, unless this is smaller than "Insulin Required" when that is used instead.

The user defined Boost Scale Value can be used to increase the boost bolus if the user requires, however, users should be aware that this increases the risk of hypos when small rises occur.

If Boost Scale Value is less than 3, Boost is enabled.

The short and long average delta clauses disable boost once delta and the average deltas are aligned. There is a preferences setting (Boost Bolus Cap) that limits the maximum bolus that can be delivered by Boost outside of the standard UAMSMBBasalMinutes limit.

If none of the conditions are met, standard SMB logic is used to size SMBs, with the insulin required PCT entered in preferences.

Once you are outside the Boost hours, "max minutes of basal to limit SMB to for UAM" is enabled.

Settings that have been added to the BOOST settings are:

Note that the default settings are designed to disable most of the functions, and you will need
to adjust them.

Boost insulin required percent - defaults to 50% can be increased, but increasing increases hypo risk.
Boost Scale Value - defaults to 1.0. Only increase multiplier once you have trialled.
Boost Bolus Cap - defaults to 0.1
UAM Boost max IOB - defaults to 0.1
UAM Boost Start Time (in hours) - defaults to 7
UAM Boost end time (in hours) - defaults to 8

Recommended Settings

Boost Bolus Cap - Start at 2.5% of TDD and increase to no more than 5% of total daily dose.
UAM Boost max IOB - Start at 5% of TDD and increase to no more than 10% of total daily dose.
UAMSMBBasalMinutes - 60 mins. This is only used overnight when IOB is large enough to trigger UAM, so it doesn't need to be a large value.
Boost insulin required percent - recommended not to exceed 75%. Start at 50% and increase as necessary.

########################################
This version of AAPS has evolved over time using elements from AIMI and Boost.
This AAPS variation is called "Eating Now" (EN) as it is a reactive operating mode without needing to inform the system.
The intent of this plugin is the same, to deliver insulin earlier using mostly openAPS predictions.
This has been tested successfully with a blend of Fiasp 80% and Novorapid 20% (F80N).
The code can be used with or without bolusing or COB entries.
However it will not become active until a treatment has been performed after the active start time.
Allowing a delayed start time for example if you sleep in. :)
This treatment can be 1g or 0.1U for example.
After this the EN mode is active until the end time specified in the preferences.
It is recommended to set maxSMBBasalMinutes and maxUAMSMBBasalMinutes to 60 minutes max as these will be used when EN is not active.

These are the methods utilised within this version:

UAM:
This is based upon Boost and is used when there is a sudden increase in BGL of >=9 (0.5 mmol)
UAMBoost will only operate when there are no COB.
TDD is used as a reference point for initial insulin dose that can be scaled within preferences.

COB:
When carbs are entered there is a time window like AIMI.
The COBpredBG prediction uses the dynamic ISF from Boost to increase insulinReq.
If within the COBBoost Window the calculated insulinReq may be delivered via a larger SMB using the COBBoost maxBolus.
Once the time window has elapsed COBBoost maxBolus is no longer used.

Predictions leverage the dynamic ISF concept within the Boost plugin.
Using the eventualBG mostly to determine the insulinReq.
The main difference is the initial ISF used to determine the predictions is based on the profile ISF.
If BG is currently the normalTarget BG from the profile the ISF will be the same as the profile.
Once BG rises the ISF number reduces, and as BG lowers the ISF number will increase.
ISF scaling can be adjusted and eventualBG weighting for UAM and COB predictions can be applied.

These are the preferences utilised for EN mode:

General:
    Start Time:         The time the EN mode will start in hours as 24h clock format
                        EN mode will be active after this time when there has been a COB or manual bolus entry of any size
    End Time:           The time that EN mode will finish. Normal maxBolus of 65% is resumed.
                        If there are COB or a TT of normalTarget EN will be active after this time, however AAPS maxBolus will be used.
                        No SMB will be given when inactive unless there is COB, detected resistance from autosens or BG is above SMB BG Threshold.
    InsulinReqPct:      Percentage that will be used for EN insulinReq as SMB to utilise prior to maxBolus restriction.
                        This will be 65% when EN is not active.
    Max IOB:            The percentage of current max-iob setting that will be used as the limit for EN.
                        EN will not add insulin when above this limit.
    SMB BG Threshold:   No SMB will be given when EN is outside operating hours and BG below this threshold.
                        If there is COB, detected resistance from autosens or BG exceeds this threshold SMB will be resumed using normal AAPS maxBolus.
    ISF BG Scaler:      As BG increases ISF will become stronger. The level of scaling can be adjusted.
                        0 = normal scaling, 5 is 5% stronger, -5 is 5% weaker ISF scaling.
    ISF BG Threshold:   As BG increases ISF will become stronger. ISF will no longer scale when above this level.

UAM:
    UAMBoost Bolus Scale:       Multiply the initial UAMBoost bolus by this amount. 0 will disable UAMBoost.
    UAMBoost maxBolus:          maxBolus to use for all BG rises without COB.  0 will use maxSMBBasalMinutes or maxUAMSMBBasalMinutes.
    UAM eventualBG Weighting:   This will be the initial weighting for eventualBG predictions without COB.
                                As ISF grows stronger the weighting will reduce favouring current BG ISF.
                                Setting to 50 will make this 50%. 0 will always use currentBG ISF without max ISF Limit applied.
COB:
    Use GhostCOB:               Ignore COB predictions after the COBBoost Window and rely purely on UAM. This setting can be handy when COB lingers for too long.
    COBBoost InsulinReqPct:     Percentage that will be used for EN insulinReq within the COBBoost Window.
    COBBoost Window:            If within the COBBoost Window the calculated insulinReq from COBPredBG may be delivered via a larger SMB using the COBBoost maxBolus.
                                Once the time window has elapsed COBBoost maxBolus is no longer used.
                                0 minutes will disable this functionality.
    COBBoost maxBolus:          maxBolus to use within the COBBoost Window. 0 will use AAPS maxBolus.
    COB maxBolus:               maxBolus to use with COB outside of the initial COBBoost Window. 0 will use AAPS maxBolus.
    COB eventualBG Weighting:   This will be the initial weighting for eventualBG predictions with COB.
                                As ISF grows stronger the weighting will reduce favouring current BG ISF.
                                Setting to 50 will make this 50%. 0 will always use currentBG ISF without max ISF Limit applied.
