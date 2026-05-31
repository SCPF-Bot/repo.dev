package com.mlbbassistant.ui.heroes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mlbbassistant.data.model.Hero
import com.mlbbassistant.databinding.ItemHeroBinding

class HeroAdapter(
    private val onHeroClick: ((Hero) -> Unit)? = null
) : ListAdapter<Hero, HeroAdapter.HeroViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeroViewHolder {
        val binding = ItemHeroBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HeroViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HeroViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HeroViewHolder(
        private val binding: ItemHeroBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(hero: Hero) {
            binding.tvHeroName.text = hero.name
            binding.tvHeroRole.text = buildString {
                append(hero.role.displayName)
                hero.secondaryRole?.let { append(" / ${it.displayName}") }
            }
            binding.tvWinRate.text  = "Win: ${"%.1f".format(hero.winRate * 100)}%"
            binding.tvBanRate.text  = "Ban: ${"%.1f".format(hero.banRate * 100)}%"
            binding.tvLane.text     = hero.lane.displayName
            binding.root.setOnClickListener { onHeroClick?.invoke(hero) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Hero>() {
            override fun areItemsTheSame(old: Hero, new: Hero)    = old.id == new.id
            override fun areContentsTheSame(old: Hero, new: Hero) = old == new
        }
    }
}
