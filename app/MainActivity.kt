
package com.silleng.aitrading

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var apiKeyInput: EditText
    private lateinit var marketInput: EditText
    private lateinit var resultText: TextView
    private lateinit var analyzeButton: Button
    private lateinit var chartWebView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        apiKeyInput = findViewById(R.id.apiKeyInput)
        marketInput = findViewById(R.id.marketInput)
        resultText = findViewById(R.id.resultText)
        analyzeButton = findViewById(R.id.analyzeButton)
        chartWebView = findViewById(R.id.chartWebView)

        val preferences = getSharedPreferences(
            "SILLENG_SETTINGS",
            MODE_PRIVATE
        )

        apiKeyInput.setText(
            preferences.getString("GEMINI_API_KEY", "")
        )

        chartWebView.settings.javaScriptEnabled = true
        chartWebView.webViewClient = WebViewClient()
        chartWebView.loadUrl("file:///android_asset/index.html")

        findViewById<Button>(R.id.saveKeyButton).setOnClickListener {
            val key = apiKeyInput.text.toString().trim()

            if (key.isEmpty()) {
                resultText.text = "Please enter your Gemini API key."
            } else {
                preferences.edit()
                    .putString("GEMINI_API_KEY", key)
                    .apply()

                resultText.text = "Gemini API key saved on this device."
            }
        }

        analyzeButton.setOnClickListener {
            val key = apiKeyInput.text.toString().trim()
            val market = marketInput.text.toString().trim()

            if (key.isEmpty()) {
                resultText.text = "Please enter your Gemini API key."
                return@setOnClickListener
            }

            if (market.isEmpty()) {
                resultText.text = "Please enter market information first."
                return@setOnClickListener
            }

            analyzeButton.isEnabled = false
            resultText.text = "Gemini AI is analyzing XAUUSD..."

            executor.execute {
                val answer = analyzeMarket(key, market)

                runOnUiThread {
                    resultText.text = answer
                    analyzeButton.isEnabled = true
                }
            }
        }
    }

    private fun analyzeMarket(
        apiKey: String,
        market: String
    ): String {

        var connection: HttpURLConnection? = null

        return try {
            val endpoint =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

            connection = URL(endpoint).openConnection()
                as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            val prompt = """
                You are an educational XAUUSD market analysis assistant.

                Analyze only the market information supplied below.
                Do not invent live prices or claim to see the TradingView chart.

                Return your analysis in this format:

                SIGNAL: BUY / SELL / WAIT
                MARKET BIAS:
                ENTRY:
                STOP LOSS:
                TAKE PROFIT 1:
                TAKE PROFIT 2:
                MARKET STRUCTURE:
                SUPPORT AND RESISTANCE:
                REASONS:
                INVALIDATION:
                RISK WARNING:

                If information is insufficient, return WAIT.
                Do not guarantee profits or a win rate.
                Never encourage excessive leverage.

                User market information:
                $market
            """.trimIndent()

            val body = JSONObject().apply {
                put(
                    "contents",
                    org.json.JSONArray().put(
                        JSONObject().put(
                            "parts",
                            org.json.JSONArray().put(
                                JSONObject().put("text", prompt)
                            )
                        )
                    )
                )
            }

            OutputStreamWriter(connection.outputStream).use {
                it.write(body.toString())
            }

            val status = connection.responseCode

            val responseStream =
                if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val response = responseStream.bufferedReader().use {
                it.readText()
            }

            if (status !in 200..299) {
                return "Gemini API error ($status): $response"
            }

            val json = JSONObject(response)

            json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

        } catch (e: Exception) {
            "Analysis failed: ${e.message ?: "Unknown error"}"
        } finally {
            connection?.disconnect()
        }
    }

    override fun onDestroy() {
        chartWebView.destroy()
        executor.shutdown()
        super.onDestroy()
    }
}
