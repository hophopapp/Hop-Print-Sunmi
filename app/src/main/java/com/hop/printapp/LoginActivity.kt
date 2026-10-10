package com.hop.printapp

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hop.printapp.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_EXPIRED = "session_expired"
    }

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)

        // Already logged in — go straight to orders
        if (session.isLoggedIn) {
            openOrders()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent.getBooleanExtra(EXTRA_SESSION_EXPIRED, false)) {
            binding.errorText.text = "Session expired, please log in again"
            binding.errorText.visibility = View.VISIBLE
        }

        binding.passwordEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doLogin()
                true
            } else false
        }

        binding.loginButton.setOnClickListener { doLogin() }
        binding.forgotPasswordText.setOnClickListener { showForgotPasswordDialog() }
    }

    private fun doLogin() {
        val identifier = binding.identifierEditText.text?.toString()?.trim() ?: ""
        val password = binding.passwordEditText.text?.toString() ?: ""

        if (identifier.isEmpty()) {
            binding.errorText.text = getString(R.string.error_identifier_required)
            binding.errorText.visibility = View.VISIBLE
            return
        }
        if (password.isEmpty()) {
            binding.errorText.text = getString(R.string.error_password_required)
            binding.errorText.visibility = View.VISIBLE
            return
        }

        setLoading(true)
        binding.errorText.visibility = View.GONE

        lifecycleScope.launch {
            when (val result = HopApiClient.login(identifier, password)) {
                is HopApiClient.ApiResult.Success -> {
                    val data = result.data
                    session.accessToken = data.accessToken
                    session.refreshToken = data.refreshToken
                    session.userId = data.userId
                    session.cafeId = data.cafeId
                    openOrders()
                }
                is HopApiClient.ApiResult.Error -> {
                    setLoading(false)
                    binding.errorText.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                            this@LoginActivity, android.R.color.holo_red_dark
                        )
                    )
                    binding.errorText.text = result.message
                    binding.errorText.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showForgotPasswordDialog() {
        val dp = resources.displayMetrics.density
        val pad = (24 * dp).toInt()

        val emailInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            hint = "Email address"
            setSingleLine()
            setPadding(pad, (12 * dp).toInt(), pad, 0)
            // Pre-fill if the identifier field looks like an email
            val current = binding.identifierEditText.text?.toString()?.trim() ?: ""
            if (current.contains("@")) setText(current)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Reset Password")
            .setMessage("Enter your email and we'll send you a reset link.")
            .setView(emailInput)
            .setPositiveButton("Send Reset Link", null) // set below to prevent auto-dismiss on error
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = emailInput.text?.toString()?.trim() ?: ""
                if (email.isEmpty() || !email.contains("@")) {
                    emailInput.error = "Enter a valid email"
                    return@setOnClickListener
                }
                emailInput.error = null
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = "Sending…"

                lifecycleScope.launch {
                    when (val result = HopApiClient.forgotPassword(email)) {
                        is HopApiClient.ApiResult.Success -> {
                            dialog.dismiss()
                            binding.errorText.setTextColor(
                                androidx.core.content.ContextCompat.getColor(
                                    this@LoginActivity, R.color.primary
                                )
                            )
                            binding.errorText.text = "✓  Reset link sent — check your email"
                            binding.errorText.visibility = View.VISIBLE
                        }
                        is HopApiClient.ApiResult.Error -> {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = "Send Reset Link"
                            emailInput.error = result.message
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun setLoading(loading: Boolean) {
        binding.loginButton.isEnabled = !loading
        binding.identifierEditText.isEnabled = !loading
        binding.passwordEditText.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun openOrders() {
        startActivity(Intent(this, OrdersActivity::class.java))
        finish()
    }
}
