package com.mlbbassistant.ui.heroes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.mlbbassistant.R
import com.mlbbassistant.core.Resource
import com.mlbbassistant.data.model.HeroRole
import com.mlbbassistant.databinding.FragmentHeroesBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HeroesFragment : Fragment() {

    private var _binding: FragmentHeroesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HeroesViewModel by viewModels()
    private lateinit var adapter: HeroAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHeroesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        setupRoleChips()
        setupSwipeRefresh()
        observeState()
        viewModel.refresh()
    }

    private fun setupRecyclerView() {
        adapter = HeroAdapter()
        binding.rvHeroes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HeroesFragment.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })
    }

    private fun setupRoleChips() {
        binding.chipAll.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.setRoleFilter(null)
        }
        HeroRole.entries.filter { it != HeroRole.UNKNOWN }.forEach { role ->
            val chip = Chip(requireContext()).apply {
                text = role.displayName
                isCheckable = true
                setOnCheckedChangeListener { _, checked ->
                    if (checked) viewModel.setRoleFilter(role)
                }
            }
            binding.chipGroupRoles.addView(chip)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.heroes.collect { heroes ->
                        if (_binding == null) return@collect
                        adapter.submitList(heroes)
                        binding.tvEmpty.isVisible = heroes.isEmpty()
                    }
                }

                launch {
                    viewModel.refreshState.collect { state ->
                        if (_binding == null) return@collect
                        binding.swipeRefresh.isRefreshing = state is Resource.Loading
                        when (state) {
                            is Resource.Error -> {
                                Snackbar.make(
                                    binding.root,
                                    getString(R.string.error_refresh, state.message),
                                    Snackbar.LENGTH_LONG
                                ).show()
                                viewModel.consumeRefreshState()
                            }
                            is Resource.Success -> viewModel.consumeRefreshState()
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        // Clear adapter before nulling binding to avoid RecyclerView leak
        binding.rvHeroes.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
