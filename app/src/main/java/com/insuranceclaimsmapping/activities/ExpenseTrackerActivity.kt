package com.insuranceclaimsmapping.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.databinding.ActivityExpenseTrackerBinding
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Claim
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExpenseTrackerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExpenseTrackerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityExpenseTrackerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarExpense)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Expense Tracker"
        binding.toolbarExpense.setNavigationOnClickListener { finish() }

        binding.progressExpense.visibility = View.VISIBLE

        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        FirebaseHelper().getClaimsByRole("PATIENT", uid, { claims ->
            if (isFinishing || isDestroyed) return@getClaimsByRole
            binding.progressExpense.visibility = View.GONE
            populateSummary(claims)
            populateTrendChart(claims)
            populateHospitalChart(claims)
        }, {
            if (isFinishing || isDestroyed) return@getClaimsByRole
            binding.progressExpense.visibility = View.GONE
            Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
        })
    }

    private fun populateSummary(claims: List<Claim>) {
        val totalBilled = claims.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
        val outOfPocket = claims.filter { it.status == "ADJUDICATED" }.sumOf { it.patientLiability }

        // Use safe currency formatting to handle large financial values gracefully
        val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        
        binding.tvTotalClaims.text = claims.size.toString()
        binding.tvTotalBilled.text = formatter.format(totalBilled)
        binding.tvOutOfPocket.text = formatter.format(outOfPocket)
    }

    private fun populateTrendChart(claims: List<Claim>) {
        if (claims.isEmpty()) { binding.lineChartExpense.setNoDataText("No expense data yet"); return }

        val sorted = claims.sortedBy { it.timestamp?.seconds ?: 0L }
        val monthFormat = SimpleDateFormat("MMM yy", Locale.getDefault())
        val monthMap = LinkedHashMap<String, Double>()
        sorted.forEach { claim ->
            val date = claim.timestamp?.toDate() ?: Date(0)
            val key = monthFormat.format(date)
            val amt = claim.amount.toDoubleOrNull() ?: 0.0
            monthMap[key] = (monthMap[key] ?: 0.0) + amt
        }

        val labels = monthMap.keys.toList()
        val entries = labels.mapIndexed { idx, _ -> Entry(idx.toFloat(), (monthMap[labels[idx]] ?: 0.0).toFloat()) }
        val lineColor = getColor(R.color.patient_dark)
        val fillColor = getColor(R.color.yellow_light)

        val dataSet = LineDataSet(entries, "Monthly Spend (₹)").apply {
            color = lineColor
            setCircleColor(lineColor)
            lineWidth = 2f
            circleRadius = 4f
            this.fillColor = fillColor
            setDrawFilled(true)
            valueTextSize = 10f
        }

        binding.lineChartExpense.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            axisRight.isEnabled = false
            legend.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f
                setDrawGridLines(false)
                labelRotationAngle = -30f
            }
            axisLeft.granularity = 1f
            animateX(600)
            invalidate()
        }
    }

    private fun populateHospitalChart(claims: List<Claim>) {
        if (claims.isEmpty()) { binding.barChartHospital.setNoDataText("No data yet"); return }

        val hospitalMap = LinkedHashMap<String, Double>()
        claims.forEach { c ->
            val h = c.hospital.ifEmpty { "Unknown" }
            hospitalMap[h] = (hospitalMap[h] ?: 0.0) + (c.amount.toDoubleOrNull() ?: 0.0)
        }

        val labels = hospitalMap.keys.toList()
        val entries = labels.mapIndexed { idx, _ -> BarEntry(idx.toFloat(), (hospitalMap[labels[idx]] ?: 0.0).toFloat()) }

        val dataSet = BarDataSet(entries, "Spend per Hospital (₹)").apply {
            colors = listOf(
                getColor(R.color.chart_blue),
                getColor(R.color.chart_green),
                getColor(R.color.chart_orange),
                getColor(R.color.chart_purple),
                getColor(R.color.chart_teal)
            )
            valueTextSize = 10f
        }

        binding.barChartHospital.apply {
            data = BarData(dataSet).apply { barWidth = 0.6f }
            description.isEnabled = false
            legend.isEnabled = false
            setFitBars(true)
            axisRight.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f
                setDrawGridLines(false)
                textColor = getColor(R.color.dark_gray)
            }
            axisLeft.granularity = 1f
            animateY(600)
            invalidate()
        }
    }
}
