package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.databinding.ActivityOnboardingBinding
import com.insuranceclaimsmapping.utils.PrefManager

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val titles = listOf(
        "Welcome to InsuMap",
        "For Patients",
        "For Hospitals",
        "For Insurers",
        "AI-Powered"
    )
    private val descriptions = listOf(
        "InsuMap connects Patients, Hospitals, and Insurers on one platform to make insurance claims fast, transparent, and smart.",
        "Submit claims, track your expenses, predict out-of-pocket costs, and view your digital insurance card — all in one place.",
        "Upload patient bills by scanning or PDF. Link bills to patient accounts using their Patient ID for seamless processing.",
        "Upload your policy PDF and let AI extract coverage rules. Adjudicate claims in bulk and monitor fraud alerts on your dashboard.",
        "Powered by Google Gemini AI — bills are scanned, policies are parsed, and claims are adjudicated automatically with full reasoning."
    )
    private val icons = listOf(
        R.drawable.logo,
        R.drawable.ic_medical_claim,
        R.drawable.ic_add_claim,
        R.drawable.ic_profile,
        R.drawable.ic_history
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Build dot indicators
        val dots = Array(titles.size) {
            TextView(this).apply {
                text = "●"
                textSize = 12f
                setPadding(8, 0, 8, 0)
                setTextColor(android.graphics.Color.LTGRAY)
            }
        }
        dots.forEach { binding.llDots.addView(it) }

        fun updatePage(pos: Int) {
            binding.tvOnboardingTitle.text = titles[pos]
            binding.tvOnboardingDesc.text = descriptions[pos]
            binding.ivOnboardingIcon.setImageResource(icons[pos])
            dots.forEachIndexed { i, dot ->
                dot.setTextColor(
                    if (i == pos) getColor(R.color.insurer_primary)
                    else android.graphics.Color.LTGRAY
                )
            }
            binding.btnOnboardingNext.text = if (pos == titles.size - 1) "Get Started" else "Next"
        }

        updatePage(0)

        // Proper adapter — each page is a lightweight empty view, content is driven by callback
        binding.viewPagerOnboarding.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = View(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                return object : RecyclerView.ViewHolder(v) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
            override fun getItemCount() = titles.size
        }

        binding.viewPagerOnboarding.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { updatePage(position) }
        })

        binding.btnOnboardingNext.setOnClickListener {
            val current = binding.viewPagerOnboarding.currentItem
            if (current < titles.size - 1) {
                binding.viewPagerOnboarding.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }

        binding.tvOnboardingSkip.setOnClickListener { finishOnboarding() }
    }

    private fun finishOnboarding() {
        PrefManager(this).setOnboardingShown(true)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
