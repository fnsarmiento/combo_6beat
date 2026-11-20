package engine.audio;

import java.io.File;
import java.util.HashMap;
import kuusisto.tinysound.Sound;
import kuusisto.tinysound.TinySound;
import processing.core.PApplet;

/**
 * Controlador global de audio del motor.
 *
 * <p>Arquitectura de canales:
 * <ul>
 *   <li><b>Efectos de sonido (SFX):</b> reproducidos con baja latencia mediante {@link TinySound}
 *       y almacenados en caché en {@code _sfxCache}.</li>
 *   <li><b>BGM Persistente ({@code _persistentBGM}):</b> pista de fondo compartida entre el Menú
 *       Principal y el Hub. No se reinicia al cambiar entre salas; si un combate rítmico la interrumpe,
 *       guarda su posición en milisegundos ({@code _persistentBGMPosMs}) para reanudarse exactamente
 *       donde quedó al volver al Hub.</li>
 *   <li><b>BGM Transitoria ({@code _actualBGM != _persistentBGM}):</b> pista propia de un combate
 *       rítmico en {@code ROTNScene}. Al finalizar o salir de la canción, {@link #stopTransientBGM()}
 *       la detiene de inmediato y cierra el {@code Clip} en un hilo daemon para no bloquear el
 *       renderizado principal.</li>
 * </ul>
 */
public class Audio {

	// =========================================================================
	// Estado Estático y Caché de Sonidos
	// =========================================================================

	private static final HashMap<String, Sound> _sfxCache = new HashMap<>();
	private static MusicTrack _actualBGM;
	private static MusicTrack _persistentBGM;
	private static String _persistentBGMPath = "";
	private static int _persistentBGMPosMs = 0;

	private static float _BGMVolume = 1.0f;
	private static float _SFXVolume = 1.0f;
	private static boolean _isMuted = false;

	// =========================================================================
	// Inicialización y Control de Volumen / Mute
	// =========================================================================

	public static void init(PApplet app) {
		if (!TinySound.isInitialized()) {
			TinySound.init();
		}
		TinySound.setGlobalVolume(_SFXVolume);
	}

	public static float getBGMVolume() {
		return _isMuted ? 0.0f : _BGMVolume;
	}

	public static boolean isMuted() {
		return _isMuted;
	}

	public static void setBGMVolume(float volume) {
		_BGMVolume = PApplet.constrain(volume, 0.0f, 1.0f);
		syncTrackVolume(_actualBGM);
		if (_persistentBGM != null && _persistentBGM != _actualBGM) {
			syncTrackVolume(_persistentBGM);
		}
	}

	public static float getSFXVolume() {
		return _isMuted ? 0.0f : _SFXVolume;
	}

	public static void setSFXVolume(float volume) {
		_SFXVolume = PApplet.constrain(volume, 0.0f, 1.0f);
		TinySound.setGlobalVolume(_isMuted ? 0.0f : _SFXVolume);
	}

	public static void setMute(boolean mute) {
		_isMuted = mute;
		syncTrackVolume(_actualBGM);
		if (_persistentBGM != null && _persistentBGM != _actualBGM) {
			syncTrackVolume(_persistentBGM);
		}
		TinySound.setGlobalVolume(_isMuted ? 0.0f : _SFXVolume);
	}

	/** Aplica de inmediato el volumen y estado de silencio globales sobre una pista {@link MusicTrack}. */
	public static void syncTrackVolume(MusicTrack track) {
		if (track == null) return;
		track.setVolume(_BGMVolume);
		track.setMute(_isMuted);
	}

	public static void toggleMute() {
		setMute(!_isMuted);
	}

	// =========================================================================
	// Efectos de Sonido (SFX - TinySound)
	// =========================================================================

	public static Sound loadSFX(String path) {
		return _sfxCache.computeIfAbsent(path, k -> TinySound.loadSound(new File(k)));
	}

	public static void playSFX(Sound sfx) {
		if (sfx != null && !_isMuted) {
			sfx.play();
		}
	}

	// =========================================================================
	// Música de Fondo (BGM Transitoria y Persistente)
	// =========================================================================

	public static MusicTrack loadBGM(String path) {
		MusicTrack bgm = new MusicTrack();
		bgm.loadMusic(path);
		return bgm;
	}

	public static void playBGM(MusicTrack bgm) {
		playBGM(bgm, 0, true);
	}

	public static void playBGM(MusicTrack bgm, boolean loop) {
		playBGM(bgm, 0, loop);
	}

	/**
	 * Inicia la reproducción de una pista BGM. Si la pista anterior era la BGM persistente
	 * de menú/hub, la pausa conservando su posición; si era otra pista transitoria, la detiene.
	 */
	public static void playBGM(MusicTrack bgm, int startMs, boolean loop) {
		if (_actualBGM != null && _actualBGM != bgm) {
			if (_actualBGM == _persistentBGM) {
				pausePersistentBGM();
			} else {
				_actualBGM.stop();
			}
		}

		_actualBGM = bgm;
		if (_actualBGM == null) return;

		_actualBGM.setVolume(_BGMVolume);
		_actualBGM.setMute(_isMuted);
		_actualBGM.play(startMs, loop);
	}

	/**
	 * Reproduce o reanuda una pista de fondo persistente (Menú + Hub) sin reiniciarla
	 * al cambiar de escena, retomando exactamente donde quedó si fue pausada por un combate.
	 */
	public static void ensurePersistentBGM(String absWavPath) {
		if (absWavPath == null || absWavPath.isEmpty()) return;
		File f = new File(absWavPath);
		if (!f.exists()) return;

		if (_persistentBGM != null && absWavPath.equals(_persistentBGMPath)) {
			if (_actualBGM == _persistentBGM && _persistentBGM.isPlaying()) {
				return;
			}
			_actualBGM = _persistentBGM;
			_persistentBGM.setVolume(_BGMVolume);
			_persistentBGM.setMute(_isMuted);
			_persistentBGM.resume();
			if (!_persistentBGM.isPlaying()) {
				_persistentBGM.play(_persistentBGMPosMs, true);
			}
			return;
		}

		if (_persistentBGM != null) {
			_persistentBGM.close();
		}
		_persistentBGM = loadBGM(absWavPath);
		_persistentBGMPath = absWavPath;
		_persistentBGMPosMs = 0;
		_actualBGM = _persistentBGM;
		if (_persistentBGM != null) {
			_persistentBGM.setVolume(_BGMVolume);
			_persistentBGM.setMute(_isMuted);
			_persistentBGM.play(0, true);
		}
	}

	/**
	 * Pausa la BGM persistente guardando su posición actual en milisegundos.
	 */
	public static void pausePersistentBGM() {
		if (_persistentBGM != null && _persistentBGM.isPlaying()) {
			_persistentBGMPosMs = _persistentBGM.getPositionMs();
			_persistentBGM.pause();
		}
	}

	/**
	 * Detiene y libera en segundo plano la BGM transitoria de combate sin afectar a {@code _persistentBGM}.
	 */
	public static void stopTransientBGM() {
		if (_actualBGM != null && _actualBGM != _persistentBGM) {
			final MusicTrack toClose = _actualBGM;
			_actualBGM = null;
			toClose.stop();
			// Cerrar el Clip en segundo plano para no bloquear el hilo gráfico al salir de la canción
			Thread t = new Thread(() -> toClose.close(), "bgm-close");
			t.setDaemon(true);
			t.start();
		}
	}

	public static void pauseBGM() {
		if (_actualBGM != null) _actualBGM.pause();
	}

	public static void resumeBGM() {
		if (_actualBGM != null) _actualBGM.resume();
	}

	public static int getBGMPosMs() {
		return _actualBGM != null ? _actualBGM.getPositionMs() : 0;
	}

	public static float getBGMPosSeconds() {
		return _actualBGM != null ? _actualBGM.getPositionSeconds() : 0.0f;
	}

	// =========================================================================
	// Liberación de Recursos
	// =========================================================================

	public static void clear() {
		if (_actualBGM != null) {
			_actualBGM.close();
			_actualBGM = null;
		}

		for (Sound sfx : _sfxCache.values()) {
			if (sfx != null) {
				sfx.unload();
			}
		}

		_sfxCache.clear();
	}
}
