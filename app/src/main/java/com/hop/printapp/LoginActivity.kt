package com.hop.printapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hop.printapp.databinding.ActivityLoginBinding
import com.hop.printapp.model.LoginRequest
import com.hop.printapp.network.RetrofitClient
import com.hop.printapp.storage.SessionManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.init(this)

        if (SessionManager.isLoggedIn) {
            startActivity(Intent(this, OrdersActivity::class.java))
            finish()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener { attemptLogin() }
    }

    private fun attemptLogin() {
        val email = binding.emailEditText.text?.toString()?.trim() ?: ""
        val password = binding.passwordEditText.text?.toString() ?: ""

        if (email.isEmpty()) {
            binding.emailInputLayout.error = "Email is required"
            return
        }
        if (password.isEmpty()) {
            binding.passwordInputLayout.error = "Password is required"
            return
        }

        binding.emailInputLayout.error = null
        binding.passwordInputLayout.error = null
        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.login(LoginRequest(email, password))
                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    val cafeId = body.user.cafeId ?: body.user.assignedCafe
                    if (cafeId.isNullOrEmpty()) {
                        setLoading(false)
                        Toast.makeText(this@LoginActivity, "This account is not assigned to a cafe", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    SessionManager.saveLoginData(body.accessToken, body.refreshToken, body.user)
                    startActivity(Intent(this@LoginActivity, OrdersActivity::class.java))
                    finish()
                } else {
                    setLoading(false)
                    val errorMsg = response.errorBody()?.string() ?: "Login failed"
                    Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(this@LoginActivity, "Connection error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.loginButton.isEnabled = !loading
        binding.emailEditText.isEnabled = !loading
        binding.passwordEditText.isEnabled = !loading
    }
}
