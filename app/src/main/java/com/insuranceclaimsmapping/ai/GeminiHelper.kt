package com.insuranceclaimsmapping.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.insuranceclaimsmapping.models.Claim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.delay
import com.insuranceclaimsmapping.BuildConfig
import com.google.firebase.Timestamp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class GeminiHelper(private val context: Context) {
    private val generativeModel = GenerativeModel(
        modelName = "gemini-3.1-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            temperature = 0.0f
        },
        requestOptions = RequestOptions(apiVersion = "v1beta")
    )

    suspend fun extractPolicyDetails(pdfUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(pdfUri)
                val bytes = inputStream?.readBytes() ?: return@withContext null
                
                val inputContent = content {
                    blob("application/pdf", bytes)
                    text("Extract real coverage details, copay percentages, and deductible limits from this insurance policy based ONLY on the provided text. Return a concise bulleted summary of medical coverage rules. Be clinical, thorough, and do not make up arbitrary rules.")
                }
                
                val response = withRetry { generativeModel.generateContent(inputContent) }
                response.text ?: "No valid text extracted."
            } catch (e: Exception) {
                Log.e("GeminiHelper", "Error extracting PDF after retries", e)
                null
            }
        }
    }

    suspend fun extractItemizedBill(billBitmap: Bitmap): ExtractionResult {
        return withContext(Dispatchers.IO) {
            try {
                // 1. OCR Phase
                val softwareBitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && billBitmap.config == Bitmap.Config.HARDWARE) {
                    billBitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    billBitmap
                }
                val image = InputImage.fromBitmap(softwareBitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val ocrResult = recognizer.process(image).await()
                val rawText = ocrResult.text
                
                Log.d("GeminiHelper", "OCR Extracted Text Length: ${rawText.length}")
                if (rawText.isBlank()) {
                    Log.w("GeminiHelper", "OCR returned blank text. Check if the image contains readable text.")
                    return@withContext ExtractionResult()
                }

                // 2. Structuring Phase
                val inputContent = content {
                    text("""
                        Analyze this medical bill text thoroughly and carefully.
                        --- RAW TEXT START ---
                        $rawText
                        --- RAW TEXT END ---
                        Extract the EXACT hospital name, patient name, and a complete itemized list of EVERY charge (description and amount) present on the document.
                        Do NOT redact any data (e.g., do not output "REDACTED"); return the exact names and details as they appear.
                        Do NOT use hospital taglines (like "Touching lives") as the patient name. Ensure you accurately identify the true patient name.
                        Do NOT hallucinate or make up any names, hospitals, or items. Only use what is clearly visible in the text.
                        If a field is not readable, use "Not Found" for strings or 0.0 for amounts.
                        Return as valid JSON with keys: 'hospitalName', 'patientName', 'items' (array of objects with 'description' and 'amount').
                        Ensure the output is a valid JSON. Capture EVERY single row of the bill, do not stop after one item.
                    """.trimIndent())
                }
                
                val response = withRetry { generativeModel.generateContent(inputContent) }
                val text = response.text
                Log.d("GeminiHelper", "Gemini Raw Response: $text")
                
                if (text == null) {
                    Log.w("GeminiHelper", "Gemini returned null text.")
                    return@withContext ExtractionResult()
                }
                
                parseExtractionResult(text)
            } catch (e: Exception) {
                Log.e("GeminiHelper", "Error extracting bill: ${e.message}", e)
                ExtractionResult()
            }
        }
    }

    private fun parseExtractionResult(jsonString: String): ExtractionResult {
        try {
            val cleanJson = jsonString.trim().removeSurrounding("```json", "```").removeSurrounding("```").trim()
            val json = JSONObject(cleanJson)
            val hospitalName = json.optString("hospitalName", "").trim()
            val patientName = json.optString("patientName", "").trim()
            val itemsArray = json.optJSONArray("items") ?: JSONArray()
            
            val billItems = mutableListOf<com.insuranceclaimsmapping.models.BillItem>()
            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(i)
                billItems.add(com.insuranceclaimsmapping.models.BillItem(
                    description = itemObj.optString("description", ""),
                    amount = itemObj.optDouble("amount", 0.0)
                ))
            }
            return ExtractionResult(hospitalName, patientName, billItems)
        } catch (e: Exception) {
            return ExtractionResult()
        }
    }

    data class ExtractionResult(
        val hospitalName: String = "",
        val patientName: String = "",
        val items: List<com.insuranceclaimsmapping.models.BillItem> = emptyList()
    )
 
    private suspend fun <T> withRetry(
        maxRetries: Int = 5,
        initialDelay: Long = 5000,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        val retryPattern = "Please retry in ([\\d.]+)s".toRegex()
        
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                val msg = e.message ?: ""
                val isQuotaError = msg.contains("Quota", ignoreCase = true) || msg.contains("429")
                
                if (isQuotaError && attempt < maxRetries - 1) {
                    val match = retryPattern.find(msg)
                    val waitTimeMs = if (match != null) {
                        (match.groupValues[1].toDouble() * 1000).toLong() + 2000
                    } else {
                        currentDelay
                    }
                    
                    Log.w("GeminiHelper", "[Quota] Limit reached. Cooling down for ${waitTimeMs}ms... (Attempt ${attempt + 1})")
                    delay(waitTimeMs)
                    currentDelay *= 2
                } else {
                    throw e
                }
            }
        }
        return block()
    }

    suspend fun adjudicateSingleClaim(claim: Claim, policyRules: String): Claim {
        return withContext(Dispatchers.IO) {
            try {
                val itemsContext = if (claim.items.isNotEmpty()) {
                    "Itemized Bill Details: \n" + claim.items.joinToString("\n") { 
                        "- ${it.description}: ${it.amount}" 
                    }
                } else {
                    "Bill Description: ${claim.description}"
                }

                val prompt = """
                    Adjudicate this insurance claim based strictly on the provided policy rules.
                    Total Claim Amount: ${claim.amount}
                    ${itemsContext}
                    Policy Rules: $policyRules
                    
                    Respond with a valid JSON object only. Keys: 
                    "status" (APPROVED, REJECTED, PARTIAL), 
                    "coveredAmount" (number), 
                    "patientLiability" (number), 
                    "aiReasoning" (string explaining the calculation and why - mentioning specific items if they caused a rejection based on the policy).
                    Ensure the math is correct according to the policy rules. Do NOT make up rules or hallucinations.
                """.trimIndent()

                val response = withRetry { generativeModel.generateContent(prompt) }
                val text = response.text ?: ""
                val cleanJson = text.substringAfter("```json").substringBeforeLast("```").trim()
                val json = try {
                    JSONObject(cleanJson.ifEmpty { text.trim() })
                } catch (e: Exception) {
                    JSONObject(text.trim()) 
                }
                
                claim.copy(
                    status = json.optString("status", "PENDING"),
                    coveredAmount = json.optDouble("coveredAmount", 0.0),
                    patientLiability = json.optDouble("patientLiability", 0.0),
                    aiReasoning = json.optString("aiReasoning", "Processed by Gemini"),
                    timestamp = Timestamp.now()
                )
            } catch (e: Exception) {
                Log.e("GeminiHelper", "Error adjudicating claim", e)
                claim.copy(aiReasoning = "Error: ${e.message}")
            }
        }
    }

    suspend fun adjudicateItemized(items: List<com.insuranceclaimsmapping.models.BillItem>, policyRules: String): List<com.insuranceclaimsmapping.models.BillItem> {
        return withContext(Dispatchers.IO) {
            try {
                val itemsJson = JSONArray()
                items.forEach { item ->
                    itemsJson.put(JSONObject().apply {
                        put("description", item.description)
                        put("amount", item.amount)
                    })
                }

                val prompt = """
                    Adjudicate these bill items based on the policy rules.
                    Policy: $policyRules
                    Items: $itemsJson
                    
                    Return a JSON array of objects with keys: "description", "amount", "coveredAmount", "aiReasoning". 
                    Ensure it is a valid JSON array only.
                """.trimIndent()

                val response = withRetry { generativeModel.generateContent(prompt) }
                val text = response.text ?: ""
                val cleanJson = text.substringAfter("```json").substringBeforeLast("```").trim()
                
                val resultArray = try {
                    if (cleanJson.startsWith("{")) {
                        // Handle wrapped object e.g. {"items": [...]} or {"adjudicatedItems": [...]}
                        val obj = JSONObject(cleanJson)
                        obj.optJSONArray("items") ?: obj.optJSONArray("adjudicatedItems") ?: JSONArray()
                    } else {
                        JSONArray(cleanJson.ifEmpty { text.trim() })
                    }
                } catch (e: Exception) {
                    Log.e("GeminiHelper", "JSON Parsing failed, trying raw text fallback", e)
                    try { JSONArray(text.trim()) } catch (inner: Exception) { JSONArray() }
                }

                val adjudicatedList = mutableListOf<com.insuranceclaimsmapping.models.BillItem>()
                for (i in 0 until resultArray.length()) {
                    val obj = resultArray.getJSONObject(i)
                    adjudicatedList.add(com.insuranceclaimsmapping.models.BillItem(
                        description = obj.optString("description", "Unknown Item"),
                        amount = obj.optDouble("amount", 0.0),
                        coveredAmount = obj.optDouble("coveredAmount", 0.0),
                        status = if (obj.optDouble("coveredAmount", 0.0) > 0) "ADJUDICATED" else "REJECTED",
                        reasoning = obj.optString("aiReasoning", obj.optString("reasoning", "Processed via AI"))
                    ))
                }
                adjudicatedList
            } catch (e: Exception) {
                Log.e("GeminiHelper", "Error in itemized adjudication", e)
                items // Fallback to original items
            }
        }
    }

    suspend fun calculateAdjudicationBatch(claims: List<Claim>, policyRules: String): String? {
        return "Success"
    }
}
