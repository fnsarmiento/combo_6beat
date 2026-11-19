package engine.audio;

import java.io.File;

import javax.sound.sampled.*;

public class MusicTrack {
	private Clip _clip;
	private FloatControl _volumeControl;
	private BooleanControl _muteControl;
	private long _pausedMicros = 0;
	private boolean _looping = false;

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

	public void play() { play(0, _looping); }

	public void play(int startMs, boolean loop) {
		if (_clip == null) return;

		_looping = loop;
		_clip.stop();

		long targetMicros = Math.max(0L, startMs * 1000L);
		if (targetMicros <= _clip.getMicrosecondLength()) {
			_clip.setMicrosecondPosition(targetMicros);
		}

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

	public boolean isPlaying() { return _clip != null && _clip.isRunning(); }

	public void setVolume(float volume) {
		if (_volumeControl != null) {
			volume = Math.max(0.0001f, Math.min(1.0f, volume));
			float db = (float) (Math.log10(volume) * 20.0);
			db = Math.max(_volumeControl.getMinimum(), Math.min(_volumeControl.getMaximum(), db));
			_volumeControl.setValue(db);
		}
	}

	public void setMute(boolean muted) {
		if (_muteControl != null) {
			_muteControl.setValue(muted);
		} else {
			setVolume( muted ? 0.0f : Audio.getBGMVolume());
		}
	}

	public int getPositionMs() {
		if (_clip != null) {
			return (int)(_clip.getMicrosecondPosition() / 1000L);
		}
		return 0;
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
