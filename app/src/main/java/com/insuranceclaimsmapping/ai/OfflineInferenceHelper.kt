package com.insuranceclaimsmapping.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.insuranceclaimsmapping.models.BillItem
import com.insuranceclaimsmapping.models.Claim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode

class OfflineInferenceHelper(private val context: Context) {
    
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractPolicyDetails(pdfUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Copy to local cache to use with PdfRenderer
                val tempFile = File.createTempFile("policy_tmp", ".pdf", context.cacheDir)
                context.contentResolver.openInputStream(pdfUri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                
                val extractedText = StringBuilder()
                
                // Read up to first 3 pages
                val pagesToRead = minOf(3, renderer.pageCount)
                for (i in 0 until pagesToRead) {
                    val page = renderer.openPage(i)
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val text = textRecognizer.process(image).await()
                    extractedText.append(text.text).append("\n")
                    
                    page.close()
                }
                
                renderer.close()
                pfd.close()
                tempFile.delete()

                val rawText = extractedText.toString()
                
                // Basic heuristic extraction for Copay and Deductible
                var copay = 20.0
                var deductible = 500.0
                
                val copayRegex = Regex("(?i)copay.*?(\\d+)%")
                val dedRegex = Regex("(?i)deductible.*?\\$(\\d+)")
                
                copayRegex.find(rawText)?.let {
                    copay = it.groupValues[1].toDoubleOrNull() ?: 20.0
                }
                dedRegex.find(rawText)?.let {
                    deductible = it.groupValues[1].toDoubleOrNull() ?: 500.0
                }
                
                "Policy Rules Extracted Locally (Offline):\nCopay: $copay%\nDeductible: $$deductible\nAdditional details sanitized."
            } catch (e: Exception) {
                Log.e("OfflineInference", "Error extracting PDF", e)
                null
            }
        }
    }

    suspend fun extractItemizedBill(billBitmap: Bitmap): ExtractionResult {
        return withContext(Dispatchers.IO) {
            try {
                val image = InputImage.fromBitmap(billBitmap, 0)
                val textResult = textRecognizer.process(image).await()
                
                val items = mutableListOf<BillItem>()
                val moneyRegex = Regex("[$]?\\d+\\.\\d{2}")
                
                // Extremely efficient local heuristic parsing instead of LLM
                for (block in textResult.textBlocks) {
                    for (line in block.lines) {
                        val lineText = line.text
                        val moneyMatch = moneyRegex.find(lineText)
                        if (moneyMatch != null) {
                            val amountStr = moneyMatch.value.replace("$", "")
                            val amount = amountStr.toDoubleOrNull() ?: 0.0
                            val desc = lineText.replace(moneyMatch.value, "").trim()
                            
                            if (amount > 0 && desc.isNotEmpty()) {
                                items.add(BillItem(description = desc, amount = amount))
                            }
                        }
                    }
                }
                
                // Redact PII (Patient Name/Hospital are anonymized locally)
                ExtractionResult(
                    hospitalName = "REDACTED_HOSPITAL_FOR_PRIVACY",
                    patientName = "REDACTED_PATIENT_FOR_PRIVACY",
                    items = items
                )
            } catch (e: Exception) {
                Log.e("OfflineInference", "Error extracting bill", e)
                ExtractionResult()
            }
        }
    }

    data class ExtractionResult(
        val hospitalName: String = "",
        val patientName: String = "",
        val items: List<BillItem> = emptyList()
    )

    suspend fun adjudicateSingleClaim(claim: Claim, policyRules: String): Claim {
        return withContext(Dispatchers.Default) {
            try {
                // NATIVE DETERMINISTIC MATH (Benchmark-ready)
                val totalAmount = BigDecimal(claim.amount.ifEmpty { "0.0" })
                
                // Parse simple rules from string
                val copayPct = extractCopay(policyRules)
                
                // Calculate
                val copayRatio = BigDecimal(copayPct).divide(BigDecimal("100"), 4, RoundingMode.HALF_UP)
                val patientLiability = totalAmount.multiply(copayRatio).setScale(2, RoundingMode.HALF_UP)
                val coveredAmount = totalAmount.subtract(patientLiability).setScale(2, RoundingMode.HALF_UP)
                
                val isFraud = totalAmount > BigDecimal("50000.0") // Simple offline fraud threshold rule
                
                claim.copy(
                    status = if (coveredAmount > BigDecimal.ZERO) "APPROVED" else "REJECTED",
                    coveredAmount = coveredAmount.toDouble(),
                    patientLiability = patientLiability.toDouble(),
                    aiReasoning = "Adjudicated deterministically via Local Logic Engine. Applied $copayPct% copay.",
                    fraudWarning = isFraud,
                    fraudReasoning = if (isFraud) "Exceeds standard threshold." else "",
                    timestamp = com.google.firebase.Timestamp.now()
                )
            } catch (e: Exception) {
                Log.e("OfflineInference", "Error adjudicating claim", e)
                claim.copy(aiReasoning = "Error: ${e.message}")
            }
        }
    }

    suspend fun adjudicateItemized(items: List<BillItem>, policyRules: String): List<BillItem> {
        return withContext(Dispatchers.Default) {
            try {
                val copayPct = extractCopay(policyRules)
                val copayRatio = BigDecimal(copayPct).divide(BigDecimal("100"), 4, RoundingMode.HALF_UP)
                
                items.map { item ->
                    val amt = BigDecimal(item.amount.toString())
                    val patientL = amt.multiply(copayRatio).setScale(2, RoundingMode.HALF_UP)
                    val covered = amt.subtract(patientL).setScale(2, RoundingMode.HALF_UP)
                    
                    val isFraud = amt > BigDecimal("5000.0")
                    
                    item.copy(
                        coveredAmount = covered.toDouble(),
                        status = if (covered > BigDecimal.ZERO) "ADJUDICATED" else "REJECTED",
                        reasoning = "Local Engine: $copayPct% copay applied.",
                        fraudWarning = isFraud
                    )
                }
            } catch (e: Exception) {
                Log.e("OfflineInference", "Error in itemized adjudication", e)
                items
            }
        }
    }
    
    private fun extractCopay(rules: String): String {
        val copayRegex = Regex("Copay: (\\d+(?:\\.\\d+)?)%")
        val match = copayRegex.find(rules)
        return match?.groupValues?.get(1) ?: "20"
    }
}
