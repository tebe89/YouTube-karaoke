package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import android.view.MotionEvent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.webkit.PermissionRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SurroundSound
import androidx.core.content.ContextCompat
import java.util.Locale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.audio.AudioEngineManager
import com.example.audio.ChannelRoutingMode
import com.example.audio.VocalRemoverMode
import com.example.service.AudioForegroundService
import com.example.service.FloatingOverlayService
import com.example.shizuku.ShizukuOptimizer
import com.example.shizuku.ShizukuState
import com.example.webview.AdBlockWebViewClient
import kotlinx.coroutines.delay
import java.net.URLEncoder
import kotlin.math.abs

// Custom Glassmorphic Theme Palette for karaokeTB
val GlassBgSlate = Color(0xFF0A0C10)
val GlassBgIndigo = Color(0xFF121824)
val NeonCyan = Color(0xFF00F0FF)
val ElectricMagenta = Color(0xFFFF007A)
val SoftWhite = Color(0xFFF0F4F8)
val GlassPanelFill = Color(0x1AFFFFFF) // 10% translucent white
val GlassPanelBorderColor = Color(0x3300F0FF)

/**
 * Display modes for HTML5 YouTube video view:
 * FULLSCREEN: 100% immersive edge-to-edge view
 * MINIPLAYER: Smoothly shrunk floating picture-in-picture card in bottom right
 */
enum class CustomVideoMode {
    FULLSCREEN,
    MINIPLAYER
}

/**
 * Modifier extension for ultra-modern frosted glass panels
 */
fun Modifier.glassPanel(
    cornerRadius: Float = 18f,
    fillColor: Color = Color(0x14FFFFFF),
    borderColor: Color = GlassPanelBorderColor,
    borderWidth: Float = 1.2f
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius.dp))
    .drawBehind {
        drawRoundRect(
            color = fillColor,
            cornerRadius = CornerRadius(cornerRadius.dp.toPx(), cornerRadius.dp.toPx())
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(
                    borderColor.copy(alpha = 0.6f),
                    borderColor.copy(alpha = 0.15f),
                    borderColor.copy(alpha = 0.45f)
                )
            ),
            cornerRadius = CornerRadius(cornerRadius.dp.toPx(), cornerRadius.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = borderWidth.dp.toPx())
        )
    }

