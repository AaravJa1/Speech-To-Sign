package com.example.speech_to_sign

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.*
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var signPlayer: SignPlayer
    private lateinit var tvResult: TextView
    private lateinit var tvMissing: TextView
    private lateinit var tvStatusChip: TextView
    private lateinit var tvNowPlaying: TextView
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechIntent: Intent

    private lateinit var tabSpeechToSign: TextView
    private lateinit var tabSignToSpeech: TextView
    private lateinit var speechToSignView: View
    private lateinit var signToSpeechView: View
    private lateinit var avatarCard: CardView
    private lateinit var resultCard: CardView
    private lateinit var micRow: View
    private lateinit var btnMic: FloatingActionButton

    private var currentTab  = 0
    private var isListening = false   // tracks mic state

    // Beep tones — created once, released in onDestroy
    private val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        bindViews()
        setupSignPlayer()
        setupSpeechRecognizer()
        setupPillSwitcher()

        btnMic.setOnClickListener {
            pressBounce()
            if (isListening) {
                // Tap again to stop
                stopListening()
            } else {
                startListening()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        toneGen.release()
        speechRecognizer.destroy()
    }

    // ── Bind ──────────────────────────────────────────────────────────────────

    private fun bindViews() {
        tvResult         = findViewById(R.id.tvResult)
        tvMissing        = findViewById(R.id.tvMissing)
        tvStatusChip     = findViewById(R.id.tvStatusChip)
        tvNowPlaying     = findViewById(R.id.tvNowPlaying)
        tabSpeechToSign  = findViewById(R.id.tabSpeechToSign)
        tabSignToSpeech  = findViewById(R.id.tabSignToSpeech)
        speechToSignView = findViewById(R.id.speechToSignView)
        signToSpeechView = findViewById(R.id.signToSpeechView)
        avatarCard       = findViewById(R.id.avatarCard)
        resultCard       = findViewById(R.id.resultCard)
        micRow           = findViewById(R.id.micRow)
        btnMic           = findViewById(R.id.btnMic)
    }

    // ── Sign Player ───────────────────────────────────────────────────────────

    private fun setupSignPlayer() {
        signPlayer = SignPlayer(
            this,
            findViewById<VideoView>(R.id.avatarVideoView),
            findViewById<ImageView>(R.id.ivPlaceholder),
            tvResult
        )
    }

    // ── Pill switcher ─────────────────────────────────────────────────────────

    private fun setupPillSwitcher() {
        tabSpeechToSign.setOnClickListener { switchToTab(0) }
        tabSignToSpeech.setOnClickListener { switchToTab(1) }
    }

    private fun switchToTab(index: Int) {
        if (index == currentTab) return
        currentTab = index
        val toSpeechSign = index == 0

        fun styleTab(tab: TextView, active: Boolean) {
            tab.setBackgroundResource(
                if (active) R.drawable.bg_pill_active else android.R.color.transparent)
            tab.setTextColor(getColor(
                if (active) android.R.color.black else R.color.tab_inactive))
            tab.setTypeface(null,
                if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        styleTab(tabSpeechToSign, toSpeechSign)
        styleTab(tabSignToSpeech, !toSpeechSign)

        micRow.visibility = if (toSpeechSign) View.VISIBLE else View.GONE

        val out = if (toSpeechSign) signToSpeechView else speechToSignView
        val ins = if (toSpeechSign) speechToSignView else signToSpeechView
        out.visibility = View.GONE
        ins.visibility = View.VISIBLE

        setStatus("Idle")
    }

    // ── Listening control ─────────────────────────────────────────────────────

    private fun startListening() {
        isListening = true
        setMicActive(true)
        // Short rising beep = "I'm listening"
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        speechRecognizer.startListening(speechIntent)
    }

    private fun stopListening() {
        isListening = false
        setMicActive(false)
        // Short falling beep = "stopped"
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 120)
        speechRecognizer.stopListening()
        setStatus("Idle")
    }

    // Change mic button colour to signal active/idle state
    private fun setMicActive(active: Boolean) {
        btnMic.backgroundTintList = getColorStateList(
            if (active) R.color.mic_active else R.color.mic_blue
        )
    }

    // ── Speech recognizer ─────────────────────────────────────────────────────

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {
                setStatus("Listening…")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {
                setStatus("Processing…")
            }
            override fun onError(error: Int) {
                isListening = false
                setMicActive(false)
                setStatus("Idle")
                // Play a low error beep
                toneGen.startTone(ToneGenerator.TONE_SUP_ERROR, 200)

                // Set a status message but DO NOT pass it to signPlayer
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that — try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed."
                    else -> "Tap the mic and speak…"
                }
                // Write directly to tvResult, bypassing signPlayer entirely
                tvResult.text = msg
                tvNowPlaying.visibility = View.GONE
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                setMicActive(false)
                // Success beep — two short tones
                toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 100)

                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()

                if (!text.isNullOrBlank()) {
                    // Only pass real recognised speech to signPlayer
                    tvResult.text = text
                    setStatus("Playing")
                    tvNowPlaying.visibility = View.VISIBLE
                    signPlayer.playSentence(text)
                } else {
                    setStatus("Idle")
                    tvResult.text = "Tap the mic and speak…"
                }
            }
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
    }

    // ── Mic bounce ────────────────────────────────────────────────────────────

    private fun pressBounce() {
        btnMic.animate()
            .scaleX(0.82f).scaleY(0.82f).setDuration(80)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                btnMic.animate()
                    .scaleX(1f).scaleY(1f).setDuration(400)
                    .setInterpolator(OvershootInterpolator(3f)).start()
            }.start()
    }

    private fun setStatus(s: String) { tvStatusChip.text = s }
}
