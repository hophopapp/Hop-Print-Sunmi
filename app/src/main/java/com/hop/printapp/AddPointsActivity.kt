package com.hop.printapp

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hop.printapp.databinding.ActivityAddPointsBinding
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class AddPointsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddPointsBinding
    private lateinit var session: SessionManager

    private var cafes: List<HopApiClient.CafeItem> = emptyList()
    private var scannedUserId: String? = null
    private var selectedCafeId: String? = null

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            scannedUserId = result.contents
            binding.userIdText.text = "Customer: …${result.contents.takeLast(6).uppercase()}"
            binding.userIdText.visibility = View.VISIBLE
            binding.successCard.visibility = View.GONE
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

        binding.cafeAutoComplete.setOnItemClickListener { _, _, position, _ ->
            selectedCafeId = cafes.getOrNull(position)?.cafeId
            binding.successCard.visibility = View.GONE
            updateSubmitState()
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

        loadCafes()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    // ── Cafe loading ─────────────────────────────────────────────────────────

    private fun loadCafes() {
        setLoading(true)
        lifecycleScope.launch {
            val token = session.accessToken ?: run { redirectToLogin(); return@launch }
            when (val result = HopApiClient.getCafes(token)) {
                is HopApiClient.ApiResult.Success -> {
                    cafes = result.data
                    val names = cafes.map { it.name }
                    val adapter = ArrayAdapter(
                        this@AddPointsActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        names
                    )
                    binding.cafeAutoComplete.setAdapter(adapter)
                    setLoading(false)
                }
                is HopApiClient.ApiResult.Error -> {
                    setLoading(false)
                    if (result.isUnauthorized) handleUnauthorized()
                    else showError(result.message)
                }
            }
        }
    }

    // ── Submit ───────────────────────────────────────────────────────────────

    private fun submitPoints() {
        val userId = scannedUserId
        val cafeId = selectedCafeId
        val points = binding.pointsEditText.text?.toString()?.trim()?.toIntOrNull() ?: 0

        if (userId.isNullOrBlank()) {
            showError(getString(R.string.error_scan_required))
            return
        }
        if (cafeId.isNullOrBlank()) {
            showError(getString(R.string.error_cafe_required))
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
            when (val result = HopApiClient.addPoints(token, userId, cafeId, points)) {
                is HopApiClient.ApiResult.Success -> {
                    setLoading(false)
                    val data = result.data
                    binding.successDetail.text =
                        "Balance: ${data.userPoints} pts at ${data.cafeName}"
                    binding.successCard.visibility = View.VISIBLE

                    // Clear userId + points for the next customer; keep cafe selected
                    scannedUserId = null
                    binding.userIdText.visibility = View.GONE
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

    private fun updateSubmitState() {
        val points = binding.pointsEditText.text?.toString()?.trim()?.toIntOrNull() ?: 0
        binding.submitButton.isEnabled =
            !scannedUserId.isNullOrBlank() && !selectedCafeId.isNullOrBlank() && points > 0
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.submitButton.isEnabled = !loading && run {
            val points = binding.pointsEditText.text?.toString()?.trim()?.toIntOrNull() ?: 0
            !scannedUserId.isNullOrBlank() && !selectedCafeId.isNullOrBlank() && points > 0
        }
        binding.scanButton.isEnabled = !loading
        binding.cafeAutoComplete.isEnabled = !loading
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
                        loadCafes()
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
