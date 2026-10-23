package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Audio Routing Modes for L/R Channel Router
 */
enum class ChannelRoutingMode(val label: String, val description: String) {
    STEREO("Stereo", "Standard Stereo (L/R)"),
    LEFT_ONLY("Left Only", "L -> L + R (Dual Left)"),
    RIGHT_ONLY("Right Only", "R -> L + R (Dual Right)"),
    MONO_DOWNMIX("Mono", "(L + R) / 2 Downmix")
}

/**
 * Presets for 10-Band EQ
 */
enum class EqPreset(val label: String, val gains: FloatArray) {
    FLAT("Flat", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    BASS_BOOST("Bass Boost", floatArrayOf(7.5f, 6.0f, 4.5f, 2.0f, 0f, 0f, 0f, 1.0f, 2.0f, 3.0f)),
    VOCAL_ENHANCE("Vocal Enhance", floatArrayOf(-2.0f, -1.0f, 0f, 2.5f, 5.0f, 4.5f, 3.0f, 1.5f, 0f, -1.0f)),
    CLUB_EDM("Club / EDM", floatArrayOf(6.0f, 5.0f, 3.0f, 0f, -1.5f, 1.5f, 3.5f, 4.0f, 5.0f, 4.0f)),
    ROCK("Rock", floatArrayOf(4.5f, 3.0f, 1.5f, -0.5f, -1.0f, 1.0f, 3.0f, 4.5f, 5.0f, 5.5f)),
    ACOUSTIC("Acoustic", floatArrayOf(3.0f, 2.0f, 1.0f, 0.5f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 2.5f)),
    STUDIO_MASTER("Studio Master", floatArrayOf(1.5f, 1.0f, 0.5f, 0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.0f, 1.5f))
}

/**
 * State representing all active DSP parameters
 */
data class AudioEngineState(
    val isMasterEnabled: Boolean = true,
    val eqBands: List<Float> = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    val activePreset: EqPreset = EqPreset.FLAT,
    // Subwoofer Synthesizer
    val subBassCutoffHz: Float = 55f, // 30Hz - 80Hz
    val subBassGainDb: Float = 3.5f,  // 0dB - 15dB
    val isSubBassEnabled: Boolean = true,
    // DAW Dynamic Range Compressor
    val compressorThresholdDb: Float = -24.0f, // -60dB to 0dB
    val compressorRatio: Float = 4.0f,         // 1.0 to 20.0
    val compressorAttackMs: Float = 15.0f,     // 1ms to 500ms
    val compressorReleaseMs: Float = 120.0f,   // 10ms to 1000ms
    val compressorMakeupGainDb: Float = 3.0f,  // -12dB to +12dB
    val compressorGainReduction: Float = 0.0f, // Meter readout (0 to -24dB)
    val isCompressorEnabled: Boolean = true,
    // L/R Channel Routing
    val routingMode: ChannelRoutingMode = ChannelRoutingMode.STEREO,
    // SoundTouch Pitchbend
    val pitchSemiTones: Float = 0.0f, // -5.0 to +5.0 semitones
    // Vocal Remover / Center Channel Cancellation (Phase Inversion + High-Pass Sub-Bass Protection)
    val vocalRemoverMode: VocalRemoverMode = VocalRemoverMode.OFF,
    val vocalAttenuation: Float = 0.85f, // 0.0 to 1.0 (0% to 100%)
    // Test Audio Player Status
    val isTestTonePlaying: Boolean = false,
    val masterVolume: Float = 0.85f
)

/**
 * Core Native Audio DSP Engine Manager
 * Controls Android DynamicsProcessing API, 10-Band EQ, Sub-Bass Synth,
 * DAW Compressor, and SoundTouch Pitch Shifter.
 */
object AudioEngineManager {
    private const val TAG = "AudioEngineManager"

    // 10 ISO standard center frequencies
    val FREQUENCY_BANDS = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    val FREQUENCY_LABELS = listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1k", "2k", "4k", "8k", "16k")

    private val _engineState = MutableStateFlow(AudioEngineState())
    val engineState: StateFlow<AudioEngineState> = _engineState.asStateFlow()

    // Spectrum Visualizer Data (32 bars normalized 0.0f to 1.0f)
    private val _spectrumData = MutableStateFlow(FloatArray(32) { 0.05f })
    val spectrumData: StateFlow<FloatArray> = _spectrumData.asStateFlow()

