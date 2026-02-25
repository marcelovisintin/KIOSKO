package com.schneider.kiosko

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.schneider.kiosko.databinding.ItemAllowedAppBinding

class AllowedAppsAdapter(
    private val onOpenClicked: (AllowedApp) -> Unit,
) : RecyclerView.Adapter<AllowedAppsAdapter.AllowedAppViewHolder>() {

    private val items = mutableListOf<AllowedApp>()

    fun submitList(newItems: List<AllowedApp>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AllowedAppViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemAllowedAppBinding.inflate(inflater, parent, false)
        return AllowedAppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AllowedAppViewHolder, position: Int) {
        holder.bind(items[position], onOpenClicked)
    }

    override fun getItemCount(): Int = items.size

    class AllowedAppViewHolder(
        private val binding: ItemAllowedAppBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AllowedApp, onOpenClicked: (AllowedApp) -> Unit) {
            binding.appIconImageView.setImageDrawable(item.icon)
            binding.appLabelTextView.text = item.label
            binding.appPackageTextView.text = item.packageName
            binding.root.setOnClickListener { onOpenClicked(item) }
        }
    }
}
