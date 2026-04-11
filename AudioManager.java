/**
 * Manages all audio playback for Lumen Architect, providing a simple interface for
 * triggering sound effects, controlling background music tracks, and adjusting the
 * master volume level at runtime. It abstracts over the underlying Java Sound API so
 * no other class needs to interact directly with {@link javax.sound.sampled} types,
 * keeping audio concerns isolated in this single class.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

public class AudioManager {

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    /** The single shared instance. */
    private static final AudioManager INSTANCE = new AudioManager();

    /**
     * Returns the singleton {@code AudioManager} instance.
     *
     * @return the shared audio manager; never {@code null}
     */
    public static AudioManager getInstance() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * The current master volume level in the range [0.0, 1.0]. Applied as a linear
     * gain multiplier to both SFX and BGM playback channels.
     */
    private float masterVolume;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs an {@code AudioManager} with master volume set to full (1.0). No
     * audio resources are loaded at construction time; sounds are loaded on demand
     * when first played.
     */
    public AudioManager() {
        this.masterVolume = 1.0f;
    }

    // -------------------------------------------------------------------------
    // Sound effects
    // -------------------------------------------------------------------------

    /**
     * Loads and plays the sound effect at the given resource filename in a fire-and-
     * forget manner. The clip is played once at the current {@link #masterVolume} level
     * and automatically released when playback finishes. Multiple SFX clips can play
     * simultaneously. This stub will be fully implemented in a later phase when audio
     * assets are available.
     *
     * @param filename the resource-relative filename of the sound effect to play
     *                 (e.g., {@code "sfx_jump.wav"}); must not be {@code null}
     */
    public void playSFX(String filename) {
        // Stub — clip loading and one-shot playback to be implemented in a later phase.
    }

    // -------------------------------------------------------------------------
    // Background music
    // -------------------------------------------------------------------------

    /**
     * Stops any currently playing background music track and begins looping the audio
     * file at the given resource filename. Volume is applied from {@link #masterVolume}
     * at the start of playback. This stub will be fully implemented in a later phase.
     *
     * @param filename the resource-relative filename of the BGM track to play
     *                 (e.g., {@code "bgm_act1.wav"}); must not be {@code null}
     */
    public void playBGM(String filename) {
        // Stub — BGM loading, looping, and volume application to be implemented later.
    }

    /**
     * Stops the currently playing background music track and releases its audio
     * resources. Safe to call even when no BGM is playing. This stub will be fully
     * implemented in a later phase.
     */
    public void stopBGM() {
        // Stub — BGM stop and resource release to be implemented in a later phase.
    }

    // -------------------------------------------------------------------------
    // Looping sound effects
    // -------------------------------------------------------------------------

    /**
     * Starts looping the sound effect at the given resource filename. Unlike
     * {@link #playSFX(String)}, this clip loops continuously until
     * {@link #stopLoopSFX()} is called. Only one looping SFX clip can be active
     * at a time; calling this while a loop is already playing will stop the
     * previous loop first. Used by {@link entities.DarkCrawler} for state-dependent
     * ambient audio.
     *
     * @param filename the resource-relative filename of the sound effect to loop
     *                 (e.g., {@code "sfx_crawler_tracking.wav"}); must not be {@code null}
     */
    public void loopSFX(String filename) {
        // Stub — looping SFX playback to be implemented in a later phase.
    }

    /**
     * Stops the currently looping sound effect started by {@link #loopSFX(String)}
     * and releases its audio resources. Safe to call even when no loop is playing.
     */
    public void stopLoopSFX() {
        // Stub — looping SFX stop to be implemented in a later phase.
    }

    // -------------------------------------------------------------------------
    // Volume control
    // -------------------------------------------------------------------------

    /**
     * Sets the master volume applied to all audio output. The value is clamped to the
     * range [0.0, 1.0]; values outside this range are silently clamped rather than
     * throwing an exception.
     *
     * @param volume the desired master volume level; 0.0 is silent, 1.0 is full volume
     */
    public void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0.0f, Math.min(1.0f, volume));
        // Stub — propagation of new volume to active clips to be implemented later.
    }

    // -------------------------------------------------------------------------
    // Accessor
    // -------------------------------------------------------------------------

    /**
     * Returns the current master volume level.
     *
     * @return the master volume in the range [0.0, 1.0]
     */
    public float getMasterVolume() {
        return masterVolume;
    }
}
