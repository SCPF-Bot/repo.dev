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

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(requireContext())) {
            viewModel.setOverlayEnabled(true)
            startOverlayService()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op; notification is non-critical */ }

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
            if (isChecked) {
                requestOverlayAndNotificationPermissions()
            } else {
                viewModel.setOverlayEnabled(false)
                stopOverlayService()
            }
        }

        binding.sliderOpacity.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.setOverlayOpacity(value)
        }

        binding.sliderSuggestions.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.setSuggestionCount(value.toInt())
        }

        binding.btnSaveWeights.setOnClickListener {
            val meta    = binding.sliderWeightMeta.value
            val counter = binding.sliderWeightCounter.value
            val synergy = binding.sliderWeightSynergy.value
            viewModel.setWeights(meta, counter, synergy)
            Snackbar.make(binding.root, R.string.settings_weights_saved, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.overlayEnabled.collect  { binding.switchOverlay.isChecked = it } }
                launch { viewModel.overlayOpacity.collect  { binding.sliderOpacity.value = it } }
                launch { viewModel.suggestionCount.collect { binding.sliderSuggestions.value = it.toFloat() } }
                launch { viewModel.weightMeta.collect      { binding.sliderWeightMeta.value = it } }
                launch { viewModel.weightCounter.collect   { binding.sliderWeightCounter.value = it } }
                launch { viewModel.weightSynergy.collect   { binding.sliderWeightSynergy.value = it } }
                launch {
                    viewModel.overlayEnabled.collect { enabled ->
                        binding.groupOverlayControls.isVisible = enabled
                    }
                }
            }
        }
    }

    private fun requestOverlayAndNotificationPermissions() {
        if (!Settings.canDrawOverlays(requireContext())) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            viewModel.setOverlayEnabled(true)
            startOverlayService()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startOverlayService() {
        requireContext().startForegroundService(
            Intent(requireContext(), OverlayService::class.java)
        )
    }

    private fun stopOverlayService() {
        requireContext().stopService(Intent(requireContext(), OverlayService::class.java))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
