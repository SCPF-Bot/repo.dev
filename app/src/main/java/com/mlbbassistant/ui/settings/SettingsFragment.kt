package com.mlbbassistant.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.mlbbassistant.R
import com.mlbbassistant.databinding.FragmentSettingsBinding
import com.mlbbassistant.overlay.OverlayService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    // Prevents recursive calls when we programmatically flip the switch
    private var suppressSwitchListener = false

    // ── Permission launchers ──────────────────────────────────────────────────

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (!isAdded || _binding == null) return@registerForActivityResult
        if (Settings.canDrawOverlays(requireContext())) {
            viewModel.setOverlayEnabled(true)
            startOverlayService()
        } else {
            suppressSwitchListener = true
            binding.switchOverlay.isChecked = false
            suppressSwitchListener = false
            showSnackbar(R.string.settings_overlay_permission_denied)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* non-critical — notification shows if granted */ }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupControls()
        observeState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Controls setup ────────────────────────────────────────────────────────

    private fun setupControls() {
        binding.switchOverlay.setOnCheckedChangeListener { _, isChecked ->
            if (suppressSwitchListener) return@setOnCheckedChangeListener
            if (isChecked) requestPermissionsAndEnable() else disableOverlay()
        }

        binding.sliderOpacity.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.setOverlayOpacity(value)
        }

        binding.sliderSuggestions.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.setSuggestionCount(value.toInt())
        }

        binding.btnSaveWeights.setOnClickListener {
            if (_binding == null) return@setOnClickListener
            viewModel.setWeights(
                binding.sliderWeightMeta.value,
                binding.sliderWeightCounter.value,
                binding.sliderWeightSynergy.value
            )
            showSnackbar(R.string.settings_weights_saved)
        }

        binding.etApiUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { saveApiUrl(); true } else false
        }
        binding.btnSaveApiUrl.setOnClickListener { saveApiUrl() }
        binding.btnClearApiUrl.setOnClickListener {
            if (_binding == null) return@setOnClickListener
            binding.etApiUrl.setText("")
            viewModel.setApiUrl("")
            showSnackbar(R.string.settings_api_url_cleared)
        }
    }

    // ── State observation ─────────────────────────────────────────────────────

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.overlayEnabled.collect { enabled ->
                        if (_binding == null) return@collect
                        suppressSwitchListener = true
                        binding.switchOverlay.isChecked = enabled
                        suppressSwitchListener = false
                        binding.groupOverlayControls.isVisible = enabled
                    }
                }
                launch {
                    viewModel.overlayOpacity.collect {
                        _binding?.sliderOpacity?.value = it
                    }
                }
                launch {
                    viewModel.suggestionCount.collect {
                        _binding?.sliderSuggestions?.value = it.toFloat()
                    }
                }
                launch { viewModel.weightMeta.collect    { _binding?.sliderWeightMeta.value = it } }
                launch { viewModel.weightCounter.collect { _binding?.sliderWeightCounter.value = it } }
                launch { viewModel.weightSynergy.collect { _binding?.sliderWeightSynergy.value = it } }
                launch {
                    viewModel.apiUrl.collect { url ->
                        val et = _binding?.etApiUrl ?: return@collect
                        if (!et.hasFocus()) et.setText(url)
                    }
                }
            }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun saveApiUrl() {
        if (_binding == null) return
        val url = binding.etApiUrl.text?.toString().orEmpty().trim()
        viewModel.setApiUrl(url)
        hideKeyboard()
        showSnackbar(if (url.isBlank()) R.string.settings_api_url_cleared else R.string.settings_api_url_saved)
    }

    private fun hideKeyboard() {
        val b = _binding ?: return
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(b.root.windowToken, 0)
    }

    private fun requestPermissionsAndEnable() {
        if (!Settings.canDrawOverlays(requireContext())) {
            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")
                )
            )
        } else {
            viewModel.setOverlayEnabled(true)
            startOverlayService()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun disableOverlay() {
        viewModel.setOverlayEnabled(false)
        runCatching {
            requireContext().stopService(Intent(requireContext(), OverlayService::class.java))
        }
    }

    private fun startOverlayService() {
        runCatching {
            requireContext().startForegroundService(
                Intent(requireContext(), OverlayService::class.java)
            )
        }.onFailure {
            if (_binding != null) showSnackbar(R.string.settings_overlay_start_failed)
        }
    }

    private fun showSnackbar(resId: Int) {
        val b = _binding ?: return
        Snackbar.make(b.root, resId, Snackbar.LENGTH_SHORT).show()
    }
}
