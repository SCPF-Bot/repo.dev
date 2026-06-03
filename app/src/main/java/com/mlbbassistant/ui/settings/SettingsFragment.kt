package com.mlbbassistant.ui.settings

import android.Manifest
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

    /** Suppress the switch listener while we're programmatically restoring state. */
    private var suppressSwitchListener = false

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
            Snackbar.make(binding.root, R.string.settings_overlay_permission_denied, Snackbar.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* non-critical */ }

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
            Snackbar.make(binding.root, R.string.settings_weights_saved, Snackbar.LENGTH_SHORT).show()
        }

        // API URL input
        binding.etApiUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveApiUrl(); true
            } else false
        }
        binding.btnSaveApiUrl.setOnClickListener { saveApiUrl() }
        binding.btnClearApiUrl.setOnClickListener {
            binding.etApiUrl.setText("")
            viewModel.setApiUrl("")
            Snackbar.make(binding.root,
                R.string.settings_api_url_cleared, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun saveApiUrl() {
        if (_binding == null) return
        val url = binding.etApiUrl.text?.toString().orEmpty().trim()
        viewModel.setApiUrl(url)
        hideKeyboard()
        val msg = if (url.isBlank()) R.string.settings_api_url_cleared
                  else R.string.settings_api_url_saved
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
    }

    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

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
                launch { viewModel.overlayOpacity.collect  { if (_binding != null) binding.sliderOpacity.value = it } }
                launch { viewModel.suggestionCount.collect { if (_binding != null) binding.sliderSuggestions.value = it.toFloat() } }
                launch { viewModel.weightMeta.collect      { if (_binding != null) binding.sliderWeightMeta.value = it } }
                launch { viewModel.weightCounter.collect   { if (_binding != null) binding.sliderWeightCounter.value = it } }
                launch { viewModel.weightSynergy.collect   { if (_binding != null) binding.sliderWeightSynergy.value = it } }
                launch {
                    viewModel.apiUrl.collect { url ->
                        if (_binding == null) return@collect
                        // Only update if the field is not currently focused to avoid cursor jump
                        if (!binding.etApiUrl.hasFocus()) {
                            binding.etApiUrl.setText(url)
                        }
                    }
                }
            }
        }
    }

    private fun requestPermissionsAndEnable() {
        if (!Settings.canDrawOverlays(requireContext())) {
            overlayPermissionLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}"))
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
            Snackbar.make(binding.root, R.string.settings_overlay_start_failed, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
