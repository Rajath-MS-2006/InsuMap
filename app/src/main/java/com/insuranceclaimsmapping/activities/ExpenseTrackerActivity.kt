package com.insuranceclaimsmapping.activities

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Claim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExpenseTrackerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense_tracker)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarExpense)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Expense Tracker"
        toolbar.setNavigationOnClickListener { finish() }

        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val firebaseHelper = FirebaseHelper()

        firebaseHelper.getClaimsByRole("PATIENT", uid, { claims ->
            populateSummary(claims)
            populateTrendChart(claims)
            populateHospitalChart(claims)
        }, {
            Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
        })
    }

    private fun populateSummary(claims: List<Claim>) {
        val totalBilled = claims.sumOf { it.amount }
        // Out-of-pocket estimate: sum of copayAmounts from adjudicated claims
        val outOfPocket = claims
            .filter { it.status == "ADJUDICATED" }
            .sumOf { it.copayAmount }

        findViewById<TextView>(R.id.tvTotalClaims).text = claims.size.toString()
        findViewById<TextView>(R.id.tvTotalBilled).text = "₹${String.format("%.0f", totalBilled)}"
        findViewById<TextView>(R.id.tvOutOfPocket).text = "₹${String.format("%.0f", outOfPocket)}"
    }

    private fun populateTrendChart(claims: List<Claim>) {
        val chart = findViewById<LineChart>(R.id.lineChartExpense)
        if (claims.isEmpty()) { chart.setNoDataText("No expense data yet"); return }

        // Sort by timestamp, group by month label
        val sorted = claims.sortedBy { it.timestamp }
        val monthFormat = SimpleDateFormat("MMM yy", Locale.getDefault())

        // Build a map of month → total amount
        val monthMap = LinkedHashMap<String, Double>()
        sorted.forEach { claim ->
            val monthKey = monthFormat.format(Date(claim.timestamp))
            monthMap[monthKey] = (monthMap[monthKey] ?: 0.0) + claim.amount
        }

        val labels = monthMap.keys.toList()
        val entries = labels.mapIndexed { idx, _ -> Entry(idx.toFloat(), monthMap[labels[idx]]!!.toFloat()) }

        val dataSet = LineDataSet(entries, "Monthly Spend (₹)").apply {
            color = Color.parseColor("#E65100")
            setCircleColor(Color.parseColor("#E65100"))
            lineWidth = 2f
            circleRadius = 4f
            fillColor = Color.parseColor("#FFCCBC")
            setDrawFilled(true)
            valueTextSize = 10f
        }

        chart.apply {
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
        val chart = findViewById<HorizontalBarChart>(R.id.barChartHospital)
        if (claims.isEmpty()) { chart.setNoDataText("No data yet"); return }

        // Group by hospital
        val hospitalMap = LinkedHashMap<String, Double>()
        claims.forEach { c ->
            val h = c.hospital.ifEmpty { "Unknown" }
            hospitalMap[h] = (hospitalMap[h] ?: 0.0) + c.amount
        }

        val labels = hospitalMap.keys.toList()
        val entries = labels.mapIndexed { idx, label ->
            BarEntry(idx.toFloat(), hospitalMap[label]!!.toFloat())
        }

        val dataSet = BarDataSet(entries, "Spend per Hospital (₹)").apply {
            colors = listOf(
                Color.parseColor("#1565C0"),
                Color.parseColor("#2E7D32"),
                Color.parseColor("#E65100"),
                Color.parseColor("#6A1B9A"),
                Color.parseColor("#00838F")
            )
            valueTextSize = 10f
        }

        chart.apply {
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
                textColor = Color.DKGRAY
            }
            axisLeft.granularity = 1f
            animateY(600)
            invalidate()
        }
    }
}
