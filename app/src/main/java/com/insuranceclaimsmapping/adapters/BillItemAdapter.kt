package com.insuranceclaimsmapping.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.models.BillItem

class BillItemAdapter(private val items: List<BillItem>) : RecyclerView.Adapter<BillItemAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDescription: TextView = view.findViewById(R.id.tvItemDescription)
        val tvAmount: TextView = view.findViewById(R.id.tvItemAmount)
        val tvStatus: TextView = view.findViewById(R.id.tvItemStatus)
        val tvRejected: TextView = view.findViewById(R.id.tvItemRejected)
        val tvReason: TextView = view.findViewById(R.id.tvItemReason)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bill_detail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvDescription.text = item.description
        holder.tvAmount.text = "₹${"%.2f".format(item.amount)}"
        holder.tvStatus.text = "Covered: ₹${"%.2f".format(item.coveredAmount)}"
        val rejected = item.amount - item.coveredAmount
        holder.tvRejected.text = "Rejected: ₹${"%.2f".format(rejected)}"
        holder.tvReason.text = item.reasoning
        
        if (item.status == "REJECTED") {
            holder.tvStatus.alpha = 0.5f
            holder.tvRejected.visibility = View.VISIBLE
        } else {
            holder.tvRejected.visibility = View.GONE
        }
    }

    override fun getItemCount() = items.size
}
