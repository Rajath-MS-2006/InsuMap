package com.insuranceclaimsmapping.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.models.Claim

class ClaimAdapter(private var claims: List<Claim>) :
    RecyclerView.Adapter<ClaimAdapter.ClaimViewHolder>() {

    class ClaimViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvHospital: TextView = itemView.findViewById(R.id.tvItemHospital)
        val tvAmount: TextView = itemView.findViewById(R.id.tvItemAmount)
        val tvName: TextView = itemView.findViewById(R.id.tvItemName)
        val tvStatus: TextView = itemView.findViewById(R.id.tvItemStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClaimViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_claim, parent, false)
        return ClaimViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClaimViewHolder, position: Int) {
        val claim = claims[position]
        val context = holder.itemView.context
        
        holder.tvHospital.text = claim.hospital
        holder.tvAmount.text = "Amount: ₹${claim.amount}"
        val idDisplay = if (claim.customPatientId.isNotEmpty()) " (${claim.customPatientId})" else ""
        holder.tvName.text = "Patient: ${claim.name}$idDisplay"
        holder.tvStatus.text = claim.status
        
        // Dynamic status background
        if (claim.status == "ADJUDICATED") {
            holder.tvStatus.setBackgroundResource(R.drawable.bg_status_approved)
        } else {
            holder.tvStatus.setBackgroundResource(R.drawable.bg_status_pending)
        }

        holder.itemView.setOnClickListener {
            val intent = android.content.Intent(context, com.insuranceclaimsmapping.activities.ClaimDetailActivity::class.java)
            intent.putExtra("claimId", claim.id)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = claims.size

    fun updateData(newClaims: List<Claim>) {
        claims = newClaims
        notifyDataSetChanged()
    }

    fun getCurrentList(): List<Claim> = claims
}

