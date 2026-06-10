package griffio.krogue.game

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent

/**
 * Desktop audio via `javax.sound.sampled`. SFX clips are short and play
 * overlapping (a fresh [Clip] per shot, closed when it stops); music holds one
 * looping clip that's swapped on demand. Every operation is guarded — a missing
 * or unsupported WAV degrades to silence rather than crashing the game.
 */
private class JvmAudioEngine : AudioEngine {

    override var muted: Boolean = false
        set(value) {
            field = value
            if (value) stopMusic()
        }

    private var musicClip: Clip? = null

    // WAV bytes, read once from the classpath and reused for every playback.
    private val cache = HashMap<String, ByteArray?>()

    override fun play(sfx: Sfx) {
        if (muted) return
        val bytes = bytesFor(sfxFile(sfx)) ?: return
        runCatching {
            val clip = openClip(bytes)
            clip.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP) clip.close()
            }
            clip.start()
        }
    }

    override fun playMusic(track: MusicTrack) {
        if (muted) return
        stopMusic()
        val bytes = bytesFor(musicFile(track)) ?: return
        runCatching {
            val clip = openClip(bytes)
            clip.loop(Clip.LOOP_CONTINUOUSLY)
            musicClip = clip
        }
    }

    override fun stopMusic() {
        musicClip?.let { runCatching { it.stop(); it.close() } }
        musicClip = null
    }

    override fun release() = stopMusic()

    private fun openClip(bytes: ByteArray): Clip {
        // getAudioInputStream needs mark/reset, hence the BufferedInputStream.
        val stream = AudioSystem.getAudioInputStream(BufferedInputStream(ByteArrayInputStream(bytes)))
        return AudioSystem.getClip().apply { open(stream) }
    }

    private fun bytesFor(resource: String): ByteArray? = cache.getOrPut(resource) {
        runCatching {
            javaClass.getResourceAsStream(resource)?.use { it.readBytes() }
        }.getOrNull().also {
            if (it == null) System.err.println("krogue audio: missing or unreadable $resource")
        }
    }

    private fun sfxFile(sfx: Sfx): String = when (sfx) {
        Sfx.FIRE_BOLT -> "/sounds/lightning-magic-strikewav.wav"
        Sfx.ENERGY_BLAST -> "/sounds/crackling_energy_1.wav"
        Sfx.VENOM -> "/sounds/dark-magic-smoke.wav"
        Sfx.DOOR -> "/sounds/wood-door-smash.wav"
        Sfx.COIN -> "/sounds/coin.wav"
        Sfx.HIT -> "/sounds/hit-thud.wav"
        Sfx.HURT -> "/sounds/hero-hurt.wav"
        Sfx.SPLASH -> "/sounds/water-splash.wav"
        Sfx.TRAP -> "/sounds/trap-snap.wav"
        Sfx.DESCEND -> "/sounds/stairs-descend.wav"
    }

    private fun musicFile(track: MusicTrack): String = when (track) {
        MusicTrack.THEME_ONE -> "/sounds/epic1.wav"
        MusicTrack.THEME_TWO -> "/sounds/epic2.wav"
    }
}

actual fun createAudioEngine(): AudioEngine = JvmAudioEngine()
