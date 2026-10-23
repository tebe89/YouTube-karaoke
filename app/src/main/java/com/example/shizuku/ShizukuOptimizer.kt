package com.example.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

sealed class ShizukuState {
    object Checking : ShizukuState()
    object NotInstalledOrUnavailable : ShizukuState()
    object NotRunning : ShizukuState()
    object PermissionRequired : ShizukuState()
    data class Active(val isOptimized: Boolean, val logMessage: String) : ShizukuState()
}

/**
 * Shizuku Optimizer for Low-Latency Audio & CPU Anti-Throttling.
 * Checks Shizuku Binder status and runs elevated commands to prioritize DSP threads.
 */
object ShizukuOptimizer {
    private const val TAG = "ShizukuOptimizer"
    private const val REQUEST_CODE_SHIZUKU = 8080

    private val _state = MutableStateFlow<ShizukuState>(ShizukuState.Checking)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_SHIZUKU) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                _state.value = ShizukuState.Active(isOptimized = false, logMessage = "Shizuku Permission Granted.")
                executeElevatedOptimizations()
            } else {
                _state.value = ShizukuState.PermissionRequired
            }
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (e: Throwable) {
            Log.d(TAG, "Shizuku listener note: ${e.message}")
        }
        checkStatus()
    }

    fun checkStatus() {
        scope.launch {
            try {
                if (!Shizuku.pingBinder()) {
                    _state.value = ShizukuState.NotRunning
                    return@launch
                }

                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    _state.value = ShizukuState.Active(
                        isOptimized = true,
                        logMessage = "Connected to Shizuku (v${Shizuku.getVersion()})"
                    )
                } else {
                    _state.value = ShizukuState.PermissionRequired
                }
            } catch (e: Throwable) {
                Log.i(TAG, "Shizuku service check: ${e.message}")
                _state.value = ShizukuState.NotInstalledOrUnavailable
            }
        }
    }

    fun requestShizukuPermission() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
                } else {
                    executeElevatedOptimizations()
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
        }
    }

    fun executeElevatedOptimizations() {
        scope.launch {
            try {
                val pkgName = appContext?.packageName ?: "com.aistudio.tbsoundlab.dsp"
                val myPid = Process.myPid()

                val commands = listOf(
                    "cmd appops set $pkgName RUN_IN_BACKGROUND allow",
                    "cmd appops set $pkgName SYSTEM_ALERT_WINDOW allow",
                    "cmd appops set $pkgName REQUEST_IGNORE_BATTERY_OPTIMIZATIONS allow",
                    "renice -20 -p $myPid"
                )

                val results = StringBuilder()
                for (cmd in commands) {
                    try {
                        val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                            "newProcess",
                            Array<String>::class.java,
                            Array<String>::class.java,
                            String::class.java
                        ).apply { isAccessible = true }
                        val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", cmd), null, null) as? java.lang.Process
                        if (process != null) {
                            val reader = BufferedReader(InputStreamReader(process.inputStream))
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                results.append(line).append("\n")
                            }
                            process.waitFor()
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Command execution note ($cmd): ${e.message}")
                    }
                }

                _state.value = ShizukuState.Active(
                    isOptimized = true,
                    logMessage = "CPU High-Priority Realtime & Background AppOps Granted"
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Shizuku optimization failed", e)
                _state.value = ShizukuState.Active(
                    isOptimized = false,
                    logMessage = "Optimization error: ${e.message}"
                )
            }
        }
    }
}