/**
 * Main karaokeTB Application Screen
 * Full YouTube Experience with:
 * 1. 100% Fullscreen YouTube WebView without status bar interference
 * 2. 2-Finger Tap Gesture to toggle floating controls (Gear ⚙️ and Navigation Pill)
 * 3. Search Button with instant frosted glass Karaoke Search Modal
 * 4. Frosted Glass Karaoke Audio Settings (Vocal Remover, Pitchbend, L/R Matrix)
 * 5. Native HTML5 video fullscreen support via WebChromeClient.onShowCustomView
 * 6. Device back gesture navigation (back in YouTube history, exit video fullscreen, or close modals)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TBSoundLabScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val engineState by AudioEngineManager.engineState.collectAsState()
    val shizukuState by ShizukuOptimizer.state.collectAsState()

    var showShizukuDialog by remember { mutableStateOf(false) }
    var isAudioSettingsOpen by remember { mutableStateOf(false) }
    var showSearchModal by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Floating UI visibility state - toggled exclusively via 2-finger tap
    var isGearVisible by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // HTML5 Fullscreen Video Custom View states
    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var customVideoMode by remember { mutableStateOf(CustomVideoMode.FULLSCREEN) }

    var blockedAdCount by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    // Auto-hide floating controls after 4s of inactivity when visible (only when not setting/modals closed)
    LaunchedEffect(isGearVisible, lastInteractionTime, isAudioSettingsOpen, showSearchModal) {
        if (isGearVisible && !isAudioSettingsOpen && !showSearchModal) {
            delay(4000)
            isGearVisible = false
        }
    }

    // Launcher for Audio / Microphone Permission
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Izin mikrofon aktif. Pencarian suara siap digunakan!", Toast.LENGTH_SHORT).show()
        }
    }

    // Request RECORD_AUDIO directly on app startup so voice search is immediately functional
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Start background audio playback service immediately
    LaunchedEffect(Unit) {
        AudioForegroundService.startService(context)
    }

    // Single persistent WebView instance configured for video, Web Audio DSP & HTML5 Fullscreen
    val persistentWebView = remember(context) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    customVideoView = view
                    customViewCallback = callback
                    customVideoMode = CustomVideoMode.FULLSCREEN
                }

                override fun onHideCustomView() {
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null
                    customVideoView = null
                    customVideoMode = CustomVideoMode.FULLSCREEN
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    // Automatically grant audio capture permission to YouTube web for in-page voice search
                    request?.grant(request.resources)
                }
            }

            // JavaScript Bridge for YouTube gestures (Swipe Up -> Fullscreen, Swipe Down -> Exit)
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun onSwipeUp() {
                    post {
                        if (customVideoView != null && customVideoMode == CustomVideoMode.MINIPLAYER) {
                            customVideoMode = CustomVideoMode.FULLSCREEN
                        }
                    }
                }

                @android.webkit.JavascriptInterface
                fun onSwipeDown() {
                    post {
                        if (customVideoView != null && customVideoMode == CustomVideoMode.FULLSCREEN) {
                            customVideoMode = CustomVideoMode.MINIPLAYER
                        }
                    }
                }
            }, "AndroidBridge")

            // Native touch listener detecting swipe UP on video player area
            var touchStartX = 0f
            var touchStartY = 0f
            var touchStartTime = 0L

            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartX = event.x
                        touchStartY = event.y
                        touchStartTime = System.currentTimeMillis()
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = event.x - touchStartX
                        val dy = event.y - touchStartY
                        val dt = System.currentTimeMillis() - touchStartTime
                        // Detect upward swipe on the top player portion (video player area)
                        if (dt < 600 && dy < -50 && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.25f) {
                            val density = v.resources.displayMetrics.density
                            if (touchStartY < 320 * density) {
                                v.post {
                                    (v as? WebView)?.evaluateJavascript(
                                        "window.tbEnterFullscreen && window.tbEnterFullscreen();",
                                        null
                                    )
                                }
                            }
                        }
                    }
                }
                false
            }
            webViewClient = object : AdBlockWebViewClient(
                onAdBlockedCountChanged = { count -> blockedAdCount = count }
            ) {
                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    canGoBack = view?.canGoBack() == true
                    canGoForward = view?.canGoForward() == true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    canGoBack = view?.canGoBack() == true
                    canGoForward = view?.canGoForward() == true
                }
            }
            loadUrl("https://m.youtube.com")
        }
    }

    // Direct Voice Search Launcher for floating button and modals
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                searchQuery = spoken
                val encoded = URLEncoder.encode(spoken.trim(), "UTF-8")
                persistentWebView.loadUrl("https://m.youtube.com/results?search_query=$encoded")
            }
        }
    }

    // Register webView with AudioEngineManager for two-way audio synchronization
    DisposableEffect(persistentWebView) {
        AudioEngineManager.registerWebView(persistentWebView)
        onDispose {
            AudioEngineManager.unregisterWebView(persistentWebView)
        }
    }

    // Reactively apply pitch, routing & vocal remover changes whenever engineState changes
    LaunchedEffect(engineState.pitchSemiTones, engineState.routingMode, engineState.vocalRemoverMode, engineState.vocalAttenuation) {
        val pitch = engineState.pitchSemiTones
        val routing = engineState.routingMode.name
        val vocalMode = engineState.vocalRemoverMode.name
        val vocalAtten = engineState.vocalAttenuation
        persistentWebView.evaluateJavascript(
            "if (window.tbSetPitch) window.tbSetPitch($pitch); if (window.tbSetRouting) window.tbSetRouting('$routing'); if (window.tbSetVocalRemover) window.tbSetVocalRemover('$vocalMode', $vocalAtten);",
            null
        )
    }

    // Device Hardware & Gesture Back Navigation (matching browser back navigation)
    // 1. If search modal open -> close search modal
    // 2. Else if audio settings open -> close audio settings
    // 3. Else if in HTML5 video fullscreen -> exit fullscreen video back to YouTube page cleanly
    // 4. Else if can navigate back in YouTube history -> go back in YouTube
    // 5. Else -> standard Android back (minimizes/exits app)
    BackHandler(enabled = showSearchModal || isAudioSettingsOpen || customVideoView != null || canGoBack) {
        when {
            showSearchModal -> {
                showSearchModal = false
            }
            isAudioSettingsOpen -> {
                isAudioSettingsOpen = false
            }
            customVideoView != null -> {
                if (customVideoMode == CustomVideoMode.FULLSCREEN) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    customVideoMode = CustomVideoMode.MINIPLAYER
                } else {
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null
                    customVideoView = null
                    customVideoMode = CustomVideoMode.FULLSCREEN
                }
            }
            canGoBack -> {
                persistentWebView.goBack()
            }
        }
    }

    // Root Container - detects 2-finger tap to toggle floating menu without blocking single-finger YouTube gestures
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var twoFingerTriggered = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pointerCount = event.changes.size
                        val allPressed = event.changes.all { it.pressed }

                        if (pointerCount >= 2 && allPressed) {
                            if (!twoFingerTriggered) {
                                twoFingerTriggered = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isGearVisible = !isGearVisible
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        } else {
                            val anyPressed = event.changes.any { it.pressed }
                            if (!anyPressed) {
                                twoFingerTriggered = false
                            }
                        }
                    }
                }
            }
    ) {
        // 1. Fullscreen Native YouTube WebView (100% of the screen)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                (persistentWebView.parent as? ViewGroup)?.removeView(persistentWebView)
                persistentWebView
            }
        )

        // 2. HTML5 Video Overlay (Smoothly animates between Fullscreen and MiniPlayer)
        if (customVideoView != null) {
            val isMini = customVideoMode == CustomVideoMode.MINIPLAYER

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val fullWidth = maxWidth
                val fullHeight = maxHeight

                val miniWidth = 220.dp
                val miniHeight = 124.dp

                val targetWidth = if (isMini) miniWidth else fullWidth
                val targetHeight = if (isMini) miniHeight else fullHeight
                val targetOffsetX = if (isMini) (fullWidth - miniWidth - 16.dp) else 0.dp
                val targetOffsetY = if (isMini) (fullHeight - miniHeight - 20.dp) else 0.dp

                val animWidth by animateDpAsState(
                    targetValue = targetWidth,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                    label = "animWidth"
                )
                val animHeight by animateDpAsState(
                    targetValue = targetHeight,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                    label = "animHeight"
                )
                val animOffsetX by animateDpAsState(
                    targetValue = targetOffsetX,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                    label = "animOffsetX"
                )
                val animOffsetY by animateDpAsState(
                    targetValue = targetOffsetY,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                    label = "animOffsetY"
                )
                val animRadius by animateDpAsState(
                    targetValue = if (isMini) 14.dp else 0.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "animRadius"
                )

                Box(
                    modifier = Modifier
                        .offset(x = animOffsetX, y = animOffsetY)
                        .size(animWidth, animHeight)
                        .shadow(if (isMini) 16.dp else 0.dp, RoundedCornerShape(animRadius))
                        .clip(RoundedCornerShape(animRadius))
                        .background(Color.Black)
                        .then(
                            if (isMini) Modifier.border(1.5.dp, NeonCyan.copy(alpha = 0.85f), RoundedCornerShape(animRadius))
                            else Modifier
                        )
                        .pointerInput(isMini) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (!isMini) {
                                    // Fullscreen: Swipe down -> shrink to miniplayer!
                                    if (dragAmount > 25) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        customVideoMode = CustomVideoMode.MINIPLAYER
                                    }
                                } else {
                                    // Miniplayer: Swipe up -> expand to fullscreen!
                                    if (dragAmount < -20) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        customVideoMode = CustomVideoMode.FULLSCREEN
                                    } else if (dragAmount > 25) {
                                        // Swipe down on miniplayer -> dismiss!
                                        customViewCallback?.onCustomViewHidden()
                                        customViewCallback = null
                                        customVideoView = null
                                        customVideoMode = CustomVideoMode.FULLSCREEN
                                    }
                                }
                            }
                        }
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            (customVideoView!!.parent as? ViewGroup)?.removeView(customVideoView)
                            customVideoView!!
                        }
                    )

                    if (isMini) {
                        // Tapping on miniplayer expands to Fullscreen
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    customVideoMode = CustomVideoMode.FULLSCREEN
                                }
                        )

                        // MiniPlayer Header with Expand & Close
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .background(Color(0xAA0D131F))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Karaoke Video",
                                color = NeonCyan,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        customVideoMode = CustomVideoMode.FULLSCREEN
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Perbesar Video",
                                        tint = SoftWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        customViewCallback?.onCustomViewHidden()
                                        customViewCallback = null
                                        customVideoView = null
                                        customVideoMode = CustomVideoMode.FULLSCREEN
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Tutup Video",
                                        tint = SoftWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // In Fullscreen: Top-left subtle collapse button (like YouTube)
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                customVideoMode = CustomVideoMode.MINIPLAYER
                            },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 14.dp, top = 14.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x88000000))
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Kecilkan Layar Video",
                                tint = SoftWhite,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Floating Quick Action Controls (Appears on 2-finger tap; auto-hides after 4.5s)
        AnimatedVisibility(
            visible = isGearVisible || isAudioSettingsOpen || showSearchModal,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top-Left Floating Navigation Pill: Back, Home, Search & Reload
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 14.dp, top = 14.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xDD0D131F))
                        .border(1.2.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Back button in history or exit fullscreen
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastInteractionTime = System.currentTimeMillis()
                            if (customVideoView != null) {
                                if (customVideoMode == CustomVideoMode.FULLSCREEN) {
                                    customVideoMode = CustomVideoMode.MINIPLAYER
                                } else {
                                    customViewCallback?.onCustomViewHidden()
                                    customViewCallback = null
                                    customVideoView = null
                                    customVideoMode = CustomVideoMode.FULLSCREEN
                                }
                            } else if (canGoBack) {
                                persistentWebView.goBack()
                            }
                        },
                        enabled = canGoBack || customVideoView != null,
                        modifier = Modifier.size(34.dp).testTag("floating_nav_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (canGoBack || customVideoView != null) NeonCyan else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Home button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastInteractionTime = System.currentTimeMillis()
                            persistentWebView.loadUrl("https://m.youtube.com")
                        },
                        modifier = Modifier.size(34.dp).testTag("floating_nav_home")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "YouTube Home",
                            tint = SoftWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Tombol Pencarian (Search Button)
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastInteractionTime = System.currentTimeMillis()
                            showSearchModal = true
                        },
                        modifier = Modifier.size(34.dp).testTag("floating_nav_search")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cari Lagu Karaoke",
                            tint = NeonCyan,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Tombol Pencarian Suara (Voice Search Mic)
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastInteractionTime = System.currentTimeMillis()
                            val hasMic = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasMic) {
                                try {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Sebutkan lagu karaoke yang ingin dicari...")
                                    }
                                    speechRecognizerLauncher.launch(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Voice Search tidak tersedia di perangkat ini", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.size(34.dp).testTag("floating_nav_mic")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Pencarian Suara",
                            tint = ElectricMagenta,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Refresh button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastInteractionTime = System.currentTimeMillis()
                            persistentWebView.reload()
                        },
                        modifier = Modifier.size(34.dp).testTag("floating_nav_refresh")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload",
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Top-Right Floating Controls: Audio Settings Gear
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 14.dp, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Floating Gear Button (⚙️) to open Audio Settings
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF1E2838), Color(0xEE0A0E17))
                                )
                            )
                            .border(1.5.dp, if (isAudioSettingsOpen) ElectricMagenta else NeonCyan, CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                lastInteractionTime = System.currentTimeMillis()
                                isAudioSettingsOpen = !isAudioSettingsOpen
                            }
                            .testTag("floating_gear_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Audio Settings",
                            tint = if (isAudioSettingsOpen) ElectricMagenta else NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // 4. Frosted Glass Karaoke Search Modal Dialog
        if (showSearchModal) {
            KaraokeSearchModal(
                initialQuery = searchQuery,
                onSearch = { query ->
                    if (query.isNotBlank()) {
                        searchQuery = query
                        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                        persistentWebView.loadUrl("https://m.youtube.com/results?search_query=$encoded")
                    }
                    showSearchModal = false
                },
                onDismiss = { showSearchModal = false }
            )
        }

        // 5. Frosted Glass Karaoke Audio Settings Modal Dialog
        if (isAudioSettingsOpen) {
            KaraokeAudioSettingsModal(
                engineState = engineState,
                shizukuState = shizukuState,
                persistentWebView = persistentWebView,
                blockedAdCount = blockedAdCount,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                onOpenSearch = {
                    isAudioSettingsOpen = false
                    showSearchModal = true
                },
                onDismiss = { isAudioSettingsOpen = false },
                onOpenShizuku = { showShizukuDialog = true }
            )
        }

        // 6. Shizuku Optimizer Dialog
        if (showShizukuDialog) {
            ShizukuOptimizerDialog(
                state = shizukuState,
                onDismiss = { showShizukuDialog = false },
                onRequestPermission = {
                    ShizukuOptimizer.requestShizukuPermission()
                },
                onRunOptimization = {
                    ShizukuOptimizer.executeElevatedOptimizations()
                    Toast.makeText(context, "Elevated audio priority executed", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

/**
 * Frosted Glass Karaoke Search Modal Dialog
 * Allows users to type search queries or tap instant karaoke chips
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KaraokeSearchModal(
    initialQuery: String,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var queryText by remember { mutableStateOf(initialQuery) }

    // Launcher for Android Speech Recognition
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                queryText = spoken
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSearch(spoken)
            }
        }
    }

    val micRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Sebutkan lagu karaoke yang ingin dicari...")
                }
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Voice Search tidak tersedia di perangkat ini", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Izin mikrofon diperlukan untuk pencarian suara", Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerVoiceSearch() {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Sebutkan lagu karaoke yang ingin dicari...")
                }
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Voice Search tidak tersedia di perangkat ini", Toast.LENGTH_SHORT).show()
            }
        } else {
            micRequestLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clickable(enabled = false) {}
                    .glassPanel(cornerRadius = 24f, fillColor = Color(0xF50D131F), borderColor = NeonCyan, borderWidth = 1.5f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // Header: Title and Close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CARI LAGU KARAOKE",
                                color = SoftWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp).testTag("close_search_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Search input text field
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_text_input"),
                        placeholder = {
                            Text(
                                "Ketik judul lagu, artis, atau klik Mic...",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        },
                        singleLine = true,
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (queryText.isNotEmpty()) {
                                    IconButton(
                                        onClick = { queryText = "" },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Hapus",
                                            tint = Color.LightGray,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        triggerVoiceSearch()
                                    },
                                    modifier = Modifier.size(34.dp).testTag("modal_search_mic_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Pencarian Suara",
                                        tint = ElectricMagenta,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                onSearch(queryText)
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = NeonCyan.copy(alpha = 0.35f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = NeonCyan
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search Buttons: Text Search & Voice Search (Mic)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                onSearch(queryText)
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .height(44.dp)
                                .testTag("submit_search_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cari di YouTube",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                triggerVoiceSearch()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("submit_voice_search_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricMagenta),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Bicara (Mic)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "REKOMENDASI KATA KUNCI KARAOKE:",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Instant 1-tap Karaoke Search Chips
                    val karaokeTags = listOf(
                        "🎤 Karaoke No Vocal",
                        "🎵 Karaoke Lirik",
                        "🎸 Karaoke Minus One",
                        "🇮🇩 Karaoke Indonesia",
                        "✨ Dangdut Koplo Karaoke",
                        "🌍 English Karaoke",
                        "🎙️ Karaoke Nada Rendah",
                        "🎹 Akustik Karaoke"
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        karaokeTags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        keyboardController?.hide()
                                        onSearch(tag.substringAfter(" "))
                                    }
                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = tag,
                                    color = SoftWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern Frosted Glass Karaoke Audio Settings Modal Dialog
 * Contains:
 * - Vocal Remover / Center Channel Cancellation (Phase Inversion + 120Hz Sub-Bass Protection)
 * - Pitchbend Key Shifter (-5.0 to +5.0 semitones with 0-tempo alteration)
 * - L/R Channel Matrix Routing (Stereo, Left Only, Right Only, Mono Downmix)
 * - Search Shortcut, Master Power & Shizuku Status
 */
@Composable
fun KaraokeAudioSettingsModal(
    engineState: com.example.audio.AudioEngineState,
    shizukuState: ShizukuState,
    persistentWebView: WebView,
    blockedAdCount: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onOpenSearch: () -> Unit,
    onDismiss: () -> Unit,
    onOpenShizuku: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.90f)
                    .clickable(enabled = false) {}
                    .glassPanel(cornerRadius = 24f, fillColor = Color(0xF50D131F), borderColor = NeonCyan, borderWidth = 1.5f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Modal Header Row: Title, Master Switch & Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(ElectricMagenta, NeonCyan))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Equalizer,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "YouTube karaoke AUDIO LAB",
                                    color = SoftWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Vocal Remover • Pitch Shifter • L/R Routing",
                                    color = Color.LightGray.copy(alpha = 0.8f),
                                    fontSize = 8.5.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Master Engine Switch
                            Switch(
                                checked = engineState.isMasterEnabled,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    AudioEngineManager.setMasterEnabled(!engineState.isMasterEnabled)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NeonCyan,
                                    checkedTrackColor = NeonCyan.copy(alpha = 0.35f),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.08f)
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Close Button (✕)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onDismiss()
                                    }
                                    .testTag("settings_modal_close_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrollable Audio Settings Content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // =========================================================================
                        // 1. VOCAL REMOVER (CENTER CHANNEL CANCELLATION & SUB-BASS PROTECTION)
                        // =========================================================================
                        VocalRemoverGlassCard(
                            currentMode = engineState.vocalRemoverMode,
                            attenuation = engineState.vocalAttenuation,
                            onModeChange = { mode ->
                                AudioEngineManager.setVocalRemoverMode(mode)
                                persistentWebView.evaluateJavascript(
                                    "if (window.tbSetVocalRemover) window.tbSetVocalRemover('${mode.name}', ${engineState.vocalAttenuation});",
                                    null
                                )
                            },
                            onAttenuationChange = { atten ->
                                AudioEngineManager.setVocalAttenuation(atten)
                                persistentWebView.evaluateJavascript(
                                    "if (window.tbSetVocalRemover) window.tbSetVocalRemover('${engineState.vocalRemoverMode.name}', $atten);",
                                    null
                                )
                            }
                        )

                        // =========================================================================
                        // 2. SOUNDTOUCH PITCHBEND / KEY SHIFTER (ZERO-TEMPO CHANGE)
                        // =========================================================================
                        Surface(
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassPanel(cornerRadius = 16f, borderColor = NeonCyan)
                                .testTag("pitchbend_card")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = NeonCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "PITCHBEND (KEY SHIFTER)",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Key readout badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (abs(engineState.pitchSemiTones) > 0.05f) ElectricMagenta.copy(alpha = 0.25f)
                                                else NeonCyan.copy(alpha = 0.15f)
                                            )
                                            .border(
                                                1.dp,
                                                if (abs(engineState.pitchSemiTones) > 0.05f) ElectricMagenta else NeonCyan,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (engineState.pitchSemiTones == 0.0f) "ORIGINAL (0 ST)"
                                            else String.format("%+.1f ST", engineState.pitchSemiTones),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text(
                                    text = "Mengubah nada dasar lagu tanpa merubah kecepatan (Speed tetap 1.0x)",
                                    color = Color.Gray,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                                )

                                // Continuous Pitch Slider
                                Slider(
                                    value = engineState.pitchSemiTones,
                                    onValueChange = { newVal ->
                                        AudioEngineManager.setPitchSemiTones(newVal)
                                        persistentWebView.evaluateJavascript(
                                            "if (window.tbSetPitch) window.tbSetPitch($newVal);",
                                            null
                                        )
                                    },
                                    valueRange = -5.0f..5.0f,
                                    steps = 19,
                                    colors = SliderDefaults.colors(
                                        thumbColor = NeonCyan,
                                        activeTrackColor = NeonCyan,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                                    ),
                                    modifier = Modifier.testTag("pitch_slider")
                                )

                                // Quick Semitone Buttons: -3, -2, -1, 0, +1, +2, +3
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val quickSteps = listOf(-3f, -2f, -1f, 0f, 1f, 2f, 3f)
                                    quickSteps.forEach { step ->
                                        val isCurrent = abs(engineState.pitchSemiTones - step) < 0.2f
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isCurrent) NeonCyan.copy(alpha = 0.35f)
                                                    else Color.White.copy(alpha = 0.05f)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isCurrent) NeonCyan else Color.White.copy(alpha = 0.15f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    AudioEngineManager.setPitchSemiTones(step)
                                                    persistentWebView.evaluateJavascript(
                                                        "if (window.tbSetPitch) window.tbSetPitch($step);",
                                                        null
                                                    )
                                                }
                                                .padding(horizontal = 7.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (step == 0f) "Reset" else String.format("%+.0f", step),
                                                color = if (isCurrent) Color.White else Color.LightGray,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // =========================================================================
                        // 3. KARAOKE L/R CHANNEL MATRIX ROUTING
                        // =========================================================================
                        Surface(
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassPanel(cornerRadius = 16f, borderColor = NeonCyan)
                                .testTag("lr_routing_card")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.SurroundSound,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "L/R CHANNEL ROUTING (MINUS-ONE KARAOKE)",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "Untuk video VCD karaoke L/R (Kiri = Musik, Kanan = Vokal Guide)",
                                    color = Color.Gray,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ChannelRoutingMode.entries.forEach { mode ->
                                        val isSelected = engineState.routingMode == mode
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isSelected) NeonCyan.copy(alpha = 0.35f)
                                                    else Color.White.copy(alpha = 0.05f)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSelected) NeonCyan else Color.White.copy(alpha = 0.12f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    AudioEngineManager.setChannelRouting(mode)
                                                    persistentWebView.evaluateJavascript(
                                                        "if (window.tbSetRouting) window.tbSetRouting('${mode.name}');",
                                                        null
                                                    )
                                                }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = when (mode) {
                                                    ChannelRoutingMode.STEREO -> "Stereo"
                                                    ChannelRoutingMode.LEFT_ONLY -> "Musik (L)"
                                                    ChannelRoutingMode.RIGHT_ONLY -> "Vokal (R)"
                                                    ChannelRoutingMode.MONO_DOWNMIX -> "Mono"
                                                },
                                                color = if (isSelected) Color.White else Color.LightGray,
                                                fontSize = 9.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // =========================================================================
                        // 4. QUICK SEARCH SHORTCUT & UTILITIES
                        // =========================================================================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Search Shortcut
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NeonCyan.copy(alpha = 0.15f))
                                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .clickable { onOpenSearch() }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cari Lagu Karaoke", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // AdBlock Counter Badge
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF00FF66).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFF00FF66).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF00FF66), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ads Blocked: $blockedAdCount", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Shizuku Optimizer Trigger
                        val isShizukuActive = shizukuState is ShizukuState.Active
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(
                                    1.dp,
                                    if (isShizukuActive) Color(0xFF00FF66) else NeonCyan.copy(alpha = 0.4f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onOpenShizuku() }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = if (isShizukuActive) Color(0xFF00FF66) else NeonCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isShizukuActive) "Priority Optimized (Shizuku Aktif)" else "Shizuku Optimizer (Prioritas CPU Rendah Latensi)",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Floating Bubble Overlay Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (Settings.canDrawOverlays(context)) {
                                    FloatingOverlayService.start(context)
                                    Toast.makeText(context, "Floating bubble active", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Grant 'Display over other apps' permission", Toast.LENGTH_LONG).show()
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            }
                            .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PictureInPicture, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Aktifkan Floating Bubble Menu", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shizuku Optimizer Dialog
 */
@Composable
fun ShizukuOptimizerDialog(
    state: ShizukuState,
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit,
    onRunOptimization: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Shizuku Audio Optimization", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Execute elevated process priority (renice -19) and grant background operations for zero audio dropouts and minimal latency.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(10.dp)
                ) {
                    val statusText = when (state) {
                        is ShizukuState.Active -> "Authorized & Priority Optimized"
                        is ShizukuState.PermissionRequired -> "Permission Required"
                        is ShizukuState.NotRunning -> "Service Not Running"
                        is ShizukuState.NotInstalledOrUnavailable -> "Shizuku Not Installed / Unavailable"
                        is ShizukuState.Checking -> "Checking Status..."
                    }
                    val isAuthorized = state is ShizukuState.Active

                    Column {
                        Text(
                            text = "Status: $statusText",
                            color = if (isAuthorized) Color(0xFF00FF66) else Color.Yellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (state is ShizukuState.Active) state.logMessage else "Elevated background scheduling",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state is ShizukuState.PermissionRequired) {
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Grant Permission", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            } else if (state is ShizukuState.Active) {
                Button(
                    onClick = onRunOptimization,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF66))
                ) {
                    Text("Optimize Now", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.LightGray)
            }
        }
    )
}
