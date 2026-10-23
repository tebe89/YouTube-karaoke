package com.example.audio

import android.util.Log
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Operating Modes for the Vocal Remover / Center Channel Cancellation DSP Engine
 */
enum class VocalRemoverMode(val label: String, val description: String) {
    OFF("Normal", "Audio stereo asli tanpa manipulasi vokal"),
    VOCAL_CUT("Hapus Vokal", "Pembalikan fase 180° untuk menghilangkan vokal tengah"),
    VOCAL_ISOLATION("Isolasi Vokal", "Ekstraksi vokal tengah (Center Isolation)")
}

/**
 * Professional Real-Time Center Channel Cancellation (Phase Inversion) DSP Engine
 * with Sub-Bass Protection High-Pass Filter (~120Hz).
 *
 * Technical Specifications:
 * 1. Center Channel Cancellation:
 *    Cancels sounds positioned in the phantom center (mono vocal image) using phase inversion.
 *    For vocal cut: Left_out = Left_in - Right_in (or Left_in - alpha * Center)
 * 2. Sub-Bass Protection (High-Pass Filter):
 *    Applies a 2nd-order Butterworth High-Pass Filter (Cutoff fc ≈ 120Hz, Q = 0.7071)
 *    to the cancellation signal so that low bass frequencies (Kick drum 50-80Hz, Bassline)
 *    are NEVER cancelled or hollowed out.
 * 3. Mode Processing:
 *    - OFF: Bit-transparent stereo passthrough.
 *    - VOCAL_CUT: Eliminates center-panned vocals while retaining full stereo separation and punchy sub-bass.
 *    - VOCAL_ISOLATION: Extracts center vocal image while suppressing side-panned backing instruments.
 * 4. Adjustable Attenuation:
 *    Linear control from 0% (transparent) to 100% (maximum reduction).
 */
class VocalRemoverDSP(
    private val sampleRate: Int = 44100,
    private var cutoffHz: Float = 120.0f
) {
    companion object {
        private const val TAG = "VocalRemoverDSP"
    }

    private var currentMode: VocalRemoverMode = VocalRemoverMode.OFF
    private var attenuation: Float = 0.85f // 0.0f to 1.0f

    // 2nd-order Butterworth Biquad High-Pass Filter Coefficients
    private var b0 = 1.0f
    private var b1 = -2.0f
    private var b2 = 1.0f
    private var a1 = 0.0f
    private var a2 = 0.0f

    // Direct Form II Transposed state registers for Left and Right channels
    private var s1L = 0.0f
    private var s2L = 0.0f
    private var s1R = 0.0f
    private var s2R = 0.0f

    init {
        updateFilterCoefficients()
    }

    /**
     * Recalculates 2nd-order Butterworth High-Pass Biquad filter coefficients
     * using the Robert Bristow-Johnson (RBJ) Audio EQ Cookbook formula.
     */
    private fun updateFilterCoefficients() {
        val clampedCutoff = cutoffHz.coerceIn(40.0f, 400.0f)
        val omega = 2.0 * PI * clampedCutoff / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val q = 1.0 / sqrt(2.0) // Butterworth Q ≈ 0.7071
        val alpha = sinOmega / (2.0 * q)

        val a0 = 1.0 + alpha
        b0 = ((1.0 + cosOmega) / 2.0 / a0).toFloat()
        b1 = (-(1.0 + cosOmega) / a0).toFloat()
        b2 = ((1.0 + cosOmega) / 2.0 / a0).toFloat()
        a1 = ((-2.0 * cosOmega) / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
    }

    fun setMode(mode: VocalRemoverMode) {
        currentMode = mode
    }

    fun getMode(): VocalRemoverMode = currentMode

    fun setAttenuation(level: Float) {
        attenuation = level.coerceIn(0.0f, 1.0f)
    }

    fun getAttenuation(): Float = attenuation

    fun setCutoffFrequency(cutoff: Float) {
        cutoffHz = cutoff
        updateFilterCoefficients()
    }

    fun reset() {
        s1L = 0.0f
        s2L = 0.0f
        s1R = 0.0f
        s2R = 0.0f
    }

    /**
     * Processes interleaved stereo PCM float audio samples [L0, R0, L1, R1, ...]
     *
     * @param inSamples input interleaved stereo buffer
     * @param outSamples output interleaved stereo buffer
     * @param frameCount number of stereo frames to process (each frame = 2 samples)
     * @return number of samples processed
     */
    fun process(
        inSamples: FloatArray,
        outSamples: FloatArray,
        frameCount: Int = inSamples.size / 2
    ): Int {
        val totalFrames = minOf(frameCount, inSamples.size / 2, outSamples.size / 2)

        if (currentMode == VocalRemoverMode.OFF || attenuation <= 0.001f) {
            // Passthrough mode (zero overhead)
            System.arraycopy(inSamples, 0, outSamples, 0, totalFrames * 2)
            return totalFrames * 2
        }

        val alpha = attenuation

        for (i in 0 until totalFrames) {
            val inL = inSamples[i * 2]
            val inR = inSamples[i * 2 + 1]

            // 1. Apply High-Pass Filter to Left channel
            val hpL = b0 * inL + s1L
            s1L = b1 * inL - a1 * hpL + s2L
            s2L = b2 * inL - a2 * hpL

            // 2. Apply High-Pass Filter to Right channel
            val hpR = b0 * inR + s1R
            s1R = b1 * inR - a1 * hpR + s2R
            s2R = b2 * inR - a2 * hpR

            // 3. Extract untouched Sub-Bass components (f < 120Hz)
            // By complementary filtering: SubBass = Original - HighPass
            val subBassL = inL - hpL
            val subBassR = inR - hpR

            // 4. Center component of mid/high range
            val centerHp = 0.5f * (hpL + hpR)

            val outL: Float
            val outR: Float

            when (currentMode) {
                VocalRemoverMode.VOCAL_CUT -> {
                    // Exact 180° Phase Inversion Center Channel Cancellation:
                    // Left_out = SubBass_L + (hpL - alpha * hpR)
                    // Right_out = SubBass_R + (hpR - alpha * hpL)
                    // When alpha = 1.0, centered vocals (hpL == hpR) result in: hpL - hpR = 0 (100% cancellation)
                    val vocalCutMidL = hpL - (alpha * hpR)
                    val vocalCutMidR = hpR - (alpha * hpL)

                    // Recombine with 100% preserved punchy Sub-Bass (< 120Hz)
                    outL = (subBassL + vocalCutMidL).coerceIn(-1.0f, 1.0f)
                    outR = (subBassR + vocalCutMidR).coerceIn(-1.0f, 1.0f)
                }
                VocalRemoverMode.VOCAL_ISOLATION -> {
                    // Extract center phantom vocal track while dampening side elements
                    val isoL = (1.0f - alpha) * inL + alpha * centerHp
                    val isoR = (1.0f - alpha) * inR + alpha * centerHp
                    outL = isoL.coerceIn(-1.0f, 1.0f)
                    outR = isoR.coerceIn(-1.0f, 1.0f)
                }
                VocalRemoverMode.OFF -> {
                    outL = inL
                    outR = inR
                }
            }

            outSamples[i * 2] = outL
            outSamples[i * 2 + 1] = outR
        }

        return totalFrames * 2
    }

    /**
     * In-place stereo buffer processing for zero memory allocation in audio loops.
     */
    fun processInPlace(buffer: FloatArray, frameCount: Int = buffer.size / 2) {
        process(buffer, buffer, frameCount)
    }
}
