package engine.audio;

import java.io.File;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

/**
 * Envoltorio sobre {@link Clip} de Java Sound para reproducción de música WAV PCM
 * con soporte de búsqueda por milisegundos, bucle continuo, pausa/reanudación y control en dB.
 */
public class MusicTrack {

	// =========================================================================
	// Estado del Clip de Audio
	// =========================================================================

	private Clip _clip;
	private FloatControl _volumeControl;
	private BooleanControl _muteControl;
	private long _pausedMicros = 0;
	private boolean _looping = false;
	private float _currentVolume = 1.0f;
	private boolean _isMuted = false;

	// =========================================================================
	// Carga y Control de Reproducción
	// =========================================================================

	/**
	 * Abre un archivo WAV en memoria dentro de un {@link Clip} y extrae sus controles de ganancia y mute.
	 */
	public void loadMusic(String wavPath) {
		try {
			File file = new File(wavPath);
			try (AudioInputStream ais = AudioSystem.getAudioInputStream(file)) {
				_clip = AudioSystem.getClip();
				_clip.open(ais);
			}

			if (_clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
				_volumeControl = (FloatControl) _clip.getControl(FloatControl.Type.MASTER_GAIN);
			}
			if (_clip.isControlSupported(BooleanControl.Type.MUTE)) {
				_muteControl = (BooleanControl) _clip.getControl(BooleanControl.Type.MUTE);
			}
		} catch (Exception e) {
			System.err.println("Error cargando el audio: " + wavPath);
			e.printStackTrace();
		}
	}

	public void play() {
		play(0, _looping);
	}

	public void play(int startMs, boolean loop) {
		if (_clip == null) return;

		_looping = loop;
		_clip.stop();

		long targetMicros = Math.max(0L, startMs * 1000L);
		if (targetMicros <= _clip.getMicrosecondLength()) {
			_clip.setMicrosecondPosition(targetMicros);
		}

		applyAudioState();
		if (_looping) {
			_clip.loop(Clip.LOOP_CONTINUOUSLY);
		} else {
			_clip.start();
		}
	}

	public void pause() {
		if (_clip != null && _clip.isRunning()) {
			_pausedMicros = _clip.getMicrosecondPosition();
			_clip.stop();
		}
	}

	public void resume() {
		if (_clip != null && !_clip.isRunning()) {
			_clip.setMicrosecondPosition(_pausedMicros);
			applyAudioState();
			if (_looping) {
				_clip.loop(Clip.LOOP_CONTINUOUSLY);
			} else {
				_clip.start();
			}
		}
	}

	public void stop() {
		if (_clip != null) {
			_clip.stop();
			_clip.setMicrosecondPosition(0);
			_pausedMicros = 0;
		}
	}

	// =========================================================================
	// Consultas de Estado, Volumen y Posición Temporal
	// =========================================================================

	public boolean isLoaded() {
		return _clip != null;
	}

	public boolean isPlaying() {
		return _clip != null && _clip.isRunning();
	}

	public void setVolume(float volume) {
		_currentVolume = Math.max(0.0f, Math.min(1.0f, volume));
		applyAudioState();
	}

	public void setMute(boolean muted) {
		_isMuted = muted;
		applyAudioState();
	}

	private void applyAudioState() {
		boolean effectiveMute = _isMuted || _currentVolume <= 0.0001f;
		if (_muteControl != null) {
			_muteControl.setValue(effectiveMute);
		}
		if (_volumeControl != null) {
			if (effectiveMute) {
				_volumeControl.setValue(_volumeControl.getMinimum());
			} else {
				float clamped = Math.max(0.0001f, Math.min(1.0f, _currentVolume));
				float db = (float) (Math.log10(clamped) * 20.0);
				db = Math.max(_volumeControl.getMinimum(), Math.min(_volumeControl.getMaximum(), db));
				_volumeControl.setValue(db);
			}
		}
	}

	public int getPositionMs() {
		if (_clip != null) {
			return (int) (_clip.getMicrosecondPosition() / 1000L);
		}
		return 0;
	}

	/** Ajusta la posición en milisegundos mientras la pista está pausada (ej. retroceso tras pausa). */
	public void setPausedPositionMs(int ms) {
		if (_clip == null) return;
		long targetMicros = Math.max(0L, Math.min(_clip.getMicrosecondLength(), ms * 1000L));
		_pausedMicros = targetMicros;
		_clip.setMicrosecondPosition(targetMicros);
	}

	public float getPositionSeconds() {
		if (_clip != null) {
			return _clip.getMicrosecondPosition() / 1_000_000.0f;
		}
		return 0;
	}

	public void close() {
		if (_clip != null) {
			_clip.stop();
			_clip.close();
		}
	}
}
