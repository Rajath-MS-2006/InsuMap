package com.insuranceclaimsmapping.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.databinding.ItemBillDetailBinding
import com.insuranceclaimsmapping.models.BillItem

class BillItemAdapter(private val items: List<BillItem>) : RecyclerView.Adapter<BillItemAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemBillDetailBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBillDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvItemDescription.text = item.description
        holder.binding.tvItemAmount.text = "₹${"%.2f".format(item.amount)}"
        holder.binding.tvItemStatus.text = "Covered: ₹${"%.2f".format(item.coveredAmount)}"
        val rejected = item.amount - item.coveredAmount
        holder.binding.tvItemRejected.text = "Rejected: ₹${"%.2f".format(rejected)}"
        holder.binding.tvItemReason.text = item.reasoning
        
        if (item.status == "REJECTED") {
            holder.binding.tvItemStatus.alpha = 0.5f
            holder.binding.tvItemRejected.visibility = View.VISIBLE
        } else {
            holder.binding.tvItemRejected.visibility = View.GONE
        }
        
        if (item.fraudWarning) {
            holder.binding.tvFraudBadgeItem.visibility = View.VISIBLE
            holder.binding.tvItemAmount.setTextColor(holder.itemView.context.getColor(R.color.error_red))
        } else {
            holder.binding.tvFraudBadgeItem.visibility = View.GONE
            holder.binding.tvItemAmount.setTextColor(holder.itemView.context.getColor(R.color.black))
        }
    }

    override fun getItemCount() = items.size
}
