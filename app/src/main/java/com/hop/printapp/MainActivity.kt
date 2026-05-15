package com.hop.printapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.hop.printapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var printer: SunmiPrinterHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        printer = SunmiPrinterHelper(this)

        binding.printButton.setOnClickListener {
            val text = binding.inputEditText.text?.toString()?.trim()
            if (text.isNullOrEmpty()) {
                Toast.makeText(this, R.string.error_empty_input, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            printReceipt(text)
        }
    }

    override fun onStart() {
        super.onStart()
        printer.bind(
            onConnected = { runOnUiThread { updateStatus(true) } },
            onDisconnected = { runOnUiThread { updateStatus(false) } }
        )
    }

    override fun onStop() {
        super.onStop()
        printer.unbind()
    }

    private fun printReceipt(text: String) {
        binding.printButton.isEnabled = false
        binding.statusText.text = getString(R.string.status_printing)

        printer.printText(text) { success, message ->
            runOnUiThread {
                binding.printButton.isEnabled = true
                binding.statusText.text = message
                binding.statusText.setTextColor(
                    ContextCompat.getColor(
                        this,
                        if (success) R.color.primary else android.R.color.holo_red_dark
                    )
                )
                if (success) {
                    binding.inputEditText.text?.clear()
                }
            }
        }
    }

    private fun updateStatus(connected: Boolean) {
        binding.statusText.text = getString(
            if (connected) R.string.status_printer_connected
            else R.string.status_printer_disconnected
        )
        binding.statusText.setTextColor(
            ContextCompat.getColor(
                this,
                if (connected) R.color.primary else android.R.color.holo_red_dark
            )
        )
        binding.printButton.isEnabled = connected
    }
}
