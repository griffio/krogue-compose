package griffio.krogue.game

/**
 * A short sound effect, one per spell kind. The mapping from a [SpellKind] (and
 * therefore from a [GameEvent.Bolt]) to one of these lives in [GameAudio]; the
 * platform engine only knows how to play a named clip.
 */
enum class Sfx { FIRE_BOLT, ENERGY_BLAST, VENOM, DOOR, COIN, HIT, HURT, SPLASH, TRAP, DESCEND }

/** A looping background theme. Swapped between games so restarts feel fresh. */
enum class MusicTrack { THEME_ONE, THEME_TWO }

/**
 * The platform's audio back-end: load and play clips. Created via the
 * [createAudioEngine] `expect` so the game core stays free of platform APIs —
 * the desktop build drives `javax.sound.sampled`, the web build is a silent
 * no-op. Implementations must be crash-proof: a missing or unreadable clip
 * should be swallowed, never thrown.
 */
interface AudioEngine {
    fun play(sfx: Sfx)
    fun playMusic(track: MusicTrack)
    fun stopMusic()

    /** When true, [play]/[playMusic] are silent and any music is stopped. */
    var muted: Boolean

    /** Release lines/threads held by the engine (called on dispose). */
    fun release()
}

expect fun createAudioEngine(): AudioEngine

/**
 * The audio layer that sits beside [EffectsState], fed by the same semantic
 * [GameEvent] stream: a spell bolt becomes a sound effect. Music is steered
 * separately by the UI (launch, new game, mute) since it isn't a world event.
 * The game core stays unaware that any of this exists.
 */
class GameAudio(private val engine: AudioEngine) {

    private var track = MusicTrack.THEME_ONE

    /** Turn a world event into sound; events with no mapped sound pass silently. */
    fun emit(event: GameEvent) {
        when (event) {
            is GameEvent.Bolt -> engine.play(event.kind.toSfx())
            is GameEvent.Step -> event.terrain.toSfx()?.let(engine::play)
            is GameEvent.Gold -> engine.play(Sfx.COIN)
            is GameEvent.Hit -> engine.play(if (event.target == HitTarget.HERO) Sfx.HURT else Sfx.HIT)
            is GameEvent.Charge -> {} // the venom bolt that follows carries the sound
        }
    }

    /** Begin looping the current theme (called once at launch). */
    fun startMusic() = engine.playMusic(track)

    /** Swap to the other theme and loop it — used when a new game starts. */
    fun nextTrack() {
        track = if (track == MusicTrack.THEME_ONE) MusicTrack.THEME_TWO else MusicTrack.THEME_ONE
        engine.playMusic(track)
    }

    /** Flip mute (muting stops the music). Returns the new muted state. */
    fun toggleMute(): Boolean {
        val muted = !engine.muted
        engine.muted = muted
        if (!muted) engine.playMusic(track)
        return muted
    }

    fun release() = engine.release()

    private fun SpellKind.toSfx(): Sfx = when (this) {
        SpellKind.FIRE -> Sfx.FIRE_BOLT
        SpellKind.ENERGY -> Sfx.ENERGY_BLAST
        SpellKind.VENOM -> Sfx.VENOM
    }

    /** Terrain that makes a noise when stepped onto; null tiles are silent. */
    private fun Terrain.toSfx(): Sfx? = when (this) {
        Terrain.DOOR -> Sfx.DOOR
        Terrain.WATER -> Sfx.SPLASH
        Terrain.TRAP -> Sfx.TRAP
        Terrain.STAIRS -> Sfx.DESCEND
        else -> null
    }
}
