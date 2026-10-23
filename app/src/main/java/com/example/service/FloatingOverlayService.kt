package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.audio.AudioEngineManager
import com.example.audio.ChannelRoutingMode

/**
 * Floating Overlay Bubble Service with Draggable Gestures & Glassmorphic Quick Control Deck
 */
class FloatingOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        const val ACTION_SHOW = "com.example.service.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE = "com.example.service.ACTION_HIDE_OVERLAY"

        fun start(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, FloatingOverlayService::class.java).apply {
                    action = ACTION_SHOW
                }
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlayView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun createOverlayView() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 220
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)

            setContent {
                FloatingBubbleContent(
                    onDrag = { dx, dy ->
                        this@FloatingOverlayService.layoutParams.x += dx.toInt()
                        this@FloatingOverlayService.layoutParams.y += dy.toInt()
                        windowManager.updateViewLayout(this@apply, this@FloatingOverlayService.layoutParams)
                    },
                    onCloseService = {
                        stopSelf()
                    }
                )
            }
        }

        try {
            windowManager.addView(composeView, layoutParams)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        composeView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun FloatingBubbleContent(
    onDrag: (Float, Float) -> Unit,
    onCloseService: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val engineState by AudioEngineManager.engineState.collectAsState()

    val cyanAccent = Color(0xFF00F0FF)
    val magentaAccent = Color(0xFFFF007A)
    val glassBg = Color(0xCC0E1524)
    val glassBorder = Brush.linearGradient(
        listOf(cyanAccent.copy(alpha = 0.6f), magentaAccent.copy(alpha = 0.4f), Color.White.copy(alpha = 0.2f))
    )

    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.padding(8.dp)
    ) {
        if (!isExpanded) {
            // Compact Floating Bubble
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(glassBg)
                    .border(1.5.dp, glassBorder, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
                    .clickable { isExpanded = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "TB Sound Lab Bubble",
                    tint = if (engineState.isMasterEnabled) cyanAccent else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            // Expanded Glassmorphic Quick Control Deck
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassBg,
                modifier = Modifier
                    .width(280.dp)
                    .border(1.dp, glassBorder, RoundedCornerShape(20.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = cyanAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "TB Sound Lab",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row {
                            // Master Toggle
                            IconButton(
                                onClick = { AudioEngineManager.setMasterEnabled(!engineState.isMasterEnabled) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.PowerSettingsNew,
                                    contentDescription = "Power",
                                    tint = if (engineState.isMasterEnabled) cyanAccent else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            // Minimize
                            IconButton(
                                onClick = { isExpanded = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Minimize",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Pitch Shifting Deck
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Pitchbend",
                            color = magentaAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            String.format("%+.1f ST", engineState.pitchSemiTones),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = engineState.pitchSemiTones,
                        onValueChange = { AudioEngineManager.setPitchSemiTones(it) },
                        valueRange = -5.0f..5.0f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = magentaAccent,
                            activeTrackColor = magentaAccent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        QuickPitchChip("-1.0") { AudioEngineManager.setPitchSemiTones(engineState.pitchSemiTones - 1f) }
                        QuickPitchChip("0") { AudioEngineManager.resetPitch() }
                        QuickPitchChip("+1.0") { AudioEngineManager.setPitchSemiTones(engineState.pitchSemiTones + 1f) }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Routing Mode Selector
                    Text(
                        "L/R Routing: ${engineState.routingMode.label}",
                        color = cyanAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ChannelRoutingMode.values().forEach { mode ->
                            val isSelected = engineState.routingMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) cyanAccent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f))
                                    .border(
                                        1.dp,
                                        if (isSelected) cyanAccent else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { AudioEngineManager.setChannelRouting(mode) }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    mode.label,
                                    fontSize = 10.sp,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Close overlay completely
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCloseService() },
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("Exit Overlay", color = Color.Red.copy(alpha = 0.7f), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPitchChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        Text(label, color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
