package com.insuranceclaimsmapping.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.databinding.ItemClaimBinding
import com.insuranceclaimsmapping.models.Claim

class ClaimAdapter(private var claims: List<Claim>) :
    RecyclerView.Adapter<ClaimAdapter.ClaimViewHolder>() {

    private var onItemClickListener: ((Claim) -> Unit)? = null

    fun setOnItemClickListener(listener: (Claim) -> Unit) {
        onItemClickListener = listener
    }

    class ClaimViewHolder(val binding: ItemClaimBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClaimViewHolder {
        val binding = ItemClaimBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ClaimViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClaimViewHolder, position: Int) {
        val claim = claims[position]
        val context = holder.itemView.context
        
        holder.binding.tvItemHospital.text = claim.hospital
        holder.binding.tvItemAmount.text = "Amount: ₹${claim.amount}"
        val idDisplay = if (claim.customPatientId.isNotEmpty()) " (${claim.customPatientId})" else ""
        holder.binding.tvItemName.text = "Patient: ${claim.name}$idDisplay"
        holder.binding.tvItemStatus.text = claim.status
        
        // Dynamic status background
        when (claim.status) {
            "ADJUDICATED", "APPROVED" -> holder.binding.tvItemStatus.setBackgroundResource(R.drawable.bg_status_approved)
            "REJECTED" -> holder.binding.tvItemStatus.setBackgroundResource(R.drawable.bg_status_pending)
            "APPEAL_PENDING" -> holder.binding.tvItemStatus.setBackgroundResource(R.drawable.bg_status_pending)
            else -> holder.binding.tvItemStatus.setBackgroundResource(R.drawable.bg_status_pending)
        }

        holder.itemView.setOnClickListener {
            if (onItemClickListener != null) {
                onItemClickListener?.invoke(claim)
            } else {
                val intent = android.content.Intent(context, com.insuranceclaimsmapping.activities.ClaimDetailActivity::class.java)
                intent.putExtra("claimId", claim.id)
                context.startActivity(intent)
            }
        }
    }

    override fun getItemCount(): Int = claims.size

    fun updateData(newClaims: List<Claim>) {
        claims = newClaims
        notifyDataSetChanged()
    }

    fun getCurrentList(): List<Claim> = claims
}

