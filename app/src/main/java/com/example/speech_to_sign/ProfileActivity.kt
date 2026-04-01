package com.example.speech_to_sign

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()

        populateUserInfo()
        setupClickListeners()
    }

    private fun populateUserInfo() {
        val user = auth.currentUser ?: return

        val email = user.email ?: "No email"
        val displayName = user.displayName

        // Avatar initials — use display name first letter, else email first letter
        val initial = when {
            !displayName.isNullOrBlank() -> displayName[0].uppercaseChar().toString()
            email.isNotBlank() -> email[0].uppercaseChar().toString()
            else -> "?"
        }

        // Name shown under avatar — use display name or email prefix
        val nameShown = when {
            !displayName.isNullOrBlank() -> displayName
            else -> email.substringBefore("@")
        }

        // Provider label
        val provider = when {
            user.providerData.any { it.providerId == "google.com" }  -> "Google"
            user.providerData.any { it.providerId == "github.com" }  -> "GitHub"
            user.providerData.any { it.providerId == "twitter.com" } -> "Twitter"
            user.providerData.any { it.providerId == "password" }    -> "Email & Password"
            user.providerData.any { it.providerId == "emailLink" }   -> "Email Link"
            else -> "Unknown"
        }

        findViewById<TextView>(R.id.tvAvatarInitials).text  = initial
        findViewById<TextView>(R.id.tvProfileName).text     = nameShown
        findViewById<TextView>(R.id.tvProfileEmail).text    = email
        findViewById<TextView>(R.id.tvEmailRow).text        = email
        findViewById<TextView>(R.id.tvProviderRow).text     = provider
    }

    private fun setupClickListeners() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<LinearLayout>(R.id.rowTerms).setOnClickListener {
            showTermsDialog()
        }

        findViewById<LinearLayout>(R.id.rowLogout).setOnClickListener {
            confirmLogout()
        }
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Log Out")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Log Out") { _, _ ->
                auth.signOut()
                startActivity(
                    Intent(this, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTermsDialog() {
        // Reuse the same dialog from LoginActivity
        val dialogView = layoutInflater.inflate(R.layout.dialog_terms, null)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(
            R.id.btnAcceptTerms
        ).apply {
            text = "Close"
            setOnClickListener { dialog.dismiss() }
        }

        // Hide decline button — user is already registered
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(
            R.id.btnDeclineTerms
        ).visibility = android.view.View.GONE

        dialog.show()
    }
}