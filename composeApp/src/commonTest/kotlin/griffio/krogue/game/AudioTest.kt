package griffio.krogue.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Records calls so the pure [GameAudio] mapping can be asserted without sound. */
private class FakeAudioEngine : AudioEngine {
    val played = mutableListOf<Sfx>()
    val music = mutableListOf<MusicTrack>()
    var stops = 0
    override var muted: Boolean = false
    override fun play(sfx: Sfx) { played.add(sfx) }
    override fun playMusic(track: MusicTrack) { music.add(track) }
    override fun stopMusic() { stops++ }
    override fun release() {}
}

class AudioTest {

    @Test
    fun boltEventsMapToTheirSpellSoundEffect() {
        val engine = FakeAudioEngine()
        val audio = GameAudio(engine)

        audio.emit(GameEvent.Bolt(0, 0, 1, 0, SpellKind.FIRE, 0))
        audio.emit(GameEvent.Bolt(0, 0, 1, 0, SpellKind.ENERGY, 2))
        audio.emit(GameEvent.Bolt(0, 0, 1, 0, SpellKind.VENOM, 0))

        assertEquals(listOf(Sfx.FIRE_BOLT, Sfx.ENERGY_BLAST, Sfx.VENOM), engine.played)
    }

    @Test
    fun steppingThroughADoorSmashesIt() {
        val engine = FakeAudioEngine()
        val audio = GameAudio(engine)

        audio.emit(GameEvent.Step(5, 5, Terrain.DOOR))

        assertEquals(listOf(Sfx.DOOR), engine.played)
    }

    @Test
    fun noisyTerrainEachMapsToItsSound() {
        val engine = FakeAudioEngine()
        val audio = GameAudio(engine)

        audio.emit(GameEvent.Step(5, 5, Terrain.WATER))
        audio.emit(GameEvent.Step(5, 5, Terrain.TRAP))
        audio.emit(GameEvent.Step(5, 5, Terrain.STAIRS))

        assertEquals(listOf(Sfx.SPLASH, Sfx.TRAP, Sfx.DESCEND), engine.played)
    }

    @Test
    fun steppingOnSilentTerrainMakesNoSound() {
        val engine = FakeAudioEngine()
        val audio = GameAudio(engine)

        audio.emit(GameEvent.Step(5, 5, Terrain.FLOOR))
        audio.emit(GameEvent.Step(5, 5, Terrain.TREASURE))

        assertTrue(engine.played.isEmpty(), "plain floor and treasure tiles are silent to step on")
    }

    @Test
    fun goldChimesAndHitsSoundByWhoIsStruck() {
        val engine = FakeAudioEngine()
        val audio = GameAudio(engine)

        audio.emit(GameEvent.Gold(2, 2, 1))
        audio.emit(GameEvent.Hit(1, 1, 4, HitTarget.HERO))
        audio.emit(GameEvent.Hit(3, 3, 6, HitTarget.MONSTER))

        assertEquals(listOf(Sfx.COIN, Sfx.HURT, Sfx.HIT), engine.played)
    }

    @Test
    fun chargeWindupIsSilent() {
        val engine = FakeAudioEngine()
        val audio = GameAudio(engine)

        audio.emit(GameEvent.Charge(3, 3, SpellKind.VENOM))

        assertTrue(engine.played.isEmpty(), "the windup is silent; its bolt carries the sound")
    }

    @Test
    fun musicStartsOnThemeOneAndAlternatesEachNewGame() {
        val engine = FakeAudioEngine()
        val audio = GameAudio(engine)

        audio.startMusic()
        audio.nextTrack()
        audio.nextTrack()

        assertEquals(
            listOf(MusicTrack.THEME_ONE, MusicTrack.THEME_TWO, MusicTrack.THEME_ONE),
            engine.music,
        )
    }

    @Test
    fun toggleMuteFlipsAndPropagatesToTheEngine() {
        val engine = FakeAudioEngine()
        val audio = GameAudio(engine)

        assertTrue(audio.toggleMute(), "first toggle mutes")
        assertTrue(engine.muted)
        assertFalse(audio.toggleMute(), "second toggle unmutes")
        assertFalse(engine.muted)
        // Unmuting resumes the (current) theme.
        assertEquals(listOf(MusicTrack.THEME_ONE), engine.music)
    }
}
