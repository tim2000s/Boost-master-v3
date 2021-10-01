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
About AIMI :
Prebolus is necessary.
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