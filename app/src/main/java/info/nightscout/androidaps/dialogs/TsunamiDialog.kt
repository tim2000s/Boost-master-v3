package info.nightscout.androidaps.dialogs

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Joiner
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.transactions.CancelCurrentTsunamiModeIfAnyTransaction
import info.nightscout.androidaps.database.transactions.TsunamiModeSwitchTransaction
import info.nightscout.androidaps.databinding.DialogTsunamiBinding
import info.nightscout.androidaps.extensions.formatColor
import info.nightscout.androidaps.interfaces.*
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.*
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.toSignedString
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.protection.ProtectionCheck.Protection.BOLUS
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.SafeParse
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max

class TsunamiDialog : DialogFragmentWithDate() {

    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var ctx: Context
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var config: Config
    @Inject lateinit var bolusTimer: BolusTimer
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var protectionCheck: ProtectionCheck

    companion object {

        private const val PLUS1_DEFAULT = 0.5
        private const val PLUS2_DEFAULT = 1.0
        private const val PLUS3_DEFAULT = 2.0
    }

    private var queryingProtection = false
    private val disposable = CompositeDisposable()
    private var _binding: DialogTsunamiBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {
            validateInputs()
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    }

    private fun validateInputs() {
        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        if (abs(binding.tsuDuration.value.toInt()) > 5 * 60) {
            binding.tsuDuration.value = 0.0
            ToastUtils.showToastInUiThread(context, rh.gs(R.string.constraintapllied))
        }
        if (binding.amount.value > maxInsulin) {
            binding.amount.value = 0.0
            ToastUtils.showToastInUiThread(context, rh.gs(R.string.bolusconstraintapplied))
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("tsuDuration", binding.tsuDuration.value)
        savedInstanceState.putDouble("amount", binding.amount.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        onCreateViewGeneral()
        _binding = DialogTsunamiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()

        binding.tsuDuration.setParams(savedInstanceState?.getDouble("tsuDuration")
            ?: SafeParse.stringToDouble(sp.getString(R.string.key_tsunami_default_duration, "0.0")), 0.0, 300.0, 30.0, DecimalFormat("0"), false, binding.okcancel.ok, textWatcher)
        binding.amount.setParams(savedInstanceState?.getDouble("amount")
            ?: 0.0, 0.0, maxInsulin, activePlugin.activePump.pumpDescription.bolusStep, DecimalFormatter.pumpSupportedBolusFormat(activePlugin.activePump), false, binding.okcancel.ok, textWatcher)

        val plus05Text = sp.getDouble(rh.gs(R.string.key_prebolus_increment_1), PLUS1_DEFAULT).toSignedString(activePlugin.activePump)
        binding.plus05.text = plus05Text
        binding.plus05.contentDescription = rh.gs(R.string.overview_insulin_label) + " " + plus05Text
        binding.plus05.setOnClickListener {
            binding.amount.value = max(0.0, binding.amount.value
                + sp.getDouble(rh.gs(R.string.key_prebolus_increment_1), PLUS1_DEFAULT))
            validateInputs()
            binding.amount.announceValue()
        }
        val plus10Text = sp.getDouble(rh.gs(R.string.key_prebolus_increment_2), PLUS2_DEFAULT).toSignedString(activePlugin.activePump)
        binding.plus10.text = plus10Text
        binding.plus10.contentDescription = rh.gs(R.string.overview_insulin_label) + " " + plus10Text
        binding.plus10.setOnClickListener {
            binding.amount.value = max(0.0, binding.amount.value
                    + sp.getDouble(rh.gs(R.string.key_prebolus_increment_2), PLUS2_DEFAULT))
            validateInputs()
            binding.amount.announceValue()
        }
        val plus20Text = sp.getDouble(rh.gs(R.string.key_prebolus_increment_3), PLUS3_DEFAULT).toSignedString(activePlugin.activePump)
        binding.plus20.text = plus20Text
        binding.plus20.contentDescription = rh.gs(R.string.overview_insulin_label) + " " + plus20Text
        binding.plus20.setOnClickListener {
            binding.amount.value = max(0.0, binding.amount.value
                    + sp.getDouble(rh.gs(R.string.key_prebolus_increment_3), PLUS3_DEFAULT))
            validateInputs()
            binding.amount.announceValue()
        }
            if (repository.getTsunamiModeActiveAt(dateUtil.now()).blockingGet() is ValueWrapper.Existing)
                binding.tsuCancel.visibility = View.VISIBLE
            else
                binding.tsuCancel.visibility = View.GONE
        binding.tsuCancel.setOnClickListener {
            binding.tsuDuration.value = 0.0
            binding.amount.value = 0.0
            if (submit()) dismiss()
        }
        binding.tsunamiLabel.labelFor = binding.amount.editTextId
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val pumpDescription = activePlugin.activePump.pumpDescription
        val insulin = SafeParse.stringToDouble(binding.amount.text)
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(Constraint(insulin)).value()
        val actions: LinkedList<String?> = LinkedList()
        val duration = binding.tsuDuration.value.toInt()

        if (insulinAfterConstraints > 0) {
            actions.add(rh.gs(R.string.prebolus) + ": " + DecimalFormatter.toPumpSupportedBolus(insulinAfterConstraints, activePlugin.activePump, rh).formatColor(context, rh, R.attr.bolusColor))
            if (abs(insulinAfterConstraints - insulin) > pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints))
                actions.add(rh.gs(R.string.bolusconstraintappliedwarn, insulin, insulinAfterConstraints).formatColor(context, rh, R.attr.warningColor))
        }
        if (duration > 0) {
            actions.add(rh.gs(R.string.tsunami_duration) + ": " + rh.gs(R.string.format_mins, duration).formatColor(rh, R.color.colorTsunamiButton))
        } else if (duration == 0 && repository.getTsunamiModeActiveAt(dateUtil.now()).blockingGet() is ValueWrapper.Existing) {
            actions.add(rh.gs(R.string.cancel_tsunami))
        }

        val time = dateUtil.now()

        val notes = binding.notesLayout.notes.text.toString()
        if (notes.isNotEmpty())
            actions.add(rh.gs(R.string.notes_label) + ": " + notes)

        if (insulinAfterConstraints > 0 || duration > 0) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(R.string.tsunami_label), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {//MP: String is header string of confirmation window if there is a prebolus
                    if (insulinAfterConstraints > 0) {
                        val detailedBolusInfo = DetailedBolusInfo()
                        detailedBolusInfo.eventType = DetailedBolusInfo.EventType.CORRECTION_BOLUS
                        detailedBolusInfo.insulin = insulinAfterConstraints
                        detailedBolusInfo.context = context
                        detailedBolusInfo.notes = notes
                        detailedBolusInfo.timestamp = time
                        if (duration == 0) {
                            uel.log(Action.BOLUS, Sources.TsunamiDialog, //TODO: CHECK
                                notes,
                                ValueWithUnit.Insulin(insulinAfterConstraints))
                        } else {
                            uel.log(Action.TSUNAMI_BOLUS, Sources.TsunamiDialog, //TODO: CHECK
                                notes,
                                ValueWithUnit.Insulin(insulinAfterConstraints), ValueWithUnit.Minute(duration))
                        }
                            commandQueue.bolus(detailedBolusInfo, object : Callback() {
                                override fun run() {
                                    if (!result.success) {
                                        ErrorHelperActivity.runAlarm(ctx, result.comment, rh.gs(R.string.treatmentdeliveryerror), R.raw.boluserror)
                                    } else {
                                        bolusTimer.removeBolusReminder()
                                    }
                                }
                            })
                    } else {
                        uel.log(Action.TSUNAMI, Sources.TsunamiDialog, //TODO: CHECK
                            notes,
                            ValueWithUnit.Minute(duration))
                    }
                    if (duration > 0) {
                        disposable += repository.runTransactionForResult(TsunamiModeSwitchTransaction(
                            timestamp = System.currentTimeMillis(),
                            duration = TimeUnit.MINUTES.toMillis(duration.toLong()),
                            tsunamiMode = 2
                        )).subscribe({ result ->
                            result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted tsunami mode $it") }
                            result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated tsunami mode $it") }
                        }, {
                            aapsLogger.error(LTag.DATABASE, "Error while saving Tsunami mode.", it)
                        })
                    } else { //MP Cancels current tsunami mode if no duration is entered, but a prebolus is issued
                        disposable += repository.runTransactionForResult(CancelCurrentTsunamiModeIfAnyTransaction(eventTime))
                            .subscribe({ result ->
                                result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated tsunami mode $it") }
                            }, {
                                aapsLogger.error(LTag.DATABASE, "Error while saving tsunami mode", it)
                            })
                    }
                })
            }
        } else if (repository.getTsunamiModeActiveAt(dateUtil.now()).blockingGet() is ValueWrapper.Existing) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(R.string.tsunami_label), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {//MP: String is header string of confirmation window if there is a prebolus
                disposable += repository.runTransactionForResult(CancelCurrentTsunamiModeIfAnyTransaction(eventTime))
                    .subscribe({ result ->
                        result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated tsunami mode $it") }
                    }, {
                        aapsLogger.error(LTag.DATABASE, "Error while saving tsunami mode", it)
                    }
                    )
                    uel.log(Action.CANCEL_TSUNAMI, Sources.TsunamiDialog, ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged })
                })
            }
        } else {
            activity?.let { activity ->
                OKDialog.show(activity, rh.gs(R.string.tsunami_label), rh.gs(R.string.no_action_selected))
            }
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        if(!queryingProtection) {
            queryingProtection = true
            activity?.let { activity ->
                val cancelFail = {
                    queryingProtection = false
                    aapsLogger.debug(LTag.APS, "Dialog canceled on resume protection: ${this.javaClass.name}")
                    ToastUtils.showToastInUiThread(ctx, R.string.dialog_canceled)
                    dismiss()
                }
                protectionCheck.queryProtection(activity, BOLUS, { queryingProtection = false }, cancelFail, cancelFail)
            }
        }
    }
}