package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.VocalRemoverMode
import kotlin.math.roundToInt

/**
 * Modern Glassmorphic UI Card for Center Channel Vocal Remover (Phase Inversion DSP).
 *
 * Provides:
 * 1. Segmented Control Buttons (Normal | Hapus Vokal | Isolasi Vokal)
 * 2. Attenuation Level Slider (0% to 100% reduction)
 * 3. Real-time Haptic Feedback on clicks and gestures
 * 4. Sub-Bass Protection Status indicator (~120Hz HPF)
 */
@Composable
fun VocalRemoverGlassCard(
    currentMode: VocalRemoverMode,
    attenuation: Float,
    onModeChange: (VocalRemoverMode) -> Unit,
    onAttenuationChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val accentColor = if (currentMode == VocalRemoverMode.VOCAL_CUT) ElectricMagenta else NeonCyan
    val percentage = (attenuation * 100f).roundToInt()

    Surface(
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(cornerRadius = 16f, borderColor = accentColor)
            .testTag("vocal_remover_card")
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            // Header Row: Title, Icon & Mode Readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (currentMode) {
                            VocalRemoverMode.VOCAL_CUT -> Icons.Default.MicOff
                            VocalRemoverMode.VOCAL_ISOLATION -> Icons.Default.Mic
                            VocalRemoverMode.OFF -> Icons.Default.MusicNote
                        },
                        contentDescription = "Vocal Remover",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "VOCAL REMOVER (PHASE INVERSION)",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Sub-Bass Protection badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF00FF66).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF00FF66).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF00FF66),
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Bass Safe (120Hz HPF)",
                            color = Color(0xFF00FF66),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Mode Selector: Segmented Control Buttons (Normal | Hapus Vokal | Isolasi Vokal)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                VocalRemoverMode.values().forEach { mode ->
                    val isSelected = currentMode == mode
                    val buttonColor = if (isSelected) {
                        when (mode) {
                            VocalRemoverMode.VOCAL_CUT -> ElectricMagenta
                            VocalRemoverMode.VOCAL_ISOLATION -> NeonCyan
                            VocalRemoverMode.OFF -> Color(0xFF6B7280)
                        }
                    } else {
                        Color.White.copy(alpha = 0.06f)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) buttonColor.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.05f))
                            .border(
                                1.dp,
                                if (isSelected) buttonColor else Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onModeChange(mode)
                            }
                            .padding(vertical = 6.dp)
                            .testTag(
                                when (mode) {
                                    VocalRemoverMode.OFF -> "vocal_mode_off"
                                    VocalRemoverMode.VOCAL_CUT -> "vocal_mode_cut"
                                    VocalRemoverMode.VOCAL_ISOLATION -> "vocal_mode_isolation"
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = mode.label,
                                color = if (isSelected) Color.White else Color.LightGray,
                                fontSize = 9.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = when (mode) {
                                    VocalRemoverMode.OFF -> "Stereo Asli"
                                    VocalRemoverMode.VOCAL_CUT -> "Minus-One"
                                    VocalRemoverMode.VOCAL_ISOLATION -> "Acapella"
                                },
                                color = if (isSelected) buttonColor else Color.Gray,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Attenuation Slider Section (Visible and active for both Cut & Isolation modes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (currentMode) {
                        VocalRemoverMode.VOCAL_CUT -> "Tingkat Reduksi Vokal"
                        VocalRemoverMode.VOCAL_ISOLATION -> "Tingkat Isolasi Vokal"
                        VocalRemoverMode.OFF -> "Sensitivitas Reduksi"
                    },
                    color = Color.LightGray,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$percentage%",
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Slider(
                value = attenuation,
                onValueChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAttenuationChange(it)
                },
                valueRange = 0.0f..1.0f,
                steps = 19, // 5% increments
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .testTag("vocal_attenuation_slider")
            )

            // Explanatory note
            Text(
                text = when (currentMode) {
                    VocalRemoverMode.VOCAL_CUT -> "Suara vokal tengah ditiadakan (L - R). Dentuman kick drum & bassline tetap utuh."
                    VocalRemoverMode.VOCAL_ISOLATION -> "Hanya vokal posisi tengah yang diekstrak untuk panduan vokal karaoke."
                    VocalRemoverMode.OFF -> "Mode normal: Suara stereo asli diputar tanpa pembatalan fase vokal."
                },
                color = Color.Gray,
                fontSize = 8.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
