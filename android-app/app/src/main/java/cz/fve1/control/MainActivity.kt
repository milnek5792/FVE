package cz.fve1.control

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cz.fve1.control.databinding.ActivityMainBinding
import cz.fve1.control.screen.AppUiState
import cz.fve1.control.screen.DailyChartView
import cz.fve1.control.screen.FveViewModel
import cz.fve1.control.screen.MqttLinkStatus
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val viewModel: FveViewModel by viewModels {
        val app = application as FveApplication
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FveViewModel(app.settingsStore, app.mqttRepository, app.chartSampler) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.bg)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.bg)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()

        bindHome()
        bindSettings()
        bindNav()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.ui.collect { render(it) }
            }
        }
    }

    private fun applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topbar.updatePadding(top = bars.top)
            binding.bottomNav.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun bindNav() {
        val goHome = View.OnClickListener { viewModel.selectTab(0) }
        val goSettings = View.OnClickListener { viewModel.selectTab(1) }
        binding.navHome.setOnClickListener(goHome)
        binding.navHomeIcon.setOnClickListener(goHome)
        binding.navHomeLabel.setOnClickListener(goHome)
        binding.navSettings.setOnClickListener(goSettings)
        binding.navSettingsIcon.setOnClickListener(goSettings)
        binding.navSettingsLabel.setOnClickListener(goSettings)
    }

    private fun bindHome() {
        home().findViewById<View>(R.id.btnReset).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Nulování")
                .setMessage("Opravdu chcete provést nulování denní výroby?")
                .setPositiveButton("Ano") { _, _ -> viewModel.resetDaily() }
                .setNegativeButton("Ne", null)
                .show()
        }
    }

    private fun bindSettings() {
        settings().findViewById<View>(R.id.btnConnect).setOnClickListener {
            syncDraftFromFields()
            viewModel.connect()
        }
        settings().findViewById<View>(R.id.btnDisconnect).setOnClickListener {
            viewModel.disconnect()
        }
        val pass = settings().findViewById<EditText>(R.id.cfgPass)
        val toggle = settings().findViewById<TextView>(R.id.btnTogglePass)
        toggle.setOnClickListener {
            val visible = pass.transformationMethod == null
            if (visible) {
                pass.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                pass.transformationMethod =
                    android.text.method.PasswordTransformationMethod.getInstance()
                toggle.alpha = 0.55f
            } else {
                pass.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                pass.transformationMethod = null
                toggle.alpha = 1f
            }
            pass.setSelection(pass.text?.length ?: 0)
        }
        toggle.alpha = 0.55f
    }

    private fun home(): View = binding.pageHome.root
    private fun settings(): View = binding.pageSettings.root

    private fun syncDraftFromFields() {
        viewModel.updateDraft(
            host = settings().findViewById<EditText>(R.id.cfgHost).text?.toString().orEmpty(),
            user = settings().findViewById<EditText>(R.id.cfgUser).text?.toString().orEmpty(),
            password = settings().findViewById<EditText>(R.id.cfgPass).text?.toString().orEmpty(),
            prefix = settings().findViewById<EditText>(R.id.cfgPrefix).text?.toString().orEmpty(),
        )
    }

    private fun render(state: AppUiState) {
        binding.pager.displayedChild = state.tab
        styleNav(state.tab)
        renderStatus(state)
        renderHome(state)
        renderSettings(state)
        state.toast?.let {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    private fun styleNav(tab: Int) {
        val active = ContextCompat.getColor(this, R.color.accent)
        val muted = ContextCompat.getColor(this, R.color.muted)
        binding.navHomeIcon.setColorFilter(if (tab == 0) active else muted)
        binding.navHomeLabel.setTextColor(if (tab == 0) active else muted)
        binding.navSettingsIcon.setColorFilter(if (tab == 1) active else muted)
        binding.navSettingsLabel.setTextColor(if (tab == 1) active else muted)
    }

    private fun renderStatus(state: AppUiState) {
        val (label, colorRes) = when (state.mqttStatus) {
            MqttLinkStatus.Connected -> "Online" to R.color.ok
            MqttLinkStatus.Connecting -> "Připojování..." to R.color.warn
            MqttLinkStatus.Error -> (state.mqttError.ifBlank { "Chyba sítě" }) to R.color.fault
            MqttLinkStatus.Idle -> "Odpojeno" to R.color.fault
        }
        binding.statusText.text = label
        val color = ContextCompat.getColor(this, colorRes)
        binding.statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        binding.diagLine.text = state.diagLine
        binding.diagLine.setTextColor(
            ContextCompat.getColor(
                this,
                when (state.mqttStatus) {
                    MqttLinkStatus.Error -> R.color.fault
                    MqttLinkStatus.Connected -> R.color.muted
                    else -> R.color.muted
                },
            ),
        )
    }

    private fun renderHome(state: AppUiState) {
        val h = home()
        h.findViewById<TextView>(R.id.totalEnergy).text = state.totalEnergy
        h.findViewById<TextView>(R.id.fvePower).text = state.fvePower
        h.findViewById<ProgressBar>(R.id.fvePowerBar).progress = state.fvePowerPct.toInt()
        h.findViewById<TextView>(R.id.monthEnergy).text = state.monthEnergy
        h.findViewById<TextView>(R.id.realPower).text = state.realPower
        h.findViewById<TextView>(R.id.dailyEnergy).text = state.dailyEnergy
        h.findViewById<ImageView>(R.id.iconRele1).setImageResource(
            if (state.rele1On) R.drawable.ic_panel_on else R.drawable.ic_panel_off,
        )
        h.findViewById<ImageView>(R.id.iconRele2).setImageResource(
            if (state.rele2On) R.drawable.ic_panel_on else R.drawable.ic_panel_off,
        )
        h.findViewById<DailyChartView>(R.id.dailyChart).setPoints(state.chartPoints)
    }

    private fun renderSettings(state: AppUiState) {
        val s = settings()
        val host = s.findViewById<EditText>(R.id.cfgHost)
        val user = s.findViewById<EditText>(R.id.cfgUser)
        val pass = s.findViewById<EditText>(R.id.cfgPass)
        val prefix = s.findViewById<EditText>(R.id.cfgPrefix)
        if (!host.isFocused) host.setTextKeepState(state.draft.host)
        if (!user.isFocused) user.setTextKeepState(state.draft.user)
        if (!pass.isFocused) pass.setTextKeepState(state.draft.password)
        if (!prefix.isFocused) prefix.setTextKeepState(state.draft.prefix)

        val status = s.findViewById<TextView>(R.id.cfgStatus)
        status.text = state.settingsStatus
        status.setTextColor(
            ContextCompat.getColor(
                this,
                when {
                    state.mqttStatus == MqttLinkStatus.Error -> R.color.fault
                    state.mqttConnected -> R.color.ok
                    else -> R.color.muted
                },
            ),
        )
        s.findViewById<View>(R.id.btnConnect).isEnabled = !state.sessionActive
        s.findViewById<View>(R.id.btnConnect).alpha = if (state.sessionActive) 0.45f else 1f
        s.findViewById<View>(R.id.btnDisconnect).isEnabled = state.sessionActive
        s.findViewById<View>(R.id.btnDisconnect).alpha = if (state.sessionActive) 1f else 0.45f
    }

    private fun EditText.setTextKeepState(value: String) {
        if (text?.toString() == value) return
        setText(value)
        setSelection(value.length.coerceAtMost(text?.length ?: 0))
    }
}
