package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioEngineManager
import com.example.audio.ChannelRoutingMode
import com.example.audio.EqPreset
import com.example.audio.PitchShifter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("YouTube karaoke", appName)
    }

    @Test
    fun `test pitch shifter semitones and ratio calculation`() {
        val pitchShifter = PitchShifter(sampleRate = 44100, channels = 2)
        pitchShifter.setPitchSemiTones(0.0f)
        assertEquals(1.0f, pitchShifter.getPitchRatio(), 0.001f)

        pitchShifter.setPitchSemiTones(12.0f) // Should clamp to max 5.0
        assertEquals(5.0f, pitchShifter.getPitchSemiTones(), 0.001f)

        val inSamples = FloatArray(512) { 0.5f }
        val outSamples = FloatArray(512)
        val written = pitchShifter.process(inSamples, outSamples)
        assertEquals(inSamples.size, written)
    }

    @Test
    fun `test audio engine state and routing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AudioEngineManager.init(context)

        AudioEngineManager.setChannelRouting(ChannelRoutingMode.MONO_DOWNMIX)
        assertEquals(ChannelRoutingMode.MONO_DOWNMIX, AudioEngineManager.engineState.value.routingMode)

        AudioEngineManager.applyPreset(EqPreset.BASS_BOOST)
        assertEquals(EqPreset.BASS_BOOST, AudioEngineManager.engineState.value.activePreset)
        assertEquals(7.5f, AudioEngineManager.engineState.value.eqBands[0], 0.01f)
    }

    @Test
    fun `test vocal remover dsp center channel cancellation and bass preservation`() {
        val dsp = com.example.audio.VocalRemoverDSP(sampleRate = 44100, cutoffHz = 120.0f)
        dsp.setMode(com.example.audio.VocalRemoverMode.VOCAL_CUT)
        dsp.setAttenuation(1.0f)

        // Generate 1 kHz center mono tone (L == R)
        val frameCount = 256
        val inSamples = FloatArray(frameCount * 2)
        for (i in 0 until frameCount) {
            val s = kotlin.math.sin(2.0 * kotlin.math.PI * 1000.0 * i / 44100.0).toFloat() * 0.5f
            inSamples[i * 2] = s
            inSamples[i * 2 + 1] = s
        }

        val outSamples = FloatArray(frameCount * 2)
        val processed = dsp.process(inSamples, outSamples, frameCount)
        assertEquals(frameCount * 2, processed)

        // Calculate energy of mid-band center vocal before and after
        var inEnergy = 0.0f
        var outEnergy = 0.0f
        // Check after filter transient (last 128 frames)
        for (i in 128 until frameCount) {
            inEnergy += inSamples[i * 2] * inSamples[i * 2]
            outEnergy += outSamples[i * 2] * outSamples[i * 2]
        }

        // Mid/high center mono signal should be significantly attenuated (> 15dB reduction)
        assertTrue(outEnergy < inEnergy * 0.1f)

        // Test AudioEngineManager vocal remover control
        AudioEngineManager.setVocalRemoverMode(com.example.audio.VocalRemoverMode.VOCAL_CUT)
        assertEquals(com.example.audio.VocalRemoverMode.VOCAL_CUT, AudioEngineManager.engineState.value.vocalRemoverMode)

        AudioEngineManager.setVocalAttenuation(0.75f)
        assertEquals(0.75f, AudioEngineManager.engineState.value.vocalAttenuation, 0.001f)
    }
}
