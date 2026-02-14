package com.example.speech_to_sign

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

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
                val sentence = spokenText[0]

                //test
                tvResult.text = "You said: $sentence"

                //play video
                val missingList = signPlayer.playSentence(sentence)


                if (missingList.isNotEmpty()) {
                    tvMissing.text = "Missing videos for: $missingList"
                } else {
                    tvMissing.text = ""
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val videoView = findViewById<VideoView>(R.id.avatarVideoView)
        tvResult = findViewById(R.id.tvResult)  // Ensure this matches the XML ID
        tvMissing = findViewById(R.id.tvMissing) // Ensure this matches the XML ID


        signPlayer = SignPlayer(this, videoView)


        val btnSpeak = findViewById<View>(R.id.btnSpeak)
        btnSpeak.setOnClickListener {
            speak()
        }
    }

    private fun speak() {

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {

            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something...")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: Exception) {

            Toast.makeText(this, "Voice Recognition Not Working", Toast.LENGTH_SHORT).show()
        }
    }
}