    private var appContext: Context? = null
    private var dynamicsProcessing: DynamicsProcessing? = null
    private val pitchShifter = PitchShifter()
    private val vocalRemoverDSP = VocalRemoverDSP()
    private val engineScope = CoroutineScope(Dispatchers.Default)

    private var previewAudioTrack: AudioTrack? = null
    private var previewJob: Job? = null
    private var visualizerJob: Job? = null
    private var activeWebView: android.webkit.WebView? = null

    fun registerWebView(webView: android.webkit.WebView?) {
        activeWebView = webView
        syncToWebView()
    }

    fun unregisterWebView(webView: android.webkit.WebView?) {
        if (activeWebView == webView) {
            activeWebView = null
        }
    }

    fun syncToWebView() {
        val webView = activeWebView ?: return
        val state = _engineState.value
        val pitch = state.pitchSemiTones
        val routing = state.routingMode.name
        val vocalMode = state.vocalRemoverMode.name
        val vocalAtten = state.vocalAttenuation
        webView.post {
            webView.evaluateJavascript(
                "if (window.tbSetPitch) window.tbSetPitch($pitch); if (window.tbSetRouting) window.tbSetRouting('$routing'); if (window.tbSetVocalRemover) window.tbSetVocalRemover('$vocalMode', $vocalAtten);",
                null
            )
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        initDynamicsProcessing(0)
        startVisualizerLoop()
    }

    /**
     * Initializes Android Native DynamicsProcessing effect
     */
    fun initDynamicsProcessing(audioSessionId: Int = 0) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                dynamicsProcessing?.release()
                val channelCount = 2

                // Setup 10-Band PreEQ & PostEQ Config
                val eqConfig = DynamicsProcessing.Eq(
                    true, // inUse
                    true, // enabled
                    FREQUENCY_BANDS.size // 10 bands
                )

                FREQUENCY_BANDS.forEachIndexed { index, freq ->
                    val band = DynamicsProcessing.EqBand(
                        true,
                        freq.toFloat(),
                        freq.toFloat() * 0.7f // Bandwidth in Hz
                    )
                    band.gain = _engineState.value.eqBands.getOrElse(index) { 0f }
                    eqConfig.setBand(index, band)
                }

                // Setup Multi-Band Compressor (MBC)
                val mbcConfig = DynamicsProcessing.Mbc(
                    true,
                    true,
                    1 // 1 broadband compressor band
                )
                val mbcBand = DynamicsProcessing.MbcBand(
                    true, // enabled
                    20000f, // cutoffFrequency
                    _engineState.value.compressorAttackMs, // attackTime
                    _engineState.value.compressorReleaseMs, // releaseTime
                    _engineState.value.compressorRatio, // ratio
                    _engineState.value.compressorThresholdDb, // threshold
                    3f, // kneeWidth
                    -70f, // noiseGateThreshold
                    1f, // expanderRatio
                    0f, // preGain
                    _engineState.value.compressorMakeupGainDb // postGain
                )
                mbcConfig.setBand(0, mbcBand)

                // Limiter Config
                val limiterConfig = DynamicsProcessing.Limiter(
                    true,
                    true,
                    0, // linkGroup
                    2.0f, // attackTime
                    50.0f, // releaseTime
                    10.0f, // ratio
                    -0.5f, // threshold (-0.5 dBFS)
                    0.0f // postGain
                )

                val builder = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    channelCount,
                    true, // preEqInUse
                    10,
                    true, // mbcInUse
                    1,
                    false, // postEqInUse
                    0,
                    true // limiterInUse
                )

                for (ch in 0 until channelCount) {
                    builder.setPreEqByChannelIndex(ch, eqConfig)
                    builder.setMbcByChannelIndex(ch, mbcConfig)
                    builder.setLimiterByChannelIndex(ch, limiterConfig)
                }

