package com.example.speech_to_sign

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.widget.ImageView // Import this
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var signPlayer: SignPlayer
    private lateinit var tvResult: TextView
    private lateinit var tvMissing: TextView


    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val spokenText = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)

            if (!spokenText.isNullOrEmpty()) {
                val sentence = spokenText[0] // Get the first result

                // Update UI text
                tvResult.text = "You said: $sentence"

                // Play the video and get list of missing words
                val missingList = signPlayer.playSentence(sentence)


            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        val videoView = findViewById<VideoView>(R.id.avatarVideoView)
        val imageView = findViewById<ImageView>(R.id.ivPlaceholder) // <--- NEW LINE
        tvResult = findViewById(R.id.tvResult)
        tvMissing = findViewById(R.id.tvMissing)


        signPlayer = SignPlayer(this, videoView, imageView , tvResult)


        val btnSpeak = findViewById<View>(R.id.btnSpeak)
        btnSpeak.setOnClickListener {
            speak()
        }
    }

    private fun speak() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // Force English (India) model
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something...")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice Recognition Not Working", Toast.LENGTH_SHORT).show()
        }
    }
}