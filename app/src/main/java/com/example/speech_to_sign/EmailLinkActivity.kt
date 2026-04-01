package com.example.speech_to_sign

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth

class EmailLinkActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: TextInputEditText
    private lateinit var btnSendLink: MaterialButton
    private lateinit var btnResend: MaterialButton
    private lateinit var stateSend: LinearLayout
    private lateinit var stateCheckEmail: LinearLayout
    private lateinit var tvSentEmail: TextView

    // Must match the URL you whitelisted in Firebase Console →
    // Authentication → Sign-in method → Email link → Authorized domains
    private val actionCodeSettings = ActionCodeSettings.newBuilder()
        .setUrl("https://speechtosign-4e9cf.firebaseapp.com") // ← change to your domain
        .setHandleCodeInApp(true)
        .setAndroidPackageName("com.example.speech_to_sign", true, null)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_link)

        auth = FirebaseAuth.getInstance()

        etEmail        = findViewById(R.id.etEmailLink)
        btnSendLink    = findViewById(R.id.btnSendLink)
        btnResend      = findViewById(R.id.btnResend)
        stateSend      = findViewById(R.id.stateSend)
        stateCheckEmail = findViewById(R.id.stateCheckEmail)
        tvSentEmail    = findViewById(R.id.tvSentEmail)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        btnSendLink.setOnClickListener { sendLink() }
        btnResend.setOnClickListener {
            // Go back to send state
            stateCheckEmail.visibility = View.GONE
            stateSend.visibility = View.VISIBLE
        }

        // Handle incoming email link if app was opened via the link
        handleIncomingLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingLink(intent)
    }

    private fun sendLink() {
        val email = etEmail.text.toString().trim()
        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
            return
        }

        btnSendLink.isEnabled = false
        btnSendLink.text = "Sending…"

        auth.sendSignInLinkToEmail(email, actionCodeSettings)
            .addOnCompleteListener { task ->
                btnSendLink.isEnabled = true
                btnSendLink.text = "Send Magic Link"

                if (task.isSuccessful) {
                    // Save email locally so we can complete sign-in when link is tapped
                    getSharedPreferences("auth", MODE_PRIVATE)
                        .edit()
                        .putString("emailForSignIn", email)
                        .apply()

                    // Switch to "check your email" state
                    stateSend.visibility = View.GONE
                    stateCheckEmail.visibility = View.VISIBLE
                    tvSentEmail.text = email
                } else {
                    Toast.makeText(
                        this,
                        "Failed to send link: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun handleIncomingLink(intent: Intent?) {
        val emailLink = intent?.data?.toString() ?: return

        if (!auth.isSignInWithEmailLink(emailLink)) return

        // Retrieve saved email
        val email = getSharedPreferences("auth", MODE_PRIVATE)
            .getString("emailForSignIn", null)

        if (email == null) {
            // Edge case: user opened link on a different device
            Toast.makeText(this, "Please enter your email to complete sign in", Toast.LENGTH_LONG).show()
            return
        }

        auth.signInWithEmailLink(email, emailLink)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Clear saved email
                    getSharedPreferences("auth", MODE_PRIVATE)
                        .edit().remove("emailForSignIn").apply()

                    val isNew = task.result?.additionalUserInfo?.isNewUser ?: false
                    if (isNew) {
                        // Show T&C — reuse LoginActivity's dialog by passing a flag
                        // Simplest approach: go to LoginActivity which handles T&C
                        startActivity(
                            Intent(this, LoginActivity::class.java)
                                .putExtra("showTerms", true)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                    } else {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        )
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Sign in failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}
