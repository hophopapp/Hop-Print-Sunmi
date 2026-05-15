package com.hop.printapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import woyou.aidlservice.jiuiv5.ICallback
import woyou.aidlservice.jiuiv5.IWoyouService

class SunmiPrinterHelper(private val context: Context) {

    private var printerService: IWoyouService? = null
    private var onConnected: (() -> Unit)? = null
    private var onDisconnected: (() -> Unit)? = null

    val isConnected: Boolean
        get() = printerService != null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            printerService = IWoyouService.Stub.asInterface(service)
            onConnected?.invoke()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            printerService = null
            onDisconnected?.invoke()
        }
    }

    fun bind(onConnected: () -> Unit = {}, onDisconnected: () -> Unit = {}) {
        this.onConnected = onConnected
        this.onDisconnected = onDisconnected

        val intent = Intent().apply {
            setPackage("woyou.aidlservice.jiuiv5")
            action = "woyou.aidlservice.jiuiv5.IWoyouService"
        }
        context.applicationContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        try {
            context.applicationContext.unbindService(serviceConnection)
        } catch (_: Exception) {
        }
        printerService = null
    }

    fun printText(text: String, onResult: (Boolean, String) -> Unit) {
        val service = printerService
        if (service == null) {
            onResult(false, "Printer not connected")
            return
        }

        try {
            service.printerInit(null)
            service.setAlignment(0, null)
            service.setFontSize(24f, null)
            service.printText("$text\n", null)
            service.lineWrap(4, object : ICallback.Stub() {
                override fun onRunResult(isSuccess: Boolean) {
                    onResult(isSuccess, if (isSuccess) "Printed successfully" else "Print failed")
                }

                override fun onReturnString(result: String?) {}

                override fun onRaiseException(code: Int, msg: String?) {
                    onResult(false, msg ?: "Print error")
                }
            })
        } catch (e: RemoteException) {
            onResult(false, e.message ?: "Remote exception")
        }
    }
}
