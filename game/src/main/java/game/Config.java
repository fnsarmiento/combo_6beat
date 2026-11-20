package game;

import engine.Engine;
import engine.KBInput;
import engine.audio.Audio;
import java.io.File;
import java.nio.file.Files;
import processing.data.JSONObject;

/**
 * Configuracion global persistente del juego (`.config` en formato JSON).
 * <p>
 * Agrupa los parametros de volumen de audio, resolucion de ventana/escalado pixel-perfect,
 * asignaciones de teclas (keybindings) primarias y alternativas para el combate ritmico
 * de 6 carriles (ROTN) y controles de movimiento/interaccion en el Hub y menus de UI.
 */
public class Config {

	// =========================================================================
	// CONFIGURACION DE AUDIO
	// =========================================================================

	/** Volumen maestro de la musica de fondo (rango 0.0f .. 1.0f). */
	public static float BGMVolume = 0.5f;

	/** Volumen maestro de los efectos de sonido (rango 0.0f .. 1.0f). */
	public static float SFXVolume = 0.5f;

	/** Indica si todo el subsistema de audio se encuentra silenciado. */
	public static boolean isMuted = false;

	/** Aplica los valores actuales de volumen y silencio al motor de audio (`TinySound`). */
	public static void applyAudioConfig() {
		Audio.setBGMVolume(BGMVolume);
		Audio.setSFXVolume(SFXVolume);
		Audio.setMute(isMuted);
	}

	// =========================================================================
	// CONFIGURACION DE PANTALLA Y ESCALADO PIXEL-PERFECT
	// =========================================================================

	/** Ancho fisico de la ventana de salida (el lienzo virtual interno siempre es 480x270). */
	public static int windowWidth = 1920;

	/** Alto fisico de la ventana de salida (el lienzo virtual interno siempre es 480x270). */
	public static int windowHeight = 1080;

	/** Activa el escalado entero con filtrado nearest-neighbor para preservar el pixel art. */
	public static boolean pixelPerfect = true;

	/** Fuerza la activacion del escalado pixel-perfect en el motor grafico. */
	public static void applyVideoConfig() {
		pixelPerfect = true;
		Engine.setPixelPerfect(true);
	}

	/**
	 * Pre-carga las dimensiones de ventana desde `.config` antes de inicializar Processing
	 * en `Main.settings()`.
	 */
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

	// =========================================================================
	// KEYBINDINGS ROTN (COMBATE RITMICO 6 CARRILES — PRIMARIOS Y ALTERNATIVOS)
	// =========================================================================

	/** Asignacion primaria por defecto para los 6 carriles ritmicos: Q, W, E, U, I, O. */
	private static final int[] DEFAULT_RHYTHM = { 'Q', 'W', 'E', 'U', 'I', 'O' };

	/** Asignacion alternativa por defecto para los 6 carriles ritmicos: A, S, D, J, K, L. */
	private static final int[] DEFAULT_RHYTHM_ALT = { 'A', 'S', 'D', 'J', 'K', 'L' };

	/** Teclas primarias activas para los 6 carriles de `ROTNScene` / `ROTNCore`. */
	public static final int[] RhythmButtons = DEFAULT_RHYTHM.clone();

	/** Teclas alternativas activas para los 6 carriles de `ROTNScene` / `ROTNCore`. */
	public static final int[] RhythmAltButtons = DEFAULT_RHYTHM_ALT.clone();

	/** Comprueba si el carril `laneIndex` (0..5) fue pulsado en el frame actual. */
	public static boolean wasRhythmKeyJustPressed(int laneIndex) {
		return KBInput.wasKeyJustPressed(RhythmButtons[laneIndex])
			|| KBInput.wasKeyJustPressed(RhythmAltButtons[laneIndex]);
	}

	// =========================================================================
	// KEYBINDINGS DE MOVIMIENTO EN HUB Y NAVEGACION DE MENUS / DIALOGOS
	// =========================================================================

	/** Indices de accion compartidos para movimiento por casillas en el Hub y navegacion de UI. */
	public static final int UP = 0;
	public static final int DOWN = 1;
	public static final int LEFT = 2;
	public static final int RIGHT = 3;
	public static final int INTERACT = 4;
	public static final int CANCEL = 5;
	public static final int SPRINT = 5;
	public static final int PULSE = 6;

	/** Asignaciones por defecto para movimiento en el Hub (7 acciones: Arriba, Abajo, Izq, Der, Interactuar, Sprint, Pulso). */
	private static final int[] DEFAULT_RPG = { 'W', 'S', 'A', 'D', 'E', 16, 32 };
	private static final int[] DEFAULT_RPG_ALT = { 38, 40, 37, 39, 10, 16, 'J' };

	/** Asignaciones por defecto para navegacion de menus y dialogos (6 acciones: Arriba, Abajo, Izq, Der, Aceptar, Cancelar). */
	private static final int[] DEFAULT_UI = { 'W', 'S', 'A', 'D', 32, 27 };
	private static final int[] DEFAULT_UI_ALT = { 38, 40, 37, 39, 10, 8 };

