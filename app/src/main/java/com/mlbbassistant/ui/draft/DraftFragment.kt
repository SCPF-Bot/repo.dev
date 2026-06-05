package com.mlbbassistant.ui.draft

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mlbbassistant.R
import com.mlbbassistant.data.model.DraftState
import com.mlbbassistant.data.model.Hero
import com.mlbbassistant.databinding.FragmentDraftBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DraftFragment : Fragment() {

    private var _binding: FragmentDraftBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DraftViewModel by viewModels()
    private lateinit var suggestionAdapter: SuggestionAdapter
    private lateinit var picksAdapter: DraftPicksAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = FragmentDraftBinding.inflate(inflater, container, false)
        .also { _binding = it }
        .root

    override fun onViewCreated(
        view: android.view.View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupButtons()
        observeState()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupAdapters() {
        suggestionAdapter = SuggestionAdapter { suggestion ->
            if (isAdded) showAddPickDialog(suggestion.hero)
        }
        binding.rvSuggestions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = suggestionAdapter
        }

        picksAdapter = DraftPicksAdapter { hero, isAlly ->
            if (isAlly) viewModel.removeAllyPick(hero) else viewModel.removeEnemyPick(hero)
        }
        binding.rvPicks.apply {
            layoutManager = LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false
            )
            adapter = picksAdapter
        }
    }

    private fun setupButtons() {
        binding.btnReset.setOnClickListener {
            if (!isAdded) return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.draft_reset_title)
                .setMessage(R.string.draft_reset_message)
                .setPositiveButton(R.string.action_reset)  { _, _ -> viewModel.resetDraft() }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }

    // ── Observation ───────────────────────────────────────────────────────────

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.suggestions.collect { suggestions ->
                        if (_binding == null) return@collect
                        suggestionAdapter.submitList(suggestions)
                    }
                }
                launch {
                    viewModel.draftState.collect { state ->
                        if (_binding == null) return@collect
                        updatePicksDisplay(state)
                        binding.tvDraftStatus.text = buildDraftStatus(state)
                    }
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun updatePicksDisplay(state: DraftState) {
        val combined = state.allyPicks.map { it to true } +
                state.enemyPicks.map { it to false }
        picksAdapter.submitList(combined)
    }

    private fun buildDraftStatus(state: DraftState) =
        "Ally ${state.allyPicks.size}/5  ·  Enemy ${state.enemyPicks.size}/5  ·  Bans ${state.bans.size}/10"

    private fun showAddPickDialog(hero: Hero) {
        if (!isAdded || activity == null) return
        val options = arrayOf(
            getString(R.string.draft_add_ally),
            getString(R.string.draft_add_enemy),
            getString(R.string.draft_add_ban)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(hero.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.addAllyPick(hero)
                    1 -> viewModel.addEnemyPick(hero)
                    2 -> viewModel.addBan(hero)
                }
            }
            .show()
    }

    override fun onDestroyView() {
        binding.rvSuggestions.adapter = null
        binding.rvPicks.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
