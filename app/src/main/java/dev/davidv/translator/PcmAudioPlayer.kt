package dev.davidv.translator

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val SPEECH_ATTRIBUTES: AudioAttributes =
  AudioAttributes
    .Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

class PcmAudioPlayer(
  audioManager: AudioManager,
  private val onPlaybackStateChanged: (Boolean) -> Unit = {},
) {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val audioFocus = SpeechAudioFocus.create(audioManager, mainHandler, onLost = ::stop)
  private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var audioTrack: AudioTrack? = null
  private var playbackJob: Job? = null

  fun play(audio: PcmAudio) {
    play(flowOf(audio))
  }

  fun play(
    audioChunks: Flow<PcmAudio>,
    onError: (String) -> Unit = {},
  ) {
    stop()

    playbackJob =
      playbackScope.launch {
        var track: AudioTrack? = null
        var sampleRate: Int? = null
        var totalFrames = 0

        try {
          var chunkIndex = 0
          audioChunks.buffer(capacity = 2).collect { audio ->
            ensureActive()
            chunkIndex += 1

            val currentTrack =
              if (track == null) {
                check(audioFocus.acquire()) { "Audio focus request denied" }
                buildAudioTrack(audio).also { newTrack ->
                  track = newTrack
                  sampleRate = audio.sampleRate
                  audioTrack = newTrack
                  notifyPlaybackStateChanged(true)
                }
              } else {
                if (audio.sampleRate != sampleRate) {
                  throw IllegalStateException(
                    "Audio stream sample rate changed from $sampleRate to ${audio.sampleRate}",
                  )
                }
                track!!
              }

            val audioDurationMs = (audio.pcmSamples.size * 1000L) / audio.sampleRate
            Log.d(
              "PcmAudioPlayer",
              "Chunk $chunkIndex: write start samples=${audio.pcmSamples.size} sampleRate=${audio.sampleRate} audioMs=$audioDurationMs playing=${currentTrack.playState == AudioTrack.PLAYSTATE_PLAYING}",
            )
            val written =
              currentTrack.write(
                audio.pcmSamples,
                0,
                audio.pcmSamples.size,
                AudioTrack.WRITE_BLOCKING,
              )
            if (written != audio.pcmSamples.size) {
              val context = currentCoroutineContext()
              if (!context.isActive || audioTrack !== currentTrack) {
                throw CancellationException()
              }
              currentTrack.release()
              throw IllegalStateException("AudioTrack wrote $written of ${audio.pcmSamples.size} samples")
            }
            Log.d("PcmAudioPlayer", "Chunk $chunkIndex: write done written=$written")

            totalFrames += audio.pcmSamples.size
            if (currentTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
              Log.d("PcmAudioPlayer", "Chunk $chunkIndex: playback start")
              currentTrack.play()
            }
          }

          val finalTrack = track ?: return@launch
          waitForPlaybackDrain(finalTrack, totalFrames)
          finishPlayback(finalTrack)
        } catch (_: CancellationException) {
          Unit
        } catch (error: RuntimeException) {
          if (audioTrack === track) {
            audioTrack = null
            audioFocus.release()
            notifyPlaybackStateChanged(false)
          }
          playbackJob = null
          mainHandler.post {
            onError(error.message ?: "Audio playback failed")
          }
          cleanupTrack(track)
        }
      }
  }

  fun stop() {
    playbackJob?.cancel()
    playbackJob = null
    audioFocus.release()

    val track = audioTrack ?: return
    audioTrack = null
    runCatching {
      if (track.playState != AudioTrack.PLAYSTATE_STOPPED) {
        track.stop()
      }
    }
    track.release()
    notifyPlaybackStateChanged(false)
  }

  fun release() {
    stop()
    playbackScope.cancel()
  }

  private fun buildAudioTrack(audio: PcmAudio): AudioTrack {
    val minBufferSize =
      AudioTrack.getMinBufferSize(
        audio.sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      )
    if (minBufferSize <= 0) {
      throw IllegalStateException("Invalid AudioTrack buffer size: $minBufferSize")
    }

    return AudioTrack
      .Builder()
      .setAudioAttributes(SPEECH_ATTRIBUTES)
      .setAudioFormat(
        AudioFormat
          .Builder()
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .setSampleRate(audio.sampleRate)
          .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
          .build(),
      ).setTransferMode(AudioTrack.MODE_STREAM)
      .setBufferSizeInBytes(maxOf(minBufferSize, audio.pcmSamples.size * Short.SIZE_BYTES))
      .build()
  }

  private suspend fun waitForPlaybackDrain(
    track: AudioTrack,
    totalFrames: Int,
  ) {
    while (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
      currentCoroutineContext().ensureActive()
      if (track.playbackHeadPosition >= totalFrames) {
        break
      }
      delay(20)
    }
  }

  private fun finishPlayback(track: AudioTrack) {
    if (audioTrack === track) {
      audioTrack = null
      cleanupTrack(track)
      audioFocus.release()
      notifyPlaybackStateChanged(false)
    } else {
      cleanupTrack(track)
    }
    playbackJob = null
  }

  private fun cleanupTrack(track: AudioTrack?) {
    if (track == null) return
    runCatching {
      if (track.playState != AudioTrack.PLAYSTATE_STOPPED) {
        track.stop()
      }
    }
    track.release()
  }

  private fun notifyPlaybackStateChanged(playing: Boolean) {
    mainHandler.post {
      onPlaybackStateChanged(playing)
    }
  }
}

// Transient may-duck focus: music lowers under an utterance and comes back once it ends. A
// transient-can-duck loss keeps speaking, since the system ducks this player on its own.
private sealed interface SpeechAudioFocus {
  fun acquire(): Boolean

  fun release()

  companion object {
    fun create(
      audioManager: AudioManager,
      handler: Handler,
      onLost: () -> Unit,
    ): SpeechAudioFocus {
      val listener =
        AudioManager.OnAudioFocusChangeListener { change ->
          if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            onLost()
          }
        }
      return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Request(audioManager, handler, listener)
      } else {
        Legacy(audioManager, listener)
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.O)
  class Request(
    private val audioManager: AudioManager,
    handler: Handler,
    listener: AudioManager.OnAudioFocusChangeListener,
  ) : SpeechAudioFocus {
    private val request =
      AudioFocusRequest
        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(SPEECH_ATTRIBUTES)
        .setOnAudioFocusChangeListener(listener, handler)
        .build()

    override fun acquire(): Boolean = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    override fun release() {
      audioManager.abandonAudioFocusRequest(request)
    }
  }

  @Suppress("DEPRECATION")
  class Legacy(
    private val audioManager: AudioManager,
    private val listener: AudioManager.OnAudioFocusChangeListener,
  ) : SpeechAudioFocus {
    override fun acquire(): Boolean =
      audioManager.requestAudioFocus(
        listener,
        AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
      ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    override fun release() {
      audioManager.abandonAudioFocus(listener)
    }
  }
}
