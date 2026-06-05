package com.mlbbassistant.ui.draft

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mlbbassistant.data.model.DraftSuggestion
import com.mlbbassistant.databinding.ItemSuggestionBinding

class SuggestionAdapter(
    private val onSuggestionClick: (DraftSuggestion) -> Unit
) : ListAdapter<DraftSuggestion, SuggestionAdapter.SuggestionViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val binding = ItemSuggestionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    inner class SuggestionViewHolder(
        private val binding: ItemSuggestionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(suggestion: DraftSuggestion, rank: Int) {
            binding.tvRank.text          = "#$rank"
            binding.tvSuggestionName.text = suggestion.hero.name
            binding.tvSuggestionRole.text = suggestion.hero.role.displayName
            binding.tvScore.text         = "${"%.0f".format(suggestion.score * 100)}pts"
            binding.tvReason.text        = suggestion.reason
            // Use setProgress(value, animated=false) — the proper LinearProgressIndicator API
            binding.progressScore.setProgress((suggestion.score * 100).toInt(), false)
            binding.root.setOnClickListener { onSuggestionClick(suggestion) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DraftSuggestion>() {
            override fun areItemsTheSame(old: DraftSuggestion, new: DraftSuggestion) =
                old.hero.id == new.hero.id
            override fun areContentsTheSame(old: DraftSuggestion, new: DraftSuggestion) =
                old == new
        }
    }
}
