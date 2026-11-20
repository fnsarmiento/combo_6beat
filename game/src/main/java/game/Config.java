package game;

import engine.Engine;
import engine.KBInput;
import engine.audio.Audio;
import engine.dialogs.GameDialog;
import java.io.File;
import java.nio.file.Files;
import processing.data.JSONObject;

public class Config {
	public static float BGMVolume = 0.5f, SFXVolume = 0.5f;
	public static boolean isMuted = false;

	// Video & Pixel-Perfect (la resolución virtual interna se mantiene en 480x270)
	public static int windowWidth = 1920;
	public static int windowHeight = 1080;
	public static boolean pixelPerfect = true;

	// Índices para legibilidad en RPG y UI
	public static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3, INTERACT = 4, CANCEL = 5, SPRINT = 5, PULSE = 6;

	private static final int[] DEFAULT_RHYTHM = { 'Q', 'W', 'E', 'U', 'I', 'O' };
	private static final int[] DEFAULT_RHYTHM_ALT = { 'A', 'S', 'D', 'J', 'K', 'L' };
	private static final int[] DEFAULT_RPG = { 'W', 'S', 'A', 'D', 'E', 16, 32 };
	private static final int[] DEFAULT_RPG_ALT = { 38, 40, 37, 39, 10, 16, 'J' };
	private static final int[] DEFAULT_UI = { 'W', 'S', 'A', 'D', 32, 27 };
	private static final int[] DEFAULT_UI_ALT = { 38, 40, 37, 39, 10, 8 };

	// Rhythm Controller (6 botones)
	public static final int[] RhythmButtons    = DEFAULT_RHYTHM.clone();
	public static final int[] RhythmAltButtons = DEFAULT_RHYTHM_ALT.clone();

	// RPG World Controller (7 botones: Up, Down, Left, Right, Interact, Sprint/Dash, Pulse)
	public static final int[] RPGButtons    = DEFAULT_RPG.clone();
	public static final int[] RPGAltButtons = DEFAULT_RPG_ALT.clone();

	// UI Controller (6 botones: Up, Down, Left, Right, Accept, Cancel)
	public static final int[] UIButtons    = DEFAULT_UI.clone();
	public static final int[] UIAltButtons = DEFAULT_UI_ALT.clone();

	public static boolean isRPGKeyDown(int actionIndex) {
		return KBInput.isKeyDown(RPGButtons[actionIndex]) || KBInput.isKeyDown(RPGAltButtons[actionIndex]);
	}

	public static boolean wasRPGKeyJustPressed(int actionIndex) {
		return KBInput.wasKeyJustPressed(RPGButtons[actionIndex]) || KBInput.wasKeyJustPressed(RPGAltButtons[actionIndex]);
	}

	public static boolean wasRhythmKeyJustPressed(int laneIndex) {
		return KBInput.wasKeyJustPressed(RhythmButtons[laneIndex]) || KBInput.wasKeyJustPressed(RhythmAltButtons[laneIndex]);
	}

	public static boolean wasUIKeyJustPressed(int actionIndex) {
		return KBInput.wasKeyJustPressed(UIButtons[actionIndex]) || KBInput.wasKeyJustPressed(UIAltButtons[actionIndex]);
	}

	public static void resetDefaultControls() {
		System.arraycopy(DEFAULT_RHYTHM, 0, RhythmButtons, 0, DEFAULT_RHYTHM.length);
		System.arraycopy(DEFAULT_RHYTHM_ALT, 0, RhythmAltButtons, 0, DEFAULT_RHYTHM_ALT.length);
		System.arraycopy(DEFAULT_RPG, 0, RPGButtons, 0, DEFAULT_RPG.length);
		System.arraycopy(DEFAULT_RPG_ALT, 0, RPGAltButtons, 0, DEFAULT_RPG_ALT.length);
		System.arraycopy(DEFAULT_UI, 0, UIButtons, 0, DEFAULT_UI.length);
		System.arraycopy(DEFAULT_UI_ALT, 0, UIAltButtons, 0, DEFAULT_UI_ALT.length);
		syncDialogKeys();
	}

	public static void syncDialogKeys() {
		GameDialog.KEYS_INTERACT = new int[] {
			UIButtons[INTERACT],
			UIAltButtons[INTERACT],
			RPGButtons[INTERACT],
			RPGAltButtons[INTERACT]
		};
		GameDialog.KEYS_LEFT = new int[] {
			UIButtons[LEFT],
			UIAltButtons[LEFT]
		};
		GameDialog.KEYS_RIGHT = new int[] {
			UIButtons[RIGHT],
			UIAltButtons[RIGHT]
		};
	}

	public static String getKeyLabel(int keyCode) {
		return switch (keyCode) {
			case 8  -> "BKSP";
			case 9  -> "TAB";
			case 10 -> "ENTER";
			case 16 -> "SHIFT";
			case 17 -> "CTRL";
			case 18 -> "ALT";
			case 27 -> "ESC";
			case 32 -> "SPACE";
			case 37 -> "LEFT";
			case 38 -> "UP";
			case 39 -> "RIGHT";
			case 40 -> "DOWN";
			default -> {
				if ((keyCode >= 'A' && keyCode <= 'Z') || (keyCode >= '0' && keyCode <= '9')) {
					yield String.valueOf((char) keyCode);
				}
				yield "#" + keyCode;
			}
		};
	}

	// Carga y Guardado

