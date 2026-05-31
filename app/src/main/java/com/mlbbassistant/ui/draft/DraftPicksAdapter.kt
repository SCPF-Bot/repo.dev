package com.mlbbassistant.ui.draft

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mlbbassistant.R
import com.mlbbassistant.data.model.Hero
import com.mlbbassistant.databinding.ItemPickChipBinding

/**
 * Displays a flat horizontal list of (Hero, isAlly) pairs.
 * Tapping the close icon on a chip calls [onRemove].
 */
class DraftPicksAdapter(
    private val onRemove: (Hero, isAlly: Boolean) -> Unit
) : ListAdapter<Pair<Hero, Boolean>, DraftPicksAdapter.PickViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PickViewHolder {
        val binding = ItemPickChipBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PickViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PickViewHolder, position: Int) {
        val (hero, isAlly) = getItem(position)
        holder.bind(hero, isAlly)
    }

    inner class PickViewHolder(
        private val binding: ItemPickChipBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(hero: Hero, isAlly: Boolean) {
            binding.chip.text = hero.name
            val colorRes = if (isAlly) R.color.ally_pick_color else R.color.enemy_pick_color
            binding.chip.setChipBackgroundColorResource(colorRes)
            binding.chip.setOnCloseIconClickListener { onRemove(hero, isAlly) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Pair<Hero, Boolean>>() {
            override fun areItemsTheSame(
                old: Pair<Hero, Boolean>, new: Pair<Hero, Boolean>
            ) = old.first.id == new.first.id && old.second == new.second
            override fun areContentsTheSame(
                old: Pair<Hero, Boolean>, new: Pair<Hero, Boolean>
            ) = old == new
        }
    }
}
