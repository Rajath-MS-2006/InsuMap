package com.insuranceclaimsmapping.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.insuranceclaimsmapping.databinding.ItemDashboardBinding
import com.insuranceclaimsmapping.models.DashboardItem

class DashboardAdapter(
    private val items: List<DashboardItem>,
    private val onItemClick: (DashboardItem) -> Unit
) : RecyclerView.Adapter<DashboardAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemDashboardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDashboardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvDashboardTitle.text = item.title
        holder.binding.ivDashboardIcon.setImageResource(item.iconResId)
        holder.binding.cardDashboard.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}
