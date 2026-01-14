package com.photobooth.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin

object ToneGenerator {
    private const val SAMPLE_RATE = 44100
    private const val DURATION_MS = 200 // Short beep

    fun playBeep(frequencyHz: Int) {
        Thread {
            val numSamples = (DURATION_MS * SAMPLE_RATE / 1000)
            val samples = ShortArray(numSamples)
            val buffer = ShortArray(numSamples)

            // Generate tone
            for (i in 0 until numSamples) {
                samples[i] = (sin(2.0 * Math.PI * i.toDouble() / (SAMPLE_RATE / frequencyHz)) * Short.MAX_VALUE).toInt().toShort()
            }

            // Play tone
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(numSamples * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(samples, 0, numSamples)
            audioTrack.play()

            // Wait for playback to finish
            Thread.sleep(DURATION_MS.toLong())
            audioTrack.release()
        }.start()
    }
}
