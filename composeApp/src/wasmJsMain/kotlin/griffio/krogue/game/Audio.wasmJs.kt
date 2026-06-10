package griffio.krogue.game

/**
 * Web build ships silent for now (see milestone notes): browser autoplay
 * policies and shipping multi-megabyte WAVs to the browser are deferred. This
 * no-op keeps the wasmJs target compiling against the shared [AudioEngine].
 */
private object SilentAudioEngine : AudioEngine {
    override var muted: Boolean = true
    override fun play(sfx: Sfx) {}
    override fun playMusic(track: MusicTrack) {}
    override fun stopMusic() {}
    override fun release() {}
}

actual fun createAudioEngine(): AudioEngine = SilentAudioEngine
