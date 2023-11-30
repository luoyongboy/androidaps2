package app.aaps.pump.equil.ui.dlg

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.pump.equil.R
import app.aaps.pump.equil.databinding.EquilDialogAlertDressingBinding
import com.bumptech.glide.Glide
import dagger.android.support.DaggerDialogFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

class EquilChangeInsulinDlg : DaggerDialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var ctx: Context
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var rxBus: RxBus

    private var _binding: EquilDialogAlertDressingBinding? = null

    val binding get() = _binding!!
    val disposable = CompositeDisposable()

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)

        _binding = EquilDialogAlertDressingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnCancel.setOnClickListener { dismiss() }
        Glide.with(view)
            .asGif()
            .load(R.drawable.equil_animation_wizard_detach)
            .into(binding.imv)
        binding.btnOk.setOnClickListener {
            dismiss()
        }
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        disposable.clear()
    }

    var task: Runnable? = null

    override fun show(manager: FragmentManager, tag: String?) {
        try {
            manager.beginTransaction().let {
                it.add(this, tag)
                it.commitAllowingStateLoss()
            }
        } catch (e: IllegalStateException) {
            aapsLogger.debug(e.localizedMessage ?: e.toString())
        }
    }
}