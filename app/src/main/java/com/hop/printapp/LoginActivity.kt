package com.hop.printapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hop.printapp.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

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

        binding.passwordEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doLogin()
                true
            } else false
        }

        binding.loginButton.setOnClickListener { doLogin() }
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
                    binding.errorText.text = result.message
                    binding.errorText.visibility = View.VISIBLE
                }
            }
        }
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
