package com.hop.printapp

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hop.printapp.databinding.ActivityAddPointsBinding
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class AddPointsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddPointsBinding
    private lateinit var session: SessionManager

    private var scannedUserId: String? = null

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            scannedUserId = result.contents
            binding.userIdText.text = "Customer: …${result.contents.takeLast(6).uppercase()}"
            binding.userIdText.visibility = View.VISIBLE
            binding.successCard.visibility = View.GONE
            binding.errorText.visibility = View.GONE
            updateSubmitState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)

        binding = ActivityAddPointsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.title_add_points)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.scanButton.setOnClickListener {
            scanLauncher.launch(ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setBeepEnabled(true)
                setBarcodeImageEnabled(false)
                setOrientationLocked(false)
            })
        }

        binding.pointsEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (binding.submitButton.isEnabled) submitPoints()
                true
            } else false
        }

        binding.pointsEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                binding.successCard.visibility = View.GONE
                updateSubmitState()
            }
        })

        binding.submitButton.setOnClickListener { submitPoints() }

        binding.newCustomerButton.setOnClickListener { resetForNewCustomer() }

        loadCafeName()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    // ── Cafe name display ────────────────────────────────────────────────────

    private fun loadCafeName() {
        val cafeId = session.cafeId
        if (cafeId.isNullOrBlank()) {
            binding.cafeNameText.text = "No cafe assigned"
            return
        }
        lifecycleScope.launch {
            val token = session.accessToken ?: run { redirectToLogin(); return@launch }
            when (val result = HopApiClient.getCafes(token)) {
                is HopApiClient.ApiResult.Success -> {
                    val cafe = result.data.find { it.cafeId == cafeId }
                    binding.cafeNameText.text = cafe?.name ?: cafeId
                }
                is HopApiClient.ApiResult.Error -> {
                    // Non-fatal — still usable, just show the raw cafeId
                    binding.cafeNameText.text = cafeId
                    if (result.isUnauthorized) handleUnauthorized()
                }
            }
        }
    }

    // ── Submit ───────────────────────────────────────────────────────────────

    private fun submitPoints() {
        val userId = scannedUserId
        val points = binding.pointsEditText.text?.toString()?.trim()?.toIntOrNull() ?: 0

        if (userId.isNullOrBlank()) {
            showError(getString(R.string.error_scan_required))
            return
        }
        if (points <= 0) {
            showError(getString(R.string.error_points_invalid))
            return
        }

        binding.errorText.visibility = View.GONE
        setLoading(true)

        lifecycleScope.launch {
            val token = session.accessToken ?: run { redirectToLogin(); return@launch }
            when (val result = HopApiClient.addPoints(token, userId, points)) {
                is HopApiClient.ApiResult.Success -> {
                    setLoading(false)
                    binding.successDetail.text = "$points point${if (points != 1) "s" else ""} successfully added for customer …${userId.takeLast(6).uppercase()}"
                    binding.successCard.visibility = View.VISIBLE
                    // Only clear the points field; keep customer ID visible for reference
                    binding.pointsEditText.setText("")
                    updateSubmitState()
                }
                is HopApiClient.ApiResult.Error -> {
                    setLoading(false)
                    if (result.isUnauthorized) handleUnauthorized()
                    else showError(result.message)
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun resetForNewCustomer() {
        scannedUserId = null
        binding.userIdText.visibility = View.GONE
        binding.pointsEditText.setText("")
        binding.successCard.visibility = View.GONE
        binding.errorText.visibility = View.GONE
        updateSubmitState()
    }

    private fun updateSubmitState() {
        val points = binding.pointsEditText.text?.toString()?.trim()?.toIntOrNull() ?: 0
        binding.submitButton.isEnabled = !scannedUserId.isNullOrBlank() && points > 0
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        val points = binding.pointsEditText.text?.toString()?.trim()?.toIntOrNull() ?: 0
        binding.submitButton.isEnabled = !loading && !scannedUserId.isNullOrBlank() && points > 0
        binding.scanButton.isEnabled = !loading
        binding.pointsEditText.isEnabled = !loading
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
    }

    private fun handleUnauthorized() {
        lifecycleScope.launch {
            val rt = session.refreshToken
            if (!rt.isNullOrEmpty()) {
                when (val r = HopApiClient.refreshToken(rt)) {
                    is HopApiClient.ApiResult.Success -> {
                        session.accessToken = r.data.accessToken
                        if (r.data.refreshToken.isNotEmpty()) session.refreshToken = r.data.refreshToken
                        loadCafeName()
                        return@launch
                    }
                    else -> {}
                }
            }
            redirectToLogin()
        }
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
