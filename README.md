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

## Boost v3.6.3

Boost v3 uses an updated version of the TDD ISF calculation, with a weighting of 20% to future Bg and 80% to current BG when delta is >6, current bg when delta 0 < 6 and future bg when delta is 0 or negative.

**Traditional Autosens is deprecated in this code** and sensitivityRatio is calculated using ( (Rolling 24 hour TDD + Predicted TDD ) / 2) / 7-day average TDD.

In Treatments Safety in preferences, there is now a user adjustable Low Glucose Suspend threshold. This allows the user to set a value higher than the system would normally use, such that when predictions drop below this level, a zero TBR is set. 

You can use Boost when announcing carbs or without announcing carbs. There is no special code that differentiaties behaviour when doing either. Similarly, if you prefer to manually bolus, it fully supports that as well.

 It also has variable insulin percentage determined by the user, and while boost time is valid, the algorithm can bolus up to a maximum bolus defined by the user in preferences.

The intention of this code is to deliver an early, larger bolus when rises are detected to intiate UAM deviations and to allow the algorithm to be more aggressive. Other than Boost, it relies on oref1 adjusted to use the variable ISF function based on TDD.

All of the additional code outside of the standard SMB calculation requires a time period to be specified within which it is active. The default time settings disable the code. The time period is specified in hours using a 24 hour clock in the Boost preferences section.

**COB:** With Carbs on Board, Boost has a 15 minute window to deliver a larger bolus than would normally be expected, up to InsulinRequired calculated by the oref1 algorithm, taking carbs into account, and limited by the user set "insulin Required percent". In the following 40 mins after the carbs are added, it can do additional larger boluses, as long as there is a delta >5 and COB > 25.

When an initial rise is detected with a meal, but no announced COB, delta, short_avgDelta and long_avgDelta are used to
 trigger the early bolus (assuming IOB is below a user defined amount). The early bolus value is
 one hour of basal requirement and is based on the current period basal rate, unless this is smaller than "Insulin Required" when that is used instead.

The user defined Boost Scale Value can be used to increase the boost bolus if the user requires, however, users should be aware that this increases the risk of hypos when small rises occur.

If **Boost Scale Value** is less than 3, Boost is enabled.

The short and long average delta clauses disable boost once delta and the average deltas are aligned. There is a preferences setting (Boost Bolus Cap) that limits the maximum bolus that can be delivered by Boost outside of the standard UAMSMBBasalMinutes limit.

**Boost Percentage Scale**

Boost percentage Scale is a new feature that allows Boost to scale the SMB from 150% of insulin required at 108 mg/dl (6 mmol/l) to the user entered insulin required PCT at 180mg/dl (10 mmol/l). It can be enabled via a switch in the preferences and the percentage values are hard coded. it is only active when [Delta - Short Average Delta ] is positive, meaning that it only happens when delta variation is accelerating.

If none of the conditions are met, standard SMB logic is used to size SMBs, with the insulin required PCT entered in preferences. This has now been modified to only work on positive deviations and similar to the percent scale, when deltas are getting larger.

Once you are outside the Boost hours, "max minutes of basal to limit SMB to for UAM" is enabled.

**The BOOST settings have a number of extra items**:

Note that the default settings are designed to disable most of the functions, and you will need
to adjust them.

**Boost insulin required percent** - defaults to 50% can be increased, but increasing increases hypo risk.
**Boost Scale Value** - defaults to 1.0. Only increase multiplier once you have trialled.
**Boost Bolus Cap** - defaults to 0.1
**UAM Boost max IOB** - defaults to 0.1
**UAM Boost Start Time (in hours using 24 hour clock)** - defaults to 7
**UAM Boost end time (in hours using 24 hour clock)** - defaults to 8

### Recommended Settings

**Boost Bolus Cap** - Start at 2.5% of TDD and increase to no more than 5% of 7 day average total daily dose.
**UAM Boost max IOB** - Start at 5% of TDD and increase to no more than 15% of 7 day average total daily dose.
**UAMSMBBasalMinutes** - 30 mins. This is only used overnight when IOB is large enough to trigger UAM, so it doesn't need to be a large value.
**Boost insulin required percent** - recommended not to exceed 75%. Start at 50% and increase as necessary.

