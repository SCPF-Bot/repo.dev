package com.mlbbassistant.overlay

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mlbbassistant.data.model.DraftSuggestion
import com.mlbbassistant.databinding.ItemOverlaySuggestionBinding

class OverlaySuggestionAdapter :
    ListAdapter<DraftSuggestion, OverlaySuggestionAdapter.OverlayViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OverlayViewHolder {
        val binding = ItemOverlaySuggestionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return OverlayViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OverlayViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    class OverlayViewHolder(
        private val binding: ItemOverlaySuggestionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(suggestion: DraftSuggestion, rank: Int) {
            binding.tvOverlayRank.text = "$rank."
            binding.tvOverlayName.text = suggestion.hero.name
            binding.tvOverlayScore.text = "${"%.0f".format(suggestion.score * 100)}"
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
