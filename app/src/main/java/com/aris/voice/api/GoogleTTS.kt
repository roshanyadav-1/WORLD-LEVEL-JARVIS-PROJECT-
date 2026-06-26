package com.aris.voice.api

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Base64
import android.util.Log
import com.aris.voice.BuildConfig
import com.aris.voice.MyApplication
import com.aris.voice.utilities.NetworkConnectivityManager
import com.aris.voice.utilities.NetworkNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Available voice options for Google TTS, using all provided Chirp3-HD voices.
 */
enum class TTSVoice(val displayName: String, val voiceName: String, val description: String) {
    CHIRP_ACHERNAR("Achernar", "en-US-Chirp3-HD-Achernar", "Articulate and friendly female voice"),
    CHIRP_ACHIRD("Achird", "en-US-Chirp3-HD-Achird", "Resonant, deep male narrator"),
    CHIRP_ALGENIB("Algenib", "en-US-Chirp3-HD-Algenib", "Clear and professional male voice"),
    CHIRP_ALGIEBA("Algieba", "en-US-Chirp3-HD-Algieba", "Rich, warm male voice"),
    CHIRP_ALNILAM("Alnilam", "en-US-Chirp3-HD-Alnilam", "Smooth, conversational male speaker"),
    CHIRP_AOEDE("Aoede", "en-US-Chirp3-HD-Aoede", "Crisp and rhythmic female voice"),
    CHIRP_AUTONOE("Autonoe", "en-US-Chirp3-HD-Autonoe", "Soft, gentle female voice"),
    CHIRP_CALLIRRHOE("Callirrhoe", "en-US-Chirp3-HD-Callirrhoe", "Bright, energetic female voice"),
    CHIRP_CHARON("Charon", "en-US-Chirp3-HD-Charon", "Deep, theatrical male voice"),
    CHIRP_DESPINA("Despina", "en-US-Chirp3-HD-Despina", "Pleasant, clear female voice"),
    CHIRP_ENCELADUS("Enceladus", "en-US-Chirp3-HD-Enceladus", "Authoritative, broad male voice"),
    CHIRP_ERINOME("Erinome", "en-US-Chirp3-HD-Erinome", "Expressive and melodic female voice"),
    CHIRP_FENRIR("Fenrir", "en-US-Chirp3-HD-Fenrir", "Husky, confident male voice"),
    CHIRP_GACRUX("Gacrux", "en-US-Chirp3-HD-Gacrux", "Serene, soothing female voice"),
    CHIRP_IAPETUS("Iapetus", "en-US-Chirp3-HD-Iapetus", "Polished, academic male voice"),
    CHIRP_KORE("Kore", "en-US-Chirp3-HD-Kore", "Playful, energetic female voice"),
    CHIRP_LAOMEDEIA("Laomedeia", "en-US-Chirp3-HD-Laomedeia", "Calm, elegant female voice"),
    CHIRP_LEDA("Leda", "en-US-Chirp3-HD-Leda", "Warm, supportive female narrator"),
    CHIRP_ORUS("Orus", "en-US-Chirp3-HD-Orus", "Cheerful, lively male voice"),
    CHIRP_PUCK("Puck", "en-US-Chirp3-HD-Puck", "Friendly, casual male voice"),
    CHIRP_PULCHERRIMA("Pulcherrima", "en-US-Chirp3-HD-Pulcherrima", "Soft-spoken, caring female tone"),
    CHIRP_RASALGETHI("Rasalgethi", "en-US-Chirp3-HD-Rasalgethi", "Bold, narrative male speaker"),
    CHIRP_SADACHBIA("Sadachbia", "en-US-Chirp3-HD-Sadachbia", "Gentle and patient male speaker"),
    CHIRP_SADALTAGER("Sadaltager", "en-US-Chirp3-HD-Sadaltager", "Aesthetic, rhythmic male vocal"),
    CHIRP_SCHEDAR("Schedar", "en-US-Chirp3-HD-Schedar", "Deep, majestic male presentation"),
    CHIRP_SULAFAT("Sulafat", "en-US-Chirp3-HD-Sulafat", "Confident, crisp female voice"),
    CHIRP_UMBRIEL("Umbriel", "en-US-Chirp3-HD-Umbriel", "Subtle, neutral male tone"),
    CHIRP_VINDEMIATRIX("Vindemiatrix", "en-US-Chirp3-HD-Vindemiatrix", "Clear, highly-intelligible female"),
    CHIRP_ZEPHYR("Zephyr", "en-US-Chirp3-HD-Zephyr", "Bright, articulate female vocal"),
    CHIRP_ZUBENELGENUBI("Zubenelgenubi", "en-US-Chirp3-HD-Zubenelgenubi", "Sophisticated, warm male voice")
}

