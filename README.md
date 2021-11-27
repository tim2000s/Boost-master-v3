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
 Initially TDD was calculated by usind an average on the last 7 days and the current daily dose
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
