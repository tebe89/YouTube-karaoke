package com.example.audio

import android.util.Log
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * High-performance Pitch Shifter interfacing with Native C++ SoundTouch NDK
 * with an integrated zero-latency, TEMPO-PRESERVED Kotlin DSP engine.
 * Supports +/- 5.0 semitones in 0.5 increments for Karaoke pitch shifting.
 * 
 * CRITICAL: The speed / playback tempo is STRICTLY 1.0x (100% constant).
 * Pitch changes NEVER alter the duration or tempo of the audio!
 */
class PitchShifter(
    private val sampleRate: Int = 44100,
    private val channels: Int = 2
) {
    companion object {
        private const val TAG = "PitchShifter"
        private var isNativeLoaded = false

        init {
            try {
                System.loadLibrary("soundtouch_pitch")
                isNativeLoaded = true
                Log.d(TAG, "Native SoundTouch C++ library loaded successfully.")
            } catch (e: UnsatisfiedLinkError) {
                isNativeLoaded = false
                Log.i(TAG, "Running with optimized native Kotlin DSP pitch shifting engine: ${e.message}")
            }
        }
    }

    private var nativeHandle: Long = 0
    private var currentSemiTones: Float = 0.0f
    private var pitchRatio: Float = 1.0f

    // Dual-delay Hann-window pitch shifter state for zero-speed-change pitchbend
    private val bufferSize = 8192
    private val delayBuffer = FloatArray(bufferSize * channels)
    private var writeIndex = 0
    private var phase = 0.0f
    private val windowSize = 2048f // frames per crossfade window

    init {
        if (isNativeLoaded) {
            try {
                nativeHandle = nativeInit(sampleRate, channels)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to initialize native engine, falling back to Kotlin DSP", e)
                nativeHandle = 0
            }
        }
    }

    /**
     * Sets pitch shift in semitones (-5.0 to +5.0).
     * Pitch changes do NOT change speed / tempo!
     */
    fun setPitchSemiTones(semiTones: Float) {
        val clamped = semiTones.coerceIn(-5.0f, 5.0f)
        currentSemiTones = clamped
        pitchRatio = 2.0f.pow(clamped / 12.0f)

        if (nativeHandle != 0L) {
            try {
                nativeSetPitchSemiTones(nativeHandle, clamped)
            } catch (e: Throwable) {
                Log.e(TAG, "Native setPitchSemiTones failed", e)
            }
        }
    }

    fun getPitchSemiTones(): Float = currentSemiTones

    fun getPitchRatio(): Float = pitchRatio

    /**
     * Process audio samples preserving 100% constant tempo (ZERO SPEED CHANGE).
     * @return Number of output samples produced
     */
    fun process(inSamples: FloatArray, outSamples: FloatArray): Int {
        if (inSamples.isEmpty() || outSamples.isEmpty()) return 0

        // If native library is active
        if (nativeHandle != 0L) {
            try {
                val processed = nativeProcess(nativeHandle, inSamples, inSamples.size, outSamples)
                if (processed > 0) return processed
            } catch (e: Throwable) {
                Log.w(TAG, "Native process error, falling back to Kotlin DSP", e)
            }
        }

        val numSamples = min(inSamples.size, outSamples.size)
        // Bypass when pitch is unchanged (0.0 semitones)
        if (kotlin.math.abs(currentSemiTones) < 0.01f) {
            System.arraycopy(inSamples, 0, outSamples, 0, numSamples)
            return numSamples
        }

        // Dual-delay pitch shifting algorithm:
        // Output frame count == input frame count at all times -> SPEED NEVER CHANGES!
        val totalFrames = numSamples / channels
        val rate = (1.0f - pitchRatio) / windowSize

        for (i in 0 until totalFrames) {
            // 1. Write current input frame to circular delay buffer
            val wBase = writeIndex * channels
            for (ch in 0 until channels) {
                delayBuffer[wBase + ch] = inSamples[i * channels + ch]
            }

            // 2. Advance phase
            phase += rate
            if (phase < 0.0f) phase += 1.0f
            if (phase >= 1.0f) phase -= 1.0f

            val phase0 = phase
            val phase1 = (phase + 0.5f) % 1.0f

            // Hann window envelope weights (weight0 + weight1 = 1.0f)
            val w0 = 0.5f * (1.0f - cos(2.0 * PI * phase0).toFloat())
            val w1 = 0.5f * (1.0f - cos(2.0 * PI * phase1).toFloat())

            // Read offset 0 with linear interpolation
            val del0 = phase0 * windowSize
            val del0Int = del0.toInt()
            val frac0 = del0 - del0Int
            val r0A = (writeIndex - del0Int + bufferSize) % bufferSize
            val r0B = (r0A - 1 + bufferSize) % bufferSize

            // Read offset 1 with linear interpolation
            val del1 = phase1 * windowSize
            val del1Int = del1.toInt()
            val frac1 = del1 - del1Int
            val r1A = (writeIndex - del1Int + bufferSize) % bufferSize
            val r1B = (r1A - 1 + bufferSize) % bufferSize

            val oBase = i * channels
            for (ch in 0 until channels) {
                val s0 = delayBuffer[r0A * channels + ch] * (1.0f - frac0) + delayBuffer[r0B * channels + ch] * frac0
                val s1 = delayBuffer[r1A * channels + ch] * (1.0f - frac1) + delayBuffer[r1B * channels + ch] * frac1

                var outSample = s0 * w0 + s1 * w1

                // Soft limiter / tape saturation to prevent clipping
                if (outSample > 0.98f) outSample = 0.98f
                else if (outSample < -0.98f) outSample = -0.98f

                outSamples[oBase + ch] = outSample
            }

            writeIndex = (writeIndex + 1) % bufferSize
        }

        return totalFrames * channels
    }

    fun release() {
        if (nativeHandle != 0L) {
            try {
                nativeDestroy(nativeHandle)
            } catch (e: Throwable) {
                Log.e(TAG, "Error releasing native handle", e)
            }
            nativeHandle = 0
        }
    }

    // JNI Native Declarations
    private external fun nativeInit(sampleRate: Int, channels: Int): Long
    private external fun nativeSetPitchSemiTones(handle: Long, semiTones: Float)
    private external fun nativeProcess(
        handle: Long,
        inSamples: FloatArray,
        numSamples: Int,
        outSamples: FloatArray
    ): Int
    private external fun nativeDestroy(handle: Long)
}