	public static void preloadWindowSize(String path) {
		try {
			File file = new File(path);
			if (!file.exists()) return;
			String raw = Files.readString(file.toPath());
			JSONObject json = JSONObject.parse(raw);
			if (json == null) return;
			JSONObject video = json.getJSONObject("Video");
			if (video != null) {
				int w = video.getInt("Width", 1920);
				int h = video.getInt("Height", 1080);
				if ((w == 1920 && h == 1080) || (w == 1280 && h == 720)) {
					windowWidth = w;
					windowHeight = h;
				}
				pixelPerfect = video.getBoolean("PixelPerfect", true);
			}
		} catch (Exception ignored) {}
	}

	public static void loadConfig(String path) {
		File file = Engine.getApp().sketchFile(path);
		if (!file.exists()) {
			saveConfig(path);
			return;
		}

		JSONObject json = Engine.getApp().loadJSONObject(path);
		if (json == null) return;

		JSONObject audio = json.getJSONObject("Audio");
		if (audio != null) {
			BGMVolume = audio.getFloat("BGMVolume", 0.5f);
			SFXVolume = audio.getFloat("SFXVolume", 0.5f);
			if (audio.hasKey("Muted")) {
				isMuted = audio.getBoolean("Muted", false);
			} else {
				isMuted = audio.getBoolean("BGMMute", false) && audio.getBoolean("SFXMute", false);
			}
		}

		JSONObject video = json.getJSONObject("Video");
		if (video != null) {
			int w = video.getInt("Width", 1920);
			int h = video.getInt("Height", 1080);
			if ((w == 1920 && h == 1080) || (w == 1280 && h == 720)) {
				windowWidth = w;
				windowHeight = h;
			}
			pixelPerfect = video.getBoolean("PixelPerfect", true);
		}

		loadKeyArray(json.getJSONObject("ControlRhythm"), RhythmButtons, RhythmAltButtons, null);

		String[] uiNames = { "Up", "Down", "Left", "Right", "Accept", "Cancel" };
		JSONObject uiCtrl = json.getJSONObject("ControlUI");
		loadKeyArray(uiCtrl, UIButtons, UIAltButtons, uiNames);
		if (uiCtrl != null) {
			if (uiCtrl.hasKey("Acept")) UIButtons[INTERACT] = uiCtrl.getInt("Acept", UIButtons[INTERACT]);
			if (uiCtrl.hasKey("AltAcept")) UIAltButtons[INTERACT] = uiCtrl.getInt("AltAcept", UIAltButtons[INTERACT]);
		}

		String[] rpgNames = { "Up", "Down", "Left", "Right", "Interact", "Sprint", "Pulse" };
		JSONObject rpgCtrl = json.getJSONObject("ControlRPG");
		if (rpgCtrl == null) {
			rpgCtrl = json.getJSONObject("ControlWorld");
		}
		loadKeyArray(rpgCtrl, RPGButtons, RPGAltButtons, rpgNames);
	}

	public static void saveConfig(String path) {
		JSONObject json = new JSONObject();

		JSONObject audio = new JSONObject();
		audio.setFloat("BGMVolume", BGMVolume);
		audio.setFloat("SFXVolume", SFXVolume);
		audio.setBoolean("Muted", isMuted);
		json.setJSONObject("Audio", audio);

		JSONObject video = new JSONObject();
		video.setInt("Width", windowWidth);
		video.setInt("Height", windowHeight);
		video.setBoolean("PixelPerfect", pixelPerfect);
		json.setJSONObject("Video", video);

		json.setJSONObject("ControlRhythm", saveKeyArray(RhythmButtons, RhythmAltButtons, null));
		json.setJSONObject("ControlUI", saveKeyArray(UIButtons, UIAltButtons, new String[] { "Up", "Down", "Left", "Right", "Accept", "Cancel" }));
		json.setJSONObject("ControlRPG", saveKeyArray(RPGButtons, RPGAltButtons, new String[] { "Up", "Down", "Left", "Right", "Interact", "Sprint", "Pulse" }));

		Engine.getApp().saveJSONObject(json, path);
	}

	public static void applyAudioConfig() {
		Audio.setBGMVolume(BGMVolume);
		Audio.setSFXVolume(SFXVolume);
		Audio.setMute(isMuted);
	}

	public static void applyVideoConfig() {
		pixelPerfect = true;
		Engine.setPixelPerfect(true);
	}

	private static void loadKeyArray(JSONObject section, int[] primary, int[] alt, String[] namedKeys) {
		if (section == null) return;
		for (int i = 0; i < primary.length; i++) {
			primary[i] = section.getInt("Button" + (i + 1), primary[i]);
			alt[i] = section.getInt("AltButton" + (i + 1), alt[i]);
			if (namedKeys != null && i < namedKeys.length) {
				primary[i] = section.getInt(namedKeys[i], primary[i]);
				alt[i] = section.getInt("Alt" + namedKeys[i], alt[i]);
			}
		}
	}

	private static JSONObject saveKeyArray(int[] primary, int[] alt, String[] namedKeys) {
		JSONObject section = new JSONObject();
		for (int i = 0; i < primary.length; i++) {
			section.setInt("Button" + (i + 1), primary[i]);
			section.setInt("AltButton" + (i + 1), alt[i]);
			if (namedKeys != null && i < namedKeys.length) {
				section.setInt(namedKeys[i], primary[i]);
				section.setInt("Alt" + namedKeys[i], alt[i]);
			}
		}
		return section;
	}
}