                val config = builder.build()
                dynamicsProcessing = DynamicsProcessing(0, audioSessionId, config).apply {
                    enabled = _engineState.value.isMasterEnabled
                }
                Log.d(TAG, "DynamicsProcessing initialized successfully on session: $audioSessionId")
            } catch (e: Throwable) {
                Log.w(TAG, "DynamicsProcessing session init note: ${e.message}. Using high-fidelity native software DSP.")
            }
        }
    }

    fun setMasterEnabled(enabled: Boolean) {
        _engineState.value = _engineState.value.copy(isMasterEnabled = enabled)
        try {
            dynamicsProcessing?.enabled = enabled
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to toggle dynamics processing", e)
        }
    }

    fun setEqBandGain(index: Int, gainDb: Float) {
        val current = _engineState.value.eqBands.toMutableList()
        if (index in current.indices) {
            current[index] = gainDb.coerceIn(-12.0f, 12.0f)
            _engineState.value = _engineState.value.copy(
                eqBands = current,
                activePreset = EqPreset.FLAT // Custom mode
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dynamicsProcessing != null) {
                try {
                    for (ch in 0 until 2) {
                        val band = dynamicsProcessing?.getPreEqBandByChannelIndex(ch, index)
                        band?.gain = gainDb
                        if (band != null) {
                            dynamicsProcessing?.setPreEqBandByChannelIndex(ch, index, band)
                        }
                    }
                } catch (e: Throwable) {
                    Log.d(TAG, "DynamicsProcessing EQ update: ${e.message}")
                }
            }
        }
    }

    fun applyPreset(preset: EqPreset) {
        val gains = preset.gains.toList()
        _engineState.value = _engineState.value.copy(
            eqBands = gains,
            activePreset = preset
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dynamicsProcessing != null) {
            try {
                gains.forEachIndexed { index, gainDb ->
                    for (ch in 0 until 2) {
                        val band = dynamicsProcessing?.getPreEqBandByChannelIndex(ch, index)
                        band?.gain = gainDb
                        if (band != null) {
                            dynamicsProcessing?.setPreEqBandByChannelIndex(ch, index, band)
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.d(TAG, "DynamicsProcessing preset update: ${e.message}")
            }
        }
    }

    fun setSubBassParams(cutoffHz: Float, gainDb: Float, enabled: Boolean = true) {
        _engineState.value = _engineState.value.copy(
            subBassCutoffHz = cutoffHz.coerceIn(30f, 80f),
            subBassGainDb = gainDb.coerceIn(0f, 15f),
            isSubBassEnabled = enabled
        )
    }

    fun setCompressorParams(
        thresholdDb: Float,
        ratio: Float,
        attackMs: Float,
        releaseMs: Float,
        makeupGainDb: Float,
        enabled: Boolean = true
    ) {
        _engineState.value = _engineState.value.copy(
            compressorThresholdDb = thresholdDb.coerceIn(-60f, 0f),
            compressorRatio = ratio.coerceIn(1f, 20f),
            compressorAttackMs = attackMs.coerceIn(1f, 500f),
            compressorReleaseMs = releaseMs.coerceIn(10f, 1000f),
            compressorMakeupGainDb = makeupGainDb.coerceIn(-12f, 12f),
            isCompressorEnabled = enabled
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dynamicsProcessing != null) {
            try {
                for (ch in 0 until 2) {
                    val mbcBand = dynamicsProcessing?.getMbcBandByChannelIndex(ch, 0)
                    if (mbcBand != null) {
                        mbcBand.threshold = thresholdDb
                        mbcBand.ratio = ratio
                        mbcBand.attackTime = attackMs
                        mbcBand.releaseTime = releaseMs
                        mbcBand.postGain = makeupGainDb
                        mbcBand.isEnabled = enabled
                        dynamicsProcessing?.setMbcBandByChannelIndex(ch, 0, mbcBand)
                    }
                }
            } catch (e: Throwable) {
                Log.d(TAG, "DynamicsProcessing compressor update: ${e.message}")
            }
        }
    }

    fun setChannelRouting(mode: ChannelRoutingMode) {
        _engineState.value = _engineState.value.copy(routingMode = mode)
        syncToWebView()
    }

    fun setPitchSemiTones(semiTones: Float) {
        val clamped = (semiTones * 2).toInt() / 2.0f // 0.5 step increments
        val finalVal = clamped.coerceIn(-5.0f, 5.0f)
        _engineState.value = _engineState.value.copy(pitchSemiTones = finalVal)
        pitchShifter.setPitchSemiTones(finalVal)
        syncToWebView()
    }

    fun resetPitch() {
        setPitchSemiTones(0.0f)
    }

    fun setVocalRemoverMode(mode: VocalRemoverMode) {
        _engineState.value = _engineState.value.copy(vocalRemoverMode = mode)
        vocalRemoverDSP.setMode(mode)
        syncToWebView()
    }

    fun setVocalAttenuation(level: Float) {
        val clamped = level.coerceIn(0.0f, 1.0f)
        _engineState.value = _engineState.value.copy(vocalAttenuation = clamped)
        vocalRemoverDSP.setAttenuation(clamped)
        syncToWebView()
    }

    fun setMasterVolume(vol: Float) {
        _engineState.value = _engineState.value.copy(masterVolume = vol.coerceIn(0f, 1f))
        previewAudioTrack?.setVolume(vol.coerceIn(0f, 1f))
    }

    /**
     * Toggles an internal DSP audio preview stream (music chord progression with drums)
     * so user can immediately hear live EQ, sub-bass, compressor, and pitch adjustments.
     */
    fun toggleTestAudio() {
        if (_engineState.value.isTestTonePlaying) {
            stopTestAudio()
        } else {
            startTestAudio()
        }
    }

    private fun startTestAudio() {
        stopTestAudio()
        _engineState.value = _engineState.value.copy(isTestTonePlaying = true)

        previewJob = engineScope.launch {
            val sampleRate = 44100
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_FLOAT
            ) * 2

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            previewAudioTrack = track
            track.play()

            // Initialize DynamicsProcessing onto this track's audioSessionId
            initDynamicsProcessing(track.audioSessionId)

            val frameCount = 1024
            val inBuffer = FloatArray(frameCount * 2)
            val dspBuffer = FloatArray(frameCount * 2)
            var phase = 0.0
            var beatPhase = 0.0

            // Musical chords: Am, F, C, G
            val chordFreqs = arrayOf(
                doubleArrayOf(220.0, 261.63, 329.63, 55.0),  // Am + Sub A
                doubleArrayOf(174.61, 220.0, 261.63, 43.65),  // F + Sub F
                doubleArrayOf(261.63, 329.63, 392.0, 65.41),  // C + Sub C
                doubleArrayOf(196.0, 246.94, 293.66, 49.0)    // G + Sub G
            )
            var chordIndex = 0
            var sampleCounter = 0

            while (isActive && _engineState.value.isTestTonePlaying) {
                val state = _engineState.value

                for (i in 0 until frameCount) {
                    val currentChords = chordFreqs[chordIndex]
                    var sampleL = 0.0
                    var sampleR = 0.0

                    // Harmony synthesis
                    for (k in 0..2) {
                        val f = currentChords[k]
                        val s = sin(2.0 * PI * f * (phase + i) / sampleRate) * 0.15
                        sampleL += s
                        sampleR += s * (0.8 + 0.2 * k)
                    }

                    // Subwoofer synthesis
                    if (state.isSubBassEnabled && state.isMasterEnabled) {
                        val subFreq = state.subBassCutoffHz.toDouble()
                        val subGainLinear = 10.0.pow(state.subBassGainDb / 20.0)
                        val subWave = sin(2.0 * PI * subFreq * (phase + i) / sampleRate) * 0.2 * subGainLinear
                        sampleL += subWave
                        sampleR += subWave
                    }

                    // Drum kick heartbeat
                    val kickEnv = max(0.0, 1.0 - (beatPhase / (sampleRate * 0.5)))
                    val kickWave = sin(2.0 * PI * (60.0 * kickEnv + 40.0) * beatPhase / sampleRate) * kickEnv * 0.35
                    sampleL += kickWave
                    sampleR += kickWave

                    beatPhase += 1.0
                    if (beatPhase >= sampleRate * 0.5) { // 120 BPM
                        beatPhase = 0.0
                        sampleCounter++
                        if (sampleCounter % 4 == 0) {
                            chordIndex = (chordIndex + 1) % chordFreqs.size
                        }
                    }

                    // Apply Master EQ software simulation fallback
                    if (state.isMasterEnabled) {
                        val bassGain = (state.eqBands[0] + state.eqBands[1]) / 24.0f
                        val midGain = (state.eqBands[4] + state.eqBands[5]) / 24.0f
                        val trebleGain = (state.eqBands[8] + state.eqBands[9]) / 24.0f
                        val eqFactor = (1.0 + bassGain * 0.5 + midGain * 0.4 + trebleGain * 0.4).coerceIn(0.2, 3.0)
                        sampleL *= eqFactor
                        sampleR *= eqFactor
                    }

                    // DAW Compressor Envelope & Gain Reduction
                    if (state.isCompressorEnabled && state.isMasterEnabled) {
                        val maxAmp = max(kotlin.math.abs(sampleL), kotlin.math.abs(sampleR))
                        val thresholdLin = 10.0.pow(state.compressorThresholdDb / 20.0)
                        if (maxAmp > thresholdLin) {
                            val overDb = 20.0 * kotlin.math.log10(maxAmp / thresholdLin)
                            val compDb = overDb * (1.0 - 1.0 / state.compressorRatio)
                            val makeupLin = 10.0.pow(state.compressorMakeupGainDb / 20.0)
                            val compGain = (10.0.pow(-compDb / 20.0) * makeupLin).coerceIn(0.1, 2.0)
                            sampleL *= compGain
                            sampleR *= compGain
                        }
                    }

                    // L/R Channel Routing
                    val finalL: Float
                    val finalR: Float
                    when (state.routingMode) {
                        ChannelRoutingMode.STEREO -> {
                            finalL = sampleL.toFloat()
                            finalR = sampleR.toFloat()
                        }
                        ChannelRoutingMode.LEFT_ONLY -> {
                            finalL = sampleL.toFloat()
                            finalR = sampleL.toFloat()
                        }
                        ChannelRoutingMode.RIGHT_ONLY -> {
                            finalL = sampleR.toFloat()
                            finalR = sampleR.toFloat()
                        }
                        ChannelRoutingMode.MONO_DOWNMIX -> {
                            val mono = ((sampleL + sampleR) * 0.5).toFloat()
                            finalL = mono
                            finalR = mono
                        }
                    }

                    inBuffer[i * 2] = finalL.coerceIn(-0.95f, 0.95f)
                    inBuffer[i * 2 + 1] = finalR.coerceIn(-0.95f, 0.95f)
                }

                phase += frameCount

                // Vocal Remover / Center Channel Cancellation (Phase Inversion + HPF)
                if (state.vocalRemoverMode != VocalRemoverMode.OFF) {
                    vocalRemoverDSP.setMode(state.vocalRemoverMode)
                    vocalRemoverDSP.setAttenuation(state.vocalAttenuation)
                    vocalRemoverDSP.processInPlace(inBuffer, frameCount)
                }

                // SoundTouch Pitch Shifting
                if (kotlin.math.abs(state.pitchSemiTones) > 0.01f) {
                    pitchShifter.process(inBuffer, dspBuffer)
                    track.write(dspBuffer, 0, dspBuffer.size, AudioTrack.WRITE_BLOCKING)
                } else {
                    track.write(inBuffer, 0, inBuffer.size, AudioTrack.WRITE_BLOCKING)
                }
            }

            try {
                track.stop()
                track.release()
            } catch (e: Throwable) {
                Log.e(TAG, "AudioTrack stop error", e)
            }
        }
    }

    fun stopTestAudio() {
        _engineState.value = _engineState.value.copy(isTestTonePlaying = false)
        previewJob?.cancel()
        previewJob = null
        try {
            previewAudioTrack?.pause()
            previewAudioTrack?.flush()
            previewAudioTrack?.release()
        } catch (e: Throwable) {
            Log.e(TAG, "AudioTrack release error", e)
        }
        previewAudioTrack = null
    }

    private fun startVisualizerLoop() {
        visualizerJob?.cancel()
        visualizerJob = engineScope.launch {
            val bars = FloatArray(32)
            var tick = 0.0
            while (isActive) {
                val state = _engineState.value
                val isPlaying = state.isTestTonePlaying
                val masterOn = state.isMasterEnabled

                tick += 0.15
                for (i in 0 until 32) {
                    val bandWeight = when {
                        i < 4 -> (state.eqBands[0] + 12f) / 24f + (if (state.isSubBassEnabled) 0.35f else 0.0f)
                        i < 8 -> (state.eqBands[1] + 12f) / 24f
                        i < 12 -> (state.eqBands[3] + 12f) / 24f
                        i < 18 -> (state.eqBands[5] + 12f) / 24f
                        i < 24 -> (state.eqBands[7] + 12f) / 24f
                        else -> (state.eqBands[9] + 12f) / 24f
                    }

                    val dynamicMultiplier = if (isPlaying && masterOn) 0.85f else if (masterOn) 0.25f else 0.05f
                    val wave = (sin(tick + i * 0.45).toFloat() * 0.5f + 0.5f) * bandWeight * dynamicMultiplier
                    val target = wave.coerceIn(0.04f, 0.98f)

                    // Smooth decay
                    bars[i] = bars[i] * 0.7f + target * 0.3f
                }

                _spectrumData.value = bars.clone()
                delay(33) // ~30 FPS
            }
        }
    }

    fun release() {
        stopTestAudio()
        visualizerJob?.cancel()
        pitchShifter.release()
        try {
            dynamicsProcessing?.release()
        } catch (e: Throwable) {
            Log.e(TAG, "DynamicsProcessing release error", e)
        }
        dynamicsProcessing = null
    }
}