enum class TTSLanguage { ENGLISH, HINDI, HINGLISH }

/**
 * Handles communication with the Google Cloud Text-to-Speech API.
 */
object GoogleTts {
    const val apiKey = BuildConfig.GOOGLE_TTS_API_KEY
    
    // Configured with custom timeouts to prevent hanging (BUG-4)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // No key is appended to the URL query string to prevent leak in logs (BUG-1 & IMP-8)
    private const val API_URL = "https://texttospeech.googleapis.com/v1beta1/text:synthesize"

    /**
     * Synthesizes speech from text using the Google Cloud TTS API with default voice.
     * @param text The text to synthesize.
     * @return A ByteArray containing the raw audio data (PCM).
     */
    suspend fun synthesize(text: String): ByteArray = synthesize(text, TTSVoice.CHIRP_LAOMEDEIA)

    /**
     * Synthesizes speech from text using the Google Cloud TTS API.
     * @param text The text to synthesize.
     * @param voice The voice to use for synthesis.
     * @return A ByteArray containing decoded PCM 16-bit 24kHz audio.
     */
    suspend fun synthesize(text: String, voice: TTSVoice): ByteArray = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) {
            throw Exception("Google TTS API key is not configured.")
        }

        // Real Network check using NetworkConnectivityManager (BUG-2 & IMP-1)
        val isOnline = try {
            NetworkConnectivityManager(MyApplication.appContext).isNetworkAvailable()
        } catch (e: Exception) {
            Log.e("GoogleTts", "Network check failed, assuming offline. ${e.message}")
            false
        }
        if (!isOnline) {
            try {
                NetworkNotifier.notifyOffline()
            } catch (e: Exception) {
                Log.e("GoogleTts", "NetworkNotifier error. ${e.message}")
            }
            throw Exception("No internet connection for TTS request.")
        }

        // Detect language for localized / Hinglish overrides (BUG-18 & IMP-13)
        val detectedLang = detectLanguage(text)
        val languageCode = if (detectedLang == TTSLanguage.HINDI) "hi-IN" else "en-US"
        val resolvedVoiceName = if (detectedLang == TTSLanguage.HINDI) {
            "hi-IN-Wavenet-B" // High quality Hindi fallback
        } else {
            voice.voiceName
        }

        // Apply SSML pre-processing (BUG-17 & IMP-3 & IMP-18)
        val ssmlText = textToSSML(text)

        // 1. Construct JSON payload requesting MP3 for compression efficiency (BUG-8 & IMP-9 & IMP-4)
        val jsonPayload = JSONObject().apply {
            put("input", JSONObject().put("ssml", ssmlText))
            put("voice", JSONObject().apply {
                put("languageCode", languageCode)
                put("name", resolvedVoiceName)
            })
            put("audioConfig", JSONObject().apply {
                put("audioEncoding", "MP3") // Smaller over the wire
                put("speakingRate", 1.0)
                put("pitch", 0.0)
                put("volumeGainDb", 0.0)
            })
        }.toString()

        // 2. Build the network request putting the API key securely in headers
        val request = Request.Builder()
            .url(API_URL)
            .header("X-Goog-Api-Key", apiKey)
            .header("Content-Type", "application/json; charset=utf-8")
            .header("X-Android-Package", BuildConfig.APPLICATION_ID)
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .build()

        // 3. Execute request and decode payload
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e("GoogleTts", "API Error: ${response.code} - $errorBody")
                throw Exception("Google TTS API request failed with code ${response.code}")
            }

            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                throw Exception("Received an empty response from Google TTS API.")
            }

            val jsonResponse = JSONObject(responseBody)
            val audioContent = jsonResponse.getString("audioContent")
            val mp3Bytes = Base64.decode(audioContent, Base64.DEFAULT)

            // 4. Decode MP3 to PCM 16-bit 24kHz using MediaCodec so AudioTrack can play it directly
            return@withContext decodeMp3ToPcm(mp3Bytes)
        }
    }

    /**
     * Converts raw MP3 bytes to raw 16-bit mono 24kHz PCM using standard Android MediaCodec.
     */
    private fun decodeMp3ToPcm(mp3Bytes: ByteArray): ByteArray {
        val cacheDir = MyApplication.appContext.cacheDir
        val tempFile = File.createTempFile("tts_decode_temp", ".mp3", cacheDir)
        tempFile.deleteOnExit()
        var trackIndex = -1
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            tempFile.writeBytes(mp3Bytes)
            extractor = MediaExtractor()
            extractor.setDataSource(tempFile.absolutePath)

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    break
                }
            }

            if (trackIndex < 0) {
                Log.e("GoogleTts", "No audio track found in synth file")
                return mp3Bytes
            }

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val outputBytes = java.io.ByteArrayOutputStream()
            var isEOS = false

            while (!isEOS) {
                val inputBufferIndex = codec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var outputBufferIndex = codec.dequeueOutputBuffer(info, 10000)
                while (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null) {
                        val chunk = ByteArray(info.size)
                        outputBuffer.get(chunk)
                        outputBytes.write(chunk)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)

                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEOS = true
                        break
                    }
                    outputBufferIndex = codec.dequeueOutputBuffer(info, 10000)
                }
                
                // Prevent infinite spinning if input is EOS but output isn't ready
                if (inputBufferIndex < 0 && outputBufferIndex < 0 && !isEOS) {
                    Thread.sleep(1)
                }
            }
            return outputBytes.toByteArray()
        } catch (e: Exception) {
            Log.e("GoogleTts", "Failed to decode MP3 to PCM: ${e.message}", e)
            return mp3Bytes // Fallback to raw mp3 (will fail AudioTrack, but acts as a fail-safe payload check)
        } finally {
            try { extractor?.release() } catch (ignored: Exception) {}
            try { codec?.stop(); codec?.release() } catch (ignored: Exception) {}
            try { tempFile.delete() } catch (ignored: Exception) {}
        }
    }

    /**
     * Prepares markdown-clean text with clean SSML formatting & natural pauses (BUG-17 & IMP-3 & IMP-18).
     */
    fun textToSSML(text: String): String {
        // Sanitize first to remove code snippets, headings, asterisks (BUG-5 & IMP-12)
        val sanitized = sanitizeForTTS(text)

        // Escape XML markers
        val escapedText = sanitized
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        // Handle abbreviations & tech words to pronounce character-by-character
        var processed = escapedText
            .replace(Regex("\\b(API|JSON|URL|UI|STT|TTS|VAD|SDK|APK|SHA1|GPS|Vico|M3)\\b")) {
                "<say-as interpret-as=\"characters\">${it.value}</say-as>"
            }
            // Date parsing say-as helper
            .replace(Regex("\\b(\\d{1,2})(st|nd|rd|th)\\s+(January|February|March|April|May|June|July|August|September|October|November|December)\\b")) {
                "<say-as interpret-as=\"date\">${it.value}</say-as>"
            }

        // Smart punctuation break injections
        processed = processed
            .replace(Regex("\\.\\s+"), ". <break time=\"300ms\"/> ")
            .replace(Regex(",\\s+"), ", <break time=\"100ms\"/> ")
            .replace(Regex("!\\s+"), "! <break time=\"400ms\"/> ")
            .replace(Regex("\\?\\s+"), "? <break time=\"400ms\"/> ")

        return "<speak>$processed</speak>"
    }

    /**
     * Removes markdown syntax (BUG-5 & IMP-12).
     */
    fun sanitizeForTTS(text: String): String {
        return text
            .replace(Regex("\\*+"), "")                                // Bold/italic markdown tags like * or **
            .replace(Regex("#{1,6}\\s+"), "")                  // Remove heading markers but keep text
            .replace(Regex("`([^`]*)`"), "$1")                             // Strip code block markers but keep text
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")        // Markdown links convert: [desc](url) -> desc
            .replace(Regex("_+"), " ")                                 // Convert underscores to spacing
            .replace(Regex("\\s+"), " ")                               // Flatten redundant spaces
            .trim()
    }

    /**
     * Determines whether Hindi characters are used to select a localized voice override (BUG-18 & IMP-13).
     */
    fun detectLanguage(text: String): TTSLanguage {
        if (text.isEmpty()) return TTSLanguage.ENGLISH
        
        val hindiCharsCount = text.count { it in '\u0900'..'\u097F' }
        val hindiRatio = hindiCharsCount.toFloat() / text.length

        return when {
            hindiRatio > 0.15 -> TTSLanguage.HINDI
            text.contains(Regex("\\b(haan|acha|thik|dhanyawad|namaste|shukriya|yaar|ji|bhai)\\b", RegexOption.IGNORE_CASE)) -> TTSLanguage.HINGLISH
            else -> TTSLanguage.ENGLISH
        }
    }

    /**
     * Get all available voice options.
     */
    fun getAvailableVoices(): List<TTSVoice> = TTSVoice.values().toList()
}
