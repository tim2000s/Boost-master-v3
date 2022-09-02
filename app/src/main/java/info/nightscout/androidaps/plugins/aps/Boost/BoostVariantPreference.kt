package info.nightscout.androidaps.plugins.aps.Boost

import android.content.Context
import android.util.AttributeSet
import androidx.preference.DropDownPreference
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.shared.sharedPreferences.SP
import java.util.*
import javax.inject.Inject

class BoostVariantPreference(context: Context, attrs: AttributeSet?)
    : DropDownPreference(context, attrs) {

    @Inject lateinit var sp: SP

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)

        val entries = Vector<CharSequence>()
        entries.add(BoostDefaults.variant)

        val list = context.assets.list("Boost/")
        list?.forEach {
            if (!it.endsWith(".js"))
                entries.add(it)
        }

        entryValues = entries.toTypedArray()
        setEntries(entries.toTypedArray())
        setDefaultValue(sp.getString(R.string.key_boost_variant, BoostDefaults.variant))
    }
}
