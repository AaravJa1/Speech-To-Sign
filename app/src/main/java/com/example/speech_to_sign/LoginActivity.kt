package com.example.speech_to_sign

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider

class LoginActivity : AppCompatActivity() {

    // ── Firebase ──────────────────────────────────────────────────────────────
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnRegister: MaterialButton
    private lateinit var btnGoogle: MaterialButton
    private lateinit var btnGitHub: MaterialButton
    private lateinit var btnTwitter: MaterialButton

    private lateinit var btnEmailLink: MaterialButton

    // ── Google Sign-In launcher ───────────────────────────────────────────────
    private val googleSignInLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                showToast("Google sign-in failed: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        setupFirebase()
        bindViews()
        runEntranceAnimation()

        // Skip login if already authenticated
        if (auth.currentUser != null) {
            goToMain()
            return
        }

        setupClickListeners()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun showTermsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_terms, null)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(
            R.id.btnAcceptTerms
        ).setOnClickListener {
            dialog.dismiss()
            goToMain()
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(
            R.id.btnDeclineTerms
        ).setOnClickListener {
            dialog.dismiss()
            // Delete the just-created account since they declined
            auth.currentUser?.delete()
            showToast("You must accept the terms to use HandSpeak.")
        }

        dialog.show()
    }


    private fun setupFirebase() {
        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun bindViews() {
        etEmail    = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin    = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)
        btnGoogle   = findViewById(R.id.btnGoogleSignIn)
        btnGitHub   = findViewById(R.id.btnGitHubSignIn)
        btnTwitter  = findViewById(R.id.btnTwitterSignIn)
        btnEmailLink = findViewById(R.id.btnEmailLink)
    }

    private fun setupClickListeners() {
        btnLogin.setOnClickListener    { signInWithEmail() }
        btnRegister.setOnClickListener { registerWithEmail() }
        btnGoogle.setOnClickListener   { signInWithGoogle() }
        btnGitHub.setOnClickListener   { signInWithGitHub() }
        btnTwitter.setOnClickListener  { signInWithTwitter() }
        btnEmailLink.setOnClickListener {
            startActivity(Intent(this, EmailLinkActivity::class.java))
        }

    }

    // ── Animation ─────────────────────────────────────────────────────────────

    private fun runEntranceAnimation() {
        val container = findViewById<LinearLayout>(R.id.loginContainer)


        for (i in 0 until container.childCount) {
            container.getChildAt(i).apply {
                alpha = 0f
                translationY = 60f
                scaleX = 0.95f
                scaleY = 0.95f
            }
        }


        for (i in 0 until container.childCount) {
            container.getChildAt(i)
                .animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(450)
                .setStartDelay((i * 70).toLong())
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        }


        val logo = container.getChildAt(0)
        logo.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setInterpolator(OvershootInterpolator(2f))
            .setDuration(600)
            .setStartDelay(100)
            .start()
    }



    private fun signInWithEmail() {
        val email    = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        if (!validateFields(email, password)) return

        setLoadingState(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                setLoadingState(false)
                if (task.isSuccessful) goToMain()
                else showToast("Login failed: ${task.exception?.message}")
            }
    }

    private fun registerWithEmail() {
        val email    = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        if (!validateFields(email, password)) return

        setLoadingState(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                setLoadingState(false)
                if (task.isSuccessful) showTermsDialog()
                else showToast("Registration failed: ${task.exception?.message}")
            }
    }

    private fun validateFields(email: String, password: String): Boolean {
        if (email.isEmpty()) { showToast("Please enter your email"); return false }
        if (password.isEmpty()) { showToast("Please enter your password"); return false }
        if (password.length < 6) { showToast("Password must be at least 6 characters"); return false }
        return true
    }



    private fun signInWithGoogle() {
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        setLoadingState(true)
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                setLoadingState(false)
                if (task.isSuccessful) {
                    val isNew = task.result?.additionalUserInfo?.isNewUser ?: false
                    if (isNew) showTermsDialog() else goToMain()
                } else showToast("Google authentication failed.")
            }
    }

    private fun signInWithGitHub() {
        val provider = OAuthProvider.newBuilder("github.com").apply {
            addCustomParameter("allow_signup", "true")
        }.build()

        auth.startActivityForSignInWithProvider(this, provider)
            .addOnSuccessListener { result ->
                val isNew = result.additionalUserInfo?.isNewUser ?: false
                if (isNew) showTermsDialog() else goToMain()
            }
            .addOnFailureListener { e ->
                showToast("GitHub sign-in failed: ${e.message}")
            }
    }


    private fun signInWithTwitter() {
        val provider = OAuthProvider.newBuilder("twitter.com").build()

        auth.startActivityForSignInWithProvider(this, provider)
            .addOnSuccessListener { result ->
                val isNew = result.additionalUserInfo?.isNewUser ?: false
                if (isNew) showTermsDialog() else goToMain()
            }
            .addOnFailureListener { e ->
                showToast("Twitter sign-in failed: ${e.message}")
            }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()


    private fun setLoadingState(loading: Boolean) {
        listOf(btnLogin, btnRegister, btnGoogle, btnGitHub, btnTwitter)
            .forEach { it.isEnabled = !loading }
        btnLogin.text = if (loading) "Please wait…" else "Login"
    }
}