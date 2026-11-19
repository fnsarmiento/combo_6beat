package engine.audio;

import java.io.File;
import java.util.HashMap;

import kuusisto.tinysound.Sound;
import processing.core.PApplet;

import kuusisto.tinysound.TinySound; // SFX solo

public class Audio {
	//private static ArrayList<Sound> _SFX;
	private static final HashMap<String, Sound> _sfxCache = new HashMap<>();
	private static MusicTrack _actualBGM;

	private static float _BGMVolume = 1.0f;
	private static float _SFXVolume = 1.0f;
	//private static float _lastBGMVolume;
	//private static float _lastSFXVolume;
	private static boolean _isMuted = false;

	public static void init(PApplet app) {
		if (!TinySound.isInitialized()) {
		TinySound.init();
		}

		TinySound.setGlobalVolume(_SFXVolume);
	}

	public static float getBGMVolume() {
		return _isMuted ? 0.0f : _BGMVolume;
	}

	public static void setBGMVolume(float volume) {
		_BGMVolume = PApplet.constrain(volume, 0.0f, 1.0f);

		if (_actualBGM != null && !_isMuted)
			_actualBGM.setVolume(_BGMVolume);
	}

	public static float getSFXVolume() {
		return _isMuted ? 0.0f : _SFXVolume;
	}

	public static void setSFXVolume(float volume) {
		_SFXVolume = PApplet.constrain(volume, 0.0f, 1.0f);

		if (!_isMuted) {
			TinySound.setGlobalVolume(_SFXVolume);
		}
	}

	public static void setMute(boolean mute) {
		_isMuted = mute;
		if (_actualBGM != null) {
			_actualBGM.setMute(_isMuted);
		}
		TinySound.setGlobalVolume(_isMuted ? 0.0f : _SFXVolume);
	}

	public static void toggleMute() {
		setMute(!_isMuted);
	}

	// SFX

	public static Sound loadSFX(String path) {
		return _sfxCache.computeIfAbsent(path, k -> TinySound.loadSound(new File(k)));
	}

	public static void playSFX(Sound sfx) {
		if (sfx != null && !_isMuted) {
			sfx.play();
		}
	}

	// BGM

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

	public static void playBGM(MusicTrack bgm, int startMs, boolean loop) {
		if (_actualBGM != null && _actualBGM != bgm) {
			_actualBGM.stop();
		}

		_actualBGM = bgm;
		if (_actualBGM == null) return;

		_actualBGM.setVolume(_BGMVolume);
		_actualBGM.setMute(_isMuted);
		_actualBGM.play(startMs, loop);
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

	// Clear

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