	/** Teclas primarias y alternativas para movimiento e interaccion en el Hub (`HubScene`). */
	public static final int[] RPGButtons = DEFAULT_RPG.clone();
	public static final int[] RPGAltButtons = DEFAULT_RPG_ALT.clone();

	/** Teclas primarias y alternativas para navegacion en menus (`InitScene`) y dialogos. */
	public static final int[] UIButtons = DEFAULT_UI.clone();
	public static final int[] UIAltButtons = DEFAULT_UI_ALT.clone();

	/** Comprueba si una accion de movimiento/Hub se mantiene presionada. */
	public static boolean isRPGKeyDown(int actionIndex) {
		return KBInput.isKeyDown(RPGButtons[actionIndex])
			|| KBInput.isKeyDown(RPGAltButtons[actionIndex]);
	}

	/** Comprueba si una accion de movimiento/Hub fue pulsada en el frame actual. */
	public static boolean wasRPGKeyJustPressed(int actionIndex) {
		return KBInput.wasKeyJustPressed(RPGButtons[actionIndex])
			|| KBInput.wasKeyJustPressed(RPGAltButtons[actionIndex]);
	}

	/** Intervalo general para repeticion de teclas mantenidas en UI (segundos). */
	public static final float UI_REPEAT_INITIAL_DELAY = 0.32f;
	public static final float UI_REPEAT_INTERVAL = 0.085f;

	/** Comprueba si una accion de interfaz/menu fue pulsada en el frame actual. */
	public static boolean wasUIKeyJustPressed(int actionIndex) {
		return KBInput.wasKeyJustPressed(UIButtons[actionIndex])
			|| KBInput.wasKeyJustPressed(UIAltButtons[actionIndex]);
	}

	/** Comprueba si una accion de interfaz/menu se mantiene presionada. */
	public static boolean isUIKeyDown(int actionIndex) {
		return KBInput.isKeyDown(UIButtons[actionIndex])
			|| KBInput.isKeyDown(UIAltButtons[actionIndex]);
	}

	/** Temporizador reutilizable para repeticion continua al mantener teclas de navegacion. */
	public static final class UIHoldRepeat {
		private boolean wasHeld = false;
		private float timer = 0.0f;

		public boolean poll(boolean justPressed, boolean isHeld, float dt) {
			if (justPressed) {
				wasHeld = true;
				timer = UI_REPEAT_INITIAL_DELAY;
				return true;
			}
			if (!isHeld) {
				wasHeld = false;
				timer = 0.0f;
				return false;
			}
			if (!wasHeld) {
				wasHeld = true;
				timer = UI_REPEAT_INITIAL_DELAY;
				return false;
			}
			timer -= dt;
			if (timer <= 0.0f) {
				timer = UI_REPEAT_INTERVAL;
				return true;
			}
			return false;
		}

		public void reset() {
			wasHeld = false;
			timer = 0.0f;
		}
	}

	/** Restaura todas las asignaciones de teclas (ROTN, Hub y UI) a sus valores de fabrica. */
	public static void resetDefaultControls() {
		System.arraycopy(DEFAULT_RHYTHM, 0, RhythmButtons, 0, DEFAULT_RHYTHM.length);
		System.arraycopy(DEFAULT_RHYTHM_ALT, 0, RhythmAltButtons, 0, DEFAULT_RHYTHM_ALT.length);
		System.arraycopy(DEFAULT_RPG, 0, RPGButtons, 0, DEFAULT_RPG.length);
		System.arraycopy(DEFAULT_RPG_ALT, 0, RPGAltButtons, 0, DEFAULT_RPG_ALT.length);
		System.arraycopy(DEFAULT_UI, 0, UIButtons, 0, DEFAULT_UI.length);
		System.arraycopy(DEFAULT_UI_ALT, 0, UIAltButtons, 0, DEFAULT_UI_ALT.length);
	}

	/** Convierte un codigo de tecla (`keyCode`) en una etiqueta corta legible para el HUD y menu de controles. */
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

	// =========================================================================
	// PERSISTENCIA DE CONFIGURACION (.config JSON)
	// =========================================================================

	/** Carga la configuracion de audio, video y controles desde el archivo JSON indicado. */
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

	/** Guarda la configuracion actual de audio, video y controles en el archivo JSON indicado. */
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
		json.setJSONObject(
			"ControlUI",
			saveKeyArray(UIButtons, UIAltButtons, new String[] { "Up", "Down", "Left", "Right", "Accept", "Cancel" })
		);
		json.setJSONObject(
			"ControlRPG",
			saveKeyArray(RPGButtons, RPGAltButtons, new String[] { "Up", "Down", "Left", "Right", "Interact", "Sprint", "Pulse" })
		);

		Engine.getApp().saveJSONObject(json, path);
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
