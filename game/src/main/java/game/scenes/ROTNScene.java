package game.scenes;

import static game.Global.BASE_H;
import static game.Global.BASE_W;

import controlP5.Button;
import controlP5.ControlFont;
import controlP5.Group;
import controlP5.Textarea;
import controlP5.Textlabel;
import engine.Engine;
import engine.KBInput;
import engine.ResourcesManager;
import engine.Scene;
import engine.audio.Audio;
import engine.audio.MusicTrack;
import engine.draw.ParticleSystem;
import game.Config;
import game.core.GameState;
import game.core.db.CustomSongRepository;
import game.core.db.SaveRepository;
import game.core.notes.NotePacker;
import game.core.notes.OsuManiaReader;
import game.core.notes.SongPackLoader;
import game.core.notes.TimingPointPacker;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import kuusisto.tinysound.Sound;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics;
import processing.core.PImage;
import processing.event.KeyEvent;

/**
 * Escena de combate rítmico de 6 carriles en perspectiva 3D (inspirada en Rift of the NecroDancer).
 * Renderiza la arena planetaria con luces de ciudad en rotación, la autopista trapezoidal de 6 carriles,
 * las ondas de pulso tipo guitarra, los bloques rojos de carril bloqueado, las notas simples/mantenidas
 * y el HUD de combate sobre ControlP5.
 */
public class ROTNScene extends Scene {

	// =========================================================================
	// 1. Constantes visuales, geometría 3D de la pista y paleta de colores
	// =========================================================================

	private static final int LANE_COUNT = 6;

	// Geometría de proyección en perspectiva 3D sobre framebuffer virtual 480x270
	private static final float CENTER_X = BASE_W * 0.5f; // 240.0f
	private static final float Y_FAR = 92.0f;            // Horizonte superior de la pista
	private static final float Y_HIT = 218.0f;           // Arco blanco de impacto (receptores)
	private static final float HALF_W_HIT = 126.0f;      // Semiancho de los 6 carriles en Y_HIT
	private static final float ARC_RISE = 16.0f;         // Curvatura parabólica hacia arriba en los extremos
	private static final float Z_FAR = 3.2f;             // Profundidad en el horizonte (t = 0)
	private static final float Z_HIT = 1.0f;             // Profundidad en la línea de impacto (t = 1)
	private static final float S_FAR = 1.0f / Z_FAR;
	private static final float PRESS_SINK_PX = 3.5f;     // Hundimiento vertical al presionar un carril

	// Semiancho normalizado (xi) de las ondas horizontales de compás y del arco de impacto
	private static final float BEAT_WAVE_ARC_MAX_XI = 1.48f;
	private static final float HIT_LINE_ARC_MAX_XI = 1.58f;

	// Velocidad de desplazamiento y tiempos de transición
	private static final float DEFAULT_SCROLL_SPEED = 0.22f;
	private static final float BASE_APPROACH_TIME_MS = 1400.0f;
	private static final float INTRO_COUNTDOWN_DURATION = 3.0f;
	private static final float RESUME_COUNTDOWN_DURATION = 3.0f;
	private static final int PAUSE_REWIND_MS = 2000;
	private static final float BGM_FADE_OUT_DURATION = 3.0f;
	private static final float HOLD_PARTICLE_INTERVAL_SEC = 0.025f;

	// Paleta de colores ARGB para juicios flotantes y partículas
	private static final int COLOR_RATING_PERFECT = 0xFF4FB3FF; // Azul
	private static final int COLOR_RATING_GOOD    = 0xFF55E87A; // Verde
	private static final int COLOR_RATING_SAFE    = 0xFFFFFFFF; // Blanco
	private static final int COLOR_RATING_MISS    = 0xFFB86BFF; // Morado

	// Clusters de luces de ciudad sobre el planeta: { xiCentro, tFase (0..1), cantidadLuces }
	private static final float[][] CITY_LIGHT_CLUSTERS = {
		{ -2.45f, 0.06f, 11 }, // 0: Izquierda lejano
		{  2.35f, 0.14f, 11 }, // 1: Derecha lejano
		{ -1.65f, 0.34f, 10 }, // 2: Izquierda medio
		{  1.70f, 0.44f, 10 }, // 3: Derecha medio
		{ -2.15f, 0.62f, 12 }, // 4: Izquierda otro continente
		{  2.25f, 0.72f, 12 }, // 5: Derecha otro continente
		{ -1.45f, 0.88f,  9 }, // 6: Izquierda cercano
		{  1.50f, 0.94f,  9 }  // 7: Derecha cercano
	};

	// Pares de clusters distantes conectados por caminos de luces en perspectiva
	private static final int[][] CITY_ROAD_PAIRS = {
		{ 0, 4 }, // Gran distancia en fase (0.06 -> 0.62)
		{ 1, 5 }  // Gran distancia en fase (0.14 -> 0.72)
	};

	// =========================================================================
	// 2. Estado de la escena (configuración de canción/batalla, audio,
	//    HUD ControlP5, partículas y luces de ciudad)
	// =========================================================================

	// Origen de datos del nivel (canción personalizada o variante de pack)
	private final CustomSongRepository.CustomSongSummary _customSong;
	private final SongPackLoader.ChartVariant _chartVariant;

	// Metadatos del encuentro y progreso de partida
	private String _trackId = "track_generic";
	private String _trackTitle = "Pista Ritmica";
	private String _trackSubtitle = "";
	private boolean _songAttemptRecorded = false;
	private boolean _customAttemptRecorded = false;
	private boolean _customIsNewRecord = false;
	private int _earnedShopPoints = 0;
	private boolean _pointsGranted = false;
	private boolean _dbBestChecked = false;
	private boolean _hasDbBest = false;
	private int _dbMaxScore = 0;
	private int _dbMaxCombo = 0;

	// Audio, núcleo rítmico y temporizadores de reproducción
	private MusicTrack _bgm;
	private Sound _hitSfx;
	private ROTNCore _rhythmCore;
	private long[] _timingPoints = new long[0];
	private float _scrollSpeed = DEFAULT_SCROLL_SPEED;
	private int _songFallbackTimerMs = 0;
	private boolean _bgmPausedByScene = false;
	private boolean _bgmStarted = false;
	private float _introCountdown = INTRO_COUNTDOWN_DURATION;
	private float _resumeCountdown = 0.0f;
	private int _resumeTargetMs = 0;
	private int _lastSongTimeMs = 0;
	private int _pauseSongTimeMs = 0;
	private float _bgmFadeOutTimer = -1.0f;

	// Animación del planeta, sistema de partículas y textos flotantes
	private float _bgAnimTime = 0.0f;
	private int _lastPlayerHp = 0;
	private int _prevMultiplier = 1;
	private float _multAnimTimer = 0.0f;
	private final ParticleSystem _particles = new ParticleSystem(320);
	private final float[] _holdParticleTimers = new float[LANE_COUNT];
	private final ArrayList<FloatingRating> _floatingRatings = new ArrayList<>();

	private static class FloatingRating {
		String text;
		float x, y, vy;
		float life, maxLife;
		int color;
	}

	// Controladores dinámicos de ControlP5 (actualizados en syncCombatUI)
	private static final float[] SCORE_CHUNK_BASE_SIZES = { 14.0f, 17.0f, 20.0f, 23.0f, 26.0f };
	private final Textlabel[] _uiScoreChunkLbls = new Textlabel[SCORE_CHUNK_BASE_SIZES.length];
	private Textlabel _uiMultLbl;
	private Textlabel _uiComboLbl;
	private Textlabel _uiCountdownLbl;
	private ControlFont _multBaseFont;
	private ControlFont _multPopFont;

	private Button _uiHpFill;
	private Textlabel _uiHpLbl;
	private Textlabel _uiShieldLbl;

	private Textlabel _uiHealRatioLbl;
	private Button _uiHealFill;

	private Textlabel _uiItemLbl;

	private Group _uiModalGroup;
	private Button _uiModalBg;
	private Textlabel _uiModalTitle;
	private Textarea _uiModalDesc;
	private Button _uiModalDivider;
	private Textlabel _uiModalLeftScoreLbl;
	private Textlabel _uiModalLeftComboLbl;
	private Textlabel _uiModalRightScoreLbl;
	private Textlabel _uiModalRightComboLbl;
	private Textlabel _uiModalPointsLbl;
	private Button _uiModalBtn1;
	private Button _uiModalBtn2;
	private Button _uiModalBtn3;
	private Button _uiModalBtn4;
	private int _modalFocusIdx = 0;
	private boolean _modalWasOpen = false;

	// Sub-modal de Configuración en Partida (Audio inmediato + Rebinding 6K Primario/Alternativo)
	private static final int INGAME_SET_BGM = 0;
	private static final int INGAME_SET_SFX = 1;
	private static final int INGAME_SET_MUTE = 2;
	private static final int INGAME_SET_UNFOCUS_MUTE = 3;
	private static final int INGAME_SET_RESET = 4;
	private static final int INGAME_SET_BACK = 5;
	private static final int INGAME_SET_RHYTHM_START = 6; // 6..11 -> C1..C6, 12..17 -> A1..A6
	private static final int[][] INGAME_SET_RIGHT_GRID = {
		{  6, 12 },
		{  7, 13 },
		{  8, 14 },
		{  9, 15 },
		{ 10, 16 },
		{ 11, 17 }
	};

	private Group _uiSettingsGroup;
	private Textlabel _uiSetBgmLbl;
	private Button _uiSetBgmBarFill;
	private Textlabel _uiSetSfxLbl;
	private Button _uiSetSfxBarFill;
	private Button _uiSetMuteBtn;
	private Button _uiSetUnfocusMuteBtn;
	private Button _uiSetResetBtn;
	private Button _uiSetBackBtn;
	private final Button[] _uiSetRhythmBtns = new Button[LANE_COUNT];
	private final Button[] _uiSetRhythmAltBtns = new Button[LANE_COUNT];
	private boolean _inGameSettingsOpen = false;
	private int _inGameSettingsFocus = INGAME_SET_BGM;
	private int _rebindTarget = -1; // -1 = ninguno, 0..5 = primario C1..C6, 6..11 = alternativo A1..A6
	private boolean _ignoreSettingsNavOnce = false;
	private final Config.UIHoldRepeat _settingsUpHold = new Config.UIHoldRepeat();
	private final Config.UIHoldRepeat _settingsDownHold = new Config.UIHoldRepeat();

	// =========================================================================
	// 3. Constructores y ciclo de vida (setup, inicialización de canción, close)
	// =========================================================================

	public ROTNScene(CustomSongRepository.CustomSongSummary customSong) {
		super((String) null);
		this._customSong = customSong;
		this._chartVariant = null;
	}

	public ROTNScene(SongPackLoader.ChartVariant variant) {
		super((String) null);
		this._customSong = (variant != null) ? variant.customDbSummary : null;
		this._chartVariant = variant;
	}

	/** Parámetros de combate recolectados durante la carga del beatmap/escena. */
	private static class CombatConfig {
		int playerMaxHp = 200;
		int missDamage = 12;
		int healThreshold = 50;
		float healActiveDuration = 6.0f;
		int laneLockInterval = 10000;
		int laneLockDuration = 2800;
	}

	@Override
	public void setup() {
		Engine.ensureVirtualScreenSize((int) BASE_W, (int) BASE_H);
		GameState.init();
		Audio.pausePersistentBGM();
		super.setup();

		_songAttemptRecorded = false;
		_dbBestChecked = false;
		_hasDbBest = false;
		_dbMaxScore = 0;
		_dbMaxCombo = 0;

		if (_customSong == null && _chartVariant == null) {
			return;
		}

		CombatConfig cfg = new CombatConfig();
		OsuManiaReader reader = loadSongAndCombatConfig(cfg);

		long[] loadedNotes = (reader != null) ? reader.getNotes() : new long[0];
		_timingPoints = (reader != null && reader.getTimingPoints() != null)
			? reader.getTimingPoints()
			: new long[0];

		_rhythmCore = new ROTNCore(loadedNotes);
		_rhythmCore.configureCombat(
			cfg.playerMaxHp,
			cfg.missDamage,
			cfg.healThreshold,
			cfg.healActiveDuration,
			cfg.laneLockInterval,
			cfg.laneLockDuration
		);
		_rhythmCore.setOnHitListener(this::onNoteHit);
		_lastPlayerHp = _rhythmCore.getPlayerHp();

		_hitSfx = ResourcesManager.loadSFX("sfx/Normal-Hitnormal.wav");

		setupCombatUI();
		syncCombatUI();

		_introCountdown = INTRO_COUNTDOWN_DURATION;
		_bgmStarted = false;
	}

	/**
	 * Carga el lector `.osu`, la pista BGM y los parámetros de combate según la fuente activa.
	 */
	private OsuManiaReader loadSongAndCombatConfig(CombatConfig cfg) {
		float speedMult = Math.max(0.5f, GameState.songSpeedMultiplier);
		OsuManiaReader reader = null;

		if (_customSong != null) {
			_trackId = !_customSong.rutaChart.isEmpty() ? _customSong.rutaChart : ("custom_" + _customSong.idCancion);
			_trackTitle = _customSong.titulo;
			_trackSubtitle = _customSong.artista;
			_scrollSpeed = DEFAULT_SCROLL_SPEED * speedMult;

			if (!_customSong.rutaChart.isEmpty()) {
				String osuAbs = ResourcesManager.getAbsPathFromProject(_customSong.rutaChart);
				reader = new OsuManiaReader(osuAbs);
				_bgm = reader.getSong();
			}
			if (_bgm == null && !_customSong.rutaAudio.isEmpty()) {
				String audioAbs = ResourcesManager.getAbsPathFromProject(_customSong.rutaAudio);
				if (new File(audioAbs).exists()) {
					_bgm = Audio.loadBGM(audioAbs);
				}
			}
		} else if (_chartVariant != null) {
			_scrollSpeed = _chartVariant.scrollSpeed * speedMult;

			if (!_chartVariant.osuAbsPath.isEmpty()) {
				reader = new OsuManiaReader(_chartVariant.osuAbsPath);
				_bgm = reader.getSong();
			}

			_trackId = !_chartVariant.osuRelPath.isEmpty() ? _chartVariant.osuRelPath : _chartVariant.id;
			_trackTitle = _chartVariant.title;
			_trackSubtitle = _chartVariant.artist;
		}

		return reader;
	}

	/**
	 * Callback disparado por {@link ROTNCore} al registrar un juicio en un carril:
	 * genera el cartel flotante sobre el receptor 3D y emite partículas de impacto.
	 */
	private void onNoteHit(int lane, ROTNCore.HitRating rating, int combo, String detail) {
		int ratingColor = switch (rating) {
			case PERFECT -> COLOR_RATING_PERFECT;
			case GOOD    -> COLOR_RATING_GOOD;
			case SAFE    -> COLOR_RATING_SAFE;
			case MISS    -> COLOR_RATING_MISS;
		};

		float xi = laneToXi(lane);
		float cx = projectX(xi, 1.0f);
		float cy = projectY(xi, 1.0f, 1.0f);

		FloatingRating fr = new FloatingRating();
		fr.text = rating.name();
		fr.x = cx;
		fr.y = cy - 18.0f;
		fr.vy = -22.0f;
		fr.life = (rating == ROTNCore.HitRating.MISS) ? 0.75f : 0.55f;
		fr.maxLife = fr.life;
		fr.color = ratingColor;
		_floatingRatings.add(fr);

		// Ráfaga de partículas coloreadas en la posición del texto de juicio
		_particles.emitBurst(fr.x, fr.y, 7, 14.0f, 44.0f, 1.8f, 3.2f, 0.18f, 0.36f, 18.0f, ratingColor);

		// Ráfaga blanca sobre el receptor cuando la nota es acertada
		if (rating != ROTNCore.HitRating.MISS) {
			_particles.emitBurst(cx, cy + PRESS_SINK_PX, 10, 24.0f, 70.0f, 2.2f, 4.0f, 0.18f, 0.36f, 55.0f, 0xFFFFFFFF);
		}
	}

	private void restartBattleScene() {
		if (_chartVariant != null) {
			Engine.changeScene(new ROTNScene(_chartVariant));
		} else {
			CustomSongRepository.CustomSongSummary latest = (_customSong != null)
				? CustomSongRepository.getById(_customSong.idCancion)
				: null;
			Engine.changeScene(new ROTNScene(latest != null ? latest : _customSong));
		}
	}

	private void returnToWorld() {
		Audio.stopTransientBGM();
		Engine.changeScene(new InitScene(InitScene.ViewMode.SONG_SELECT));
	}

	@Override
	public void close() {
		super.close();
		Audio.stopTransientBGM();
	}

	// =========================================================================
	// 4. Construcción y sincronización de UI ControlP5 (HUD superior, barra de
	//    progreso de curación, modales de pausa, victoria y derrota)
	// =========================================================================

	private PImage createPixelHeartIcon() {
		int w = Math.max(16, (int) scaleW(24));
		int h = Math.max(16, (int) scaleH(24));
		String[] rows = {
			"..RRRR..RRRR..",
			".RLLLLRRLLRRR.",
			"RLLRRRRRRRRRRR",
			"RLLRRRRRRRRRRR",
			"RRRRRRRRRRRRRR",
			".RRRRRRRRRRRR.",
			"..RRRRRRRRRR..",
			"...RRRRRRRR...",
			"....RRRRRR....",
			".....RRRR.....",
			"......RR......"
		};
		return bakeAsciiIcon(rows, w, h);
	}

	private PImage createPixelHealIcon() {
		int w = Math.max(16, (int) scaleW(24));
		int h = Math.max(16, (int) scaleH(24));
		String[] rows = {
			"....MMMMMM....",
			"...MLLLLLLM...",
			"..MLMMMMMLLM..",
			"..MMMMMMMMMM..",
			"...MMMMMMMM...",
			"....MMMMMM....",
			".....MMMM..WW.",
			"......MM..WWWW",
			".......WWWWWW.",
			"........WWWW..",
			".........WW..."
		};
		return bakeAsciiIcon(rows, w, h);
	}

	private PImage bakeAsciiIcon(String[] rows, int targetW, int targetH) {
		PApplet app = Engine.getApp();
		PImage img = app.createImage(targetW, targetH, PConstants.ARGB);
		img.loadPixels();
		int srcH = rows.length;
		int srcW = rows[0].length();
		for (int py = 0; py < targetH; py++) {
			int sy = Math.min(srcH - 1, (py * srcH) / targetH);
			String row = rows[sy];
			for (int px = 0; px < targetW; px++) {
				int sx = Math.min(srcW - 1, (px * srcW) / targetW);
				char c = row.charAt(sx);
				int col = switch (c) {
					case 'R' -> app.color(215, 42, 55, 255);
					case 'L' -> app.color(255, 130, 140, 255);
					case 'M' -> app.color(185, 88, 52, 255);
					case 'W' -> app.color(240, 232, 210, 255);
					default  -> app.color(0, 0, 0, 0);
				};
				img.pixels[py * targetW + px] = col;
			}
		}
		img.updatePixels();
		return img;
	}

	/**
	 * Construye todos los widgets de ControlP5 para el HUD de combate y el modal de estado.
	 */
	private void setupCombatUI() {
		PApplet app = Engine.getApp();

		ControlFont fontSmall = makeFont(6.5f, false);
		ControlFont fontMed = makeFont(8.0f, false);
		ControlFont fontBig = makeFont(10.5f, true);
		ControlFont fontModalPoints = makeFont(10.0f, true);
		ControlFont fontModalTitle = makeFont(18.0f, true);

		// 1. Arriba-Izquierda: Corazón + Vida del Jugador + Estado del Escudo
		PImage heartImg = createPixelHeartIcon();
		addUIBox("ui_rotn_heart_icon", null, 10, 8, 24, 24, 0)
			.setSize(heartImg.width, heartImg.height)
			.setImage(heartImg);

		_uiHpLbl = addUILabel("ui_rotn_hp_lbl", null, "HP: 100/100", 38, 9, app.color(245, 235, 240), fontMed);
		addUIBox("ui_rotn_hp_bg", null, 38, 21, 96, 7, app.color(40, 20, 28, 220));
		_uiHpFill = addUIBox("ui_rotn_hp_fill", null, 38, 21, 96, 7, app.color(230, 65, 85, 245));
		_uiShieldLbl = addUILabel("ui_rotn_shield_lbl", null, "", 38, 31, app.color(110, 235, 255), fontSmall);

		// 2. HUD Derecho en ControlP5 (Score multi-color por bloques de 3 cifras, Multiplicador amarillo, Combo azul)
		for (int i = 0; i < _uiScoreChunkLbls.length; i++) {
			ControlFont chunkFont = makeFont(SCORE_CHUNK_BASE_SIZES[i], true);
			_uiScoreChunkLbls[i] = addUILabel("ui_rotn_score_c" + i, null, "", BASE_W - 14, 8, app.color(255), chunkFont);
			_uiScoreChunkLbls[i].hide();
		}

		_multBaseFont = makeFont(24.0f, true);
		_multPopFont = makeFont(34.0f, true);

		_uiMultLbl = addUILabel("ui_rotn_mult_lbl", null, "x1", 412, 95, app.color(255, 224, 58), _multBaseFont);
		_uiComboLbl = addUILabel("ui_rotn_combo_lbl", null, "0", 416, 134, app.color(75, 190, 255), _multBaseFont);

		ControlFont fontCountdown = makeFont(30.0f, true);
		_uiCountdownLbl = addUILabel("ui_rotn_countdown_lbl", null, "", CENTER_X - 10, 113, app.color(255, 235, 110), fontCountdown);
		_uiCountdownLbl.hide();

		// 4. Abajo-Izquierda: Barra Vertical de Curación Dinámica
		_uiHealRatioLbl = addUILabel("ui_rotn_heal_ratio_lbl", null, "0/2", 12, 162, app.color(230, 60, 60), fontBig);
		addUIBox("ui_rotn_heal_bg", null, 14, 178, 14, 76, app.color(24, 30, 38, 225));
		_uiHealFill = addUIBox("ui_rotn_heal_fill", null, 14, 252, 14, 1, app.color(45, 200, 90, 245));
		addUILabel("ui_rotn_heal_t2", null, "2", 31, 200, app.color(220, 235, 220), fontSmall);
		addUILabel("ui_rotn_heal_t1", null, "1", 31, 238, app.color(220, 235, 220), fontSmall);

		// 5. Abajo-Derecha: Ícono pixel art de curación + Estrategia + Amuleto equipado
		addUIBox("ui_rotn_item_badge", null, 346, 232, 124, 30, app.color(18, 16, 28, 205));
		PImage healImg = createPixelHealIcon();
		addUIBox("ui_rotn_item_icon", null, 442, 235, 24, 24, 0)
			.setSize(healImg.width, healImg.height)
			.setImage(healImg);
		_uiItemLbl = addUILabel("ui_rotn_item_lbl", null, "Cura: Optimo", 352, 238, app.color(235, 240, 255), fontSmall);

		// 6. Modal ControlP5 para Pausa / Victoria / Derrota
		_uiModalGroup = addUIGroup("ui_rotn_modal_group", (BASE_W - 180) * 0.5f, (BASE_H - 164) * 0.5f, 180, 164);
		_uiModalBg = addUIBox("ui_rotn_modal_bg", _uiModalGroup, 0, 0, 180, 164, app.color(12, 14, 24, 238));
		_uiModalTitle = addUILabel("ui_rotn_modal_title", _uiModalGroup, "PAUSA", 20, 12, app.color(255), fontModalTitle);
		_uiModalDesc = addUITextarea("ui_rotn_modal_desc", _uiModalGroup, "", 20, 42, 220, 52, app.color(230, 238, 250), app.color(18, 22, 34), fontMed);
		_uiModalDesc.hide();

		_uiModalDivider = addUIBox("ui_rotn_modal_divider", _uiModalGroup, 138, 46, 1, 40, app.color(75, 100, 155, 200));
		_uiModalLeftScoreLbl = addUILabel("ui_rotn_modal_l_score", _uiModalGroup, "", 18, 48, app.color(140, 230, 255), fontMed);
		_uiModalLeftComboLbl = addUILabel("ui_rotn_modal_l_combo", _uiModalGroup, "", 18, 66, app.color(200, 225, 255), fontMed);
		_uiModalRightScoreLbl = addUILabel("ui_rotn_modal_r_score", _uiModalGroup, "", 148, 48, app.color(255, 215, 110), fontMed);
		_uiModalRightComboLbl = addUILabel("ui_rotn_modal_r_combo", _uiModalGroup, "", 148, 66, app.color(255, 215, 110), fontMed);
		_uiModalPointsLbl = addUILabel("ui_rotn_modal_pts", _uiModalGroup, "", 18, 92, app.color(255, 215, 110), fontModalPoints);

		_uiModalBtn1 = addUIButton(
			"ui_rotn_modal_btn1", _uiModalGroup, "Reanudar", 20, 44, 140, 22,
			app.color(35, 90, 155), app.color(60, 130, 215), fontSmall,
			() -> onModalButtonClick(0)
		);
		_uiModalBtn2 = addUIButton(
			"ui_rotn_modal_btn2", _uiModalGroup, "Reiniciar", 20, 72, 140, 22,
			app.color(35, 90, 155), app.color(60, 130, 215), fontSmall,
			() -> onModalButtonClick(1)
		);
		_uiModalBtn3 = addUIButton(
			"ui_rotn_modal_btn3", _uiModalGroup, "Configuracion", 20, 100, 140, 22,
			app.color(45, 72, 118), app.color(75, 115, 185), fontSmall,
			() -> onModalButtonClick(2)
		);
		_uiModalBtn4 = addUIButton(
			"ui_rotn_modal_btn4", _uiModalGroup, "Salir", 20, 128, 140, 22,
			app.color(95, 38, 52), app.color(145, 55, 75), fontSmall,
			() -> onModalButtonClick(3)
		);

		_uiModalGroup.hide();

		// 7. Sub-modal de Configuración en Partida
		buildInGameSettingsUI(app, fontSmall);
	}

	private void buildInGameSettingsUI(PApplet app, ControlFont fontSmall) {
		_uiSettingsGroup = addUIGroup("ui_rotn_set_group", 30, 26, 420, 218);
		addUIBox("ui_rotn_set_bg", _uiSettingsGroup, 0, 0, 420, 218, app.color(12, 14, 24, 245));
		addUILabel("ui_rotn_set_hdr", _uiSettingsGroup, "CONFIGURACION EN PARTIDA", 14, 12, app.color(255, 225, 130), fontSmall);

		// Columna izquierda: Volumen inmediato BGM/SFX, Mute, Mute sin foco, Modo teclas y Volver
		_uiSetBgmLbl = addUILabel("ui_rotn_set_bgm_lbl", _uiSettingsGroup, "BGM: 50%", 14, 34, app.color(220, 235, 255), fontSmall);
		addUIButton("ui_rotn_set_bgm_minus", _uiSettingsGroup, "-", 14, 46, 22, 16, app.color(35, 45, 70), app.color(55, 85, 140), fontSmall,
			() -> { _inGameSettingsFocus = INGAME_SET_BGM; adjustInGameBgmVolume(-0.1f); });
		addUIBox("ui_rotn_set_bgm_bar_bg", _uiSettingsGroup, 40, 48, 138, 12, app.color(26, 30, 42));
		_uiSetBgmBarFill = addUIBox("ui_rotn_set_bgm_bar_fill", _uiSettingsGroup, 40, 48, 69, 12, app.color(80, 195, 255));
		addUIButton("ui_rotn_set_bgm_plus", _uiSettingsGroup, "+", 182, 46, 22, 16, app.color(35, 45, 70), app.color(55, 85, 140), fontSmall,
			() -> { _inGameSettingsFocus = INGAME_SET_BGM; adjustInGameBgmVolume(0.1f); });

		_uiSetSfxLbl = addUILabel("ui_rotn_set_sfx_lbl", _uiSettingsGroup, "SFX: 50%", 14, 70, app.color(220, 235, 255), fontSmall);
		addUIButton("ui_rotn_set_sfx_minus", _uiSettingsGroup, "-", 14, 82, 22, 16, app.color(35, 45, 70), app.color(55, 85, 140), fontSmall,
			() -> { _inGameSettingsFocus = INGAME_SET_SFX; adjustInGameSfxVolume(-0.1f); });
		addUIBox("ui_rotn_set_sfx_bar_bg", _uiSettingsGroup, 40, 84, 138, 12, app.color(26, 30, 42));
		_uiSetSfxBarFill = addUIBox("ui_rotn_set_sfx_bar_fill", _uiSettingsGroup, 40, 84, 69, 12, app.color(95, 235, 165));
		addUIButton("ui_rotn_set_sfx_plus", _uiSettingsGroup, "+", 182, 82, 22, 16, app.color(35, 45, 70), app.color(55, 85, 140), fontSmall,
			() -> { _inGameSettingsFocus = INGAME_SET_SFX; adjustInGameSfxVolume(0.1f); });

		_uiSetMuteBtn = addUIButton("ui_rotn_set_mute", _uiSettingsGroup, "MUTE GENERAL: OFF", 14, 106, 190, 18,
			app.color(32, 42, 66), app.color(55, 95, 155), fontSmall, () -> {
				_inGameSettingsFocus = INGAME_SET_MUTE;
				toggleInGameMute();
			});
		_uiSetUnfocusMuteBtn = addUIButton("ui_rotn_set_unfocus_mute", _uiSettingsGroup, "MUTE SIN FOCO: OFF", 14, 128, 190, 18,
			app.color(32, 42, 66), app.color(55, 95, 155), fontSmall, () -> {
				_inGameSettingsFocus = INGAME_SET_UNFOCUS_MUTE;
				toggleInGameMuteOnUnfocus();
			});
		_uiSetResetBtn = addUIButton("ui_rotn_set_reset", _uiSettingsGroup, "RESTAURAR TECLAS", 14, 154, 190, 18,
			app.color(65, 52, 32), app.color(110, 88, 52), fontSmall, () -> {
				_inGameSettingsFocus = INGAME_SET_RESET;
				_rebindTarget = -1;
				Config.resetDefaultControls();
				Config.saveConfig(".config");
				syncInGameSettingsUI(app);
			});
		_uiSetBackBtn = addUIButton("ui_rotn_set_back", _uiSettingsGroup, "LISTO / VOLVER", 14, 182, 190, 22,
			app.color(35, 115, 78), app.color(55, 160, 110), fontSmall, this::closeInGameSettings);

		// Columna derecha: Rebinding rápido de los 6 carriles (Primarios C1..C6 y Alternativos A1..A6)
		addUILabel("ui_rotn_set_keys_hdr", _uiSettingsGroup, "CARRILES 6K (PRIM / ALT)", 218, 30, app.color(140, 215, 255), fontSmall);
		for (int i = 0; i < LANE_COUNT; i++) {
			final int lane = i;
			int btnY = 44 + i * 24;
			_uiSetRhythmBtns[i] = addUIButton("ui_rotn_set_k_" + i, _uiSettingsGroup, "",
				218, btnY, 92, 20,
				app.color(28, 34, 52), app.color(50, 95, 165), fontSmall, () -> {
					_inGameSettingsFocus = INGAME_SET_RHYTHM_START + lane;
					_rebindTarget = lane;
					syncInGameSettingsUI(app);
				});
			_uiSetRhythmAltBtns[i] = addUIButton("ui_rotn_set_ka_" + i, _uiSettingsGroup, "",
				314, btnY, 92, 20,
				app.color(28, 34, 52), app.color(50, 95, 165), fontSmall, () -> {
					_inGameSettingsFocus = INGAME_SET_RHYTHM_START + LANE_COUNT + lane;
					_rebindTarget = LANE_COUNT + lane;
					syncInGameSettingsUI(app);
				});
		}
		addUITextarea("ui_rotn_set_hint", _uiSettingsGroup,
			"Click/Enter: reasignar | Izq/Der: vol | ESC: volver",
			218, 190, 188, 22, app.color(175, 205, 240), app.color(18, 22, 36), fontSmall);

		_uiSettingsGroup.hide();
	}

	private void onModalButtonClick(int slot) {
		if (_rhythmCore == null) return;
		ROTNCore.BattleState state = _rhythmCore.getBattleState();
		if (state == ROTNCore.BattleState.VICTORY || state == ROTNCore.BattleState.DEFEAT) {
			_modalFocusIdx = (slot == 0) ? 0 : 1;
			if (slot == 0) {
				restartBattleScene();
			} else if (slot == 1) {
				returnToWorld();
			}
		} else if (_rhythmCore.isPaused()) {
			_modalFocusIdx = PApplet.constrain(slot, 0, 3);
			switch (slot) {
				case 0 -> unpauseBattle();
				case 1 -> restartBattleScene();
				case 2 -> openInGameSettings();
				case 3 -> returnToWorld();
			}
		}
	}

	private void unpauseBattle() {
		if (_rhythmCore == null || !_rhythmCore.isPaused()) return;
		_rhythmCore.togglePause();
		_modalWasOpen = false;
		_inGameSettingsOpen = false;
		_rebindTarget = -1;
		_settingsUpHold.reset();
		_settingsDownHold.reset();
		beginResumeCountdown();
	}

	private float measurePhysicalTextWidth(PApplet app, String text, float pxSize) {
		if (app == null || app.g == null || text == null || text.isEmpty()) return 0.0f;
		app.g.pushStyle();
		if (Engine.titleFont != null) {
			app.g.textFont(Engine.titleFont, pxSize);
		} else {
			app.g.textSize(pxSize);
		}
		float w = app.g.textWidth(text);
		app.g.popStyle();
		return w;
	}

	private int getScoreTierColor(PApplet app, int groupIdx) {
		return switch (groupIdx) {
			case 0 -> app.color(75, 190, 255);   // Unidades..Centenas: Azul
			case 1 -> app.color(195, 110, 255);  // Miles: Morado
			case 2 -> app.color(255, 78, 92);    // Millones: Rojo
			case 3 -> app.color(255, 155, 55);   // Miles de millones: Naranja
			default -> app.color(255, 224, 58);  // 13+ cifras: Amarillo
		};
	}

	private void beginResumeCountdown() {
		if (_rhythmCore == null) return;
		if (_rhythmCore.getBattleState() != ROTNCore.BattleState.PLAYING || _rhythmCore.isTrackFinished(_pauseSongTimeMs)) {
			_resumeCountdown = 0.0f;
			if (_bgm != null && _bgm.isLoaded() && _bgmPausedByScene) {
				_bgmPausedByScene = false;
				Audio.resumeBGM();
				Audio.syncTrackVolume(_bgm);
			}
			return;
		}
		if (_introCountdown > 0.0f) {
			_introCountdown = INTRO_COUNTDOWN_DURATION;
			return;
		}
		_resumeTargetMs = Math.max(0, _pauseSongTimeMs - PAUSE_REWIND_MS);
		_resumeCountdown = RESUME_COUNTDOWN_DURATION;
		_rhythmCore.cancelInterruptedHoldsOnRewind(_resumeTargetMs);
		_songFallbackTimerMs = _resumeTargetMs;
		if (_bgm != null && _bgm.isLoaded()) {
			if (_bgm.isPlaying()) {
				Audio.pauseBGM();
			}
			_bgmPausedByScene = true;
			_bgm.setPausedPositionMs(_resumeTargetMs);
		}
	}

	private void syncRightHudControlP5(PApplet app) {
		// 1. Score arriba a la derecha por bloques de 3 cifras (cada grupo con su color y tamaño en ControlP5)
		String scoreText = String.valueOf(Math.max(0, _rhythmCore.getScore()));
		float cursorX = scaleW(BASE_W - 10.0f);
		int pos = scoreText.length();
		for (int gIdx = 0; gIdx < _uiScoreChunkLbls.length; gIdx++) {
			Textlabel lbl = _uiScoreChunkLbls[gIdx];
			if (lbl == null) continue;
			if (pos > 0) {
				int start = Math.max(0, pos - 3);
				String chunk = scoreText.substring(start, pos);
				pos = start;

				float pxSize = Math.max(8, (int) scaleH(SCORE_CHUNK_BASE_SIZES[gIdx]));
				float chunkW = measurePhysicalTextWidth(app, chunk, pxSize);
				cursorX -= chunkW;
				float yPos = scaleH(Math.max(2.0f, 13.0f - SCORE_CHUNK_BASE_SIZES[gIdx] * 0.35f));
				lbl.setPosition(cursorX, yPos);
				lbl.setColorValue(getScoreTierColor(app, gIdx));
				lbl.setText(chunk);
				lbl.show();
				cursorX -= scaleW(2.0f);
			} else {
				lbl.hide();
			}
		}

		// 2. Multiplicador a la derecha en amarillo (con animación de salto y tamaño al subir)
		if (_uiMultLbl != null) {
			String multText = "x" + _rhythmCore.getScoreMultiplier();
			_uiMultLbl.setText(multText);
			if (_multAnimTimer > 0.0f) {
				float p = 1.0f - (_multAnimTimer / 0.42f);
				float jumpY = (float) (-Math.sin(p * Math.PI) * 9.0f);
				float shakeX = (float) (Math.sin(p * Math.PI * 6.0f) * (1.0f - p) * 5.0f);
				if (_multPopFont != null) _uiMultLbl.setFont(_multPopFont);
				float w = measurePhysicalTextWidth(app, multText, Math.max(8, (int) scaleH(34.0f)));
				_uiMultLbl.setPosition(scaleW(424.0f + shakeX) - w * 0.5f, scaleH(90.0f + jumpY));
			} else {
				if (_multBaseFont != null) _uiMultLbl.setFont(_multBaseFont);
				float w = measurePhysicalTextWidth(app, multText, Math.max(8, (int) scaleH(24.0f)));
				_uiMultLbl.setPosition(scaleW(424.0f) - w * 0.5f, scaleH(95.0f));
			}
		}

		// 3. Combo debajo en azul (sin etiqueta "COMBO")
		if (_uiComboLbl != null) {
			String comboText = String.valueOf(_rhythmCore.getCombo());
			_uiComboLbl.setText(comboText);
			float w = measurePhysicalTextWidth(app, comboText, Math.max(8, (int) scaleH(24.0f)));
			_uiComboLbl.setPosition(scaleW(424.0f) - w * 0.5f, scaleH(134.0f));
		}

		// 4. Contador regresivo 3..2..1 en ControlP5
		if (_uiCountdownLbl != null) {
			float activeCountdown = Math.max(_introCountdown, _resumeCountdown);
			if (activeCountdown > 0.0f) {
				int sec = PApplet.constrain((int) Math.ceil(activeCountdown), 1, 3);
				String secText = String.valueOf(sec);
				_uiCountdownLbl.setText(secText);
				float w = measurePhysicalTextWidth(app, secText, Math.max(8, (int) scaleH(30.0f)));
				_uiCountdownLbl.setPosition(scaleW(CENTER_X) - w * 0.5f, scaleH(113.0f));
				_uiCountdownLbl.show();
			} else {
				_uiCountdownLbl.hide();
			}
		}
	}

	private boolean isGameplayInputActive() {
		return _rhythmCore != null
			&& _rhythmCore.getBattleState() == ROTNCore.BattleState.PLAYING
			&& !_rhythmCore.isPaused()
			&& !_inGameSettingsOpen
			&& _resumeCountdown <= 0.0f;
	}

	private void openInGameSettings() {
		_inGameSettingsOpen = true;
		_inGameSettingsFocus = INGAME_SET_BGM;
		_rebindTarget = -1;
		_ignoreSettingsNavOnce = true;
		_settingsUpHold.reset();
		_settingsDownHold.reset();
	}

	private void closeInGameSettings() {
		_rebindTarget = -1;
		_inGameSettingsOpen = false;
		_settingsUpHold.reset();
		_settingsDownHold.reset();
		Config.saveConfig(".config");
		if (_uiSettingsGroup != null) _uiSettingsGroup.hide();
	}

	private void adjustInGameBgmVolume(float delta) {
		Config.BGMVolume = PApplet.constrain(Math.round((Config.BGMVolume + delta) * 10.0f) / 10.0f, 0.0f, 1.0f);
		Config.applyAudioConfig();
		Audio.syncTrackVolume(_bgm);
		Config.saveConfig(".config");
		syncInGameSettingsUI(Engine.getApp());
	}

	private void adjustInGameSfxVolume(float delta) {
		Config.SFXVolume = PApplet.constrain(Math.round((Config.SFXVolume + delta) * 10.0f) / 10.0f, 0.0f, 1.0f);
		Config.applyAudioConfig();
		Audio.playSFX(_hitSfx);
		Config.saveConfig(".config");
		syncInGameSettingsUI(Engine.getApp());
	}

	private void toggleInGameMute() {
		Config.isMuted = !Config.isMuted;
		Config.applyAudioConfig();
		Audio.syncTrackVolume(_bgm);
		Config.saveConfig(".config");
		syncInGameSettingsUI(Engine.getApp());
	}

	private void toggleInGameMuteOnUnfocus() {
		Config.muteOnUnfocus = !Config.muteOnUnfocus;
		Config.applyAudioConfig();
		Config.saveConfig(".config");
		syncInGameSettingsUI(Engine.getApp());
	}

	/**
	 * Sincroniza los textos, barras de progreso y visibilidad del modal de ControlP5
	 * con el estado actual de {@link ROTNCore}.
	 */
	private void syncCombatUI() {
		if (_rhythmCore == null) return;
		PApplet app = Engine.getApp();

		// 1. HP del Jugador y Escudo
		float pHpRatio = (float) _rhythmCore.getPlayerHp() / Math.max(1, _rhythmCore.getPlayerMaxHp());
		pHpRatio = PApplet.constrain(pHpRatio, 0.0f, 1.0f);
		if (_uiHpLbl != null) {
			_uiHpLbl.setText("HP: " + _rhythmCore.getPlayerHp() + "/" + _rhythmCore.getPlayerMaxHp());
		}
		if (_uiHpFill != null) {
			int w = Math.max(1, Math.round(scaleW(96) * pHpRatio));
			_uiHpFill.setSize(w, Math.max(1, (int) scaleH(7)));
			_uiHpFill.setColorBackground(
				_rhythmCore.getInvincibilityTimer() > 0
					? app.color(255, 230, 90, 245)
					: app.color(230, 65, 85, 245)
			);
		}
		if (_uiShieldLbl != null) {
			if (_rhythmCore.hasInvincibilityShield()) {
				_uiShieldLbl.setText("[ESCUDO LISTO]");
				_uiShieldLbl.setColorValue(app.color(110, 235, 255));
			} else if (_rhythmCore.getInvincibilityTimer() > 0) {
				_uiShieldLbl.setText("[INVENCIBLE]");
				_uiShieldLbl.setColorValue(app.color(255, 230, 90));
			} else {
				_uiShieldLbl.setText("");
			}
		}

		// 2. HUD Derecho y contador regresivo en ControlP5
		syncRightHudControlP5(app);

		// 4. Barra vertical de Curación Dinámica (abajo-izquierda)
		int threshold = _rhythmCore.getEffectiveHealThreshold();
		float healRatio = _rhythmCore.isHealBarActive()
			? (_rhythmCore.getHealBarTimer() / Math.max(0.1f, _rhythmCore.getHealActiveDuration()))
			: ((float) _rhythmCore.getHealPoints() / Math.max(1, threshold));
		healRatio = PApplet.constrain(healRatio, 0.0f, 1.0f);

		if (_uiHealRatioLbl != null) {
			if (_rhythmCore.isHealBarActive()) {
				_uiHealRatioLbl.setText(String.format("%.1fs", _rhythmCore.getHealBarTimer()));
				_uiHealRatioLbl.setColorValue(app.color(90, 255, 165));
			} else {
				_uiHealRatioLbl.setText(_rhythmCore.getHealPoints() + "/" + threshold);
				_uiHealRatioLbl.setColorValue(app.color(230, 55, 65));
			}
		}
		if (_uiHealFill != null) {
			int fullH = Math.max(1, (int) scaleH(76));
			int fillH = Math.max(1, Math.round(fullH * healRatio));
			float bottomY = scaleH(178 + 76);
			_uiHealFill.setPosition(scaleW(14), bottomY - fillH);
			_uiHealFill.setSize(Math.max(1, (int) scaleW(14)), fillH);
			_uiHealFill.setColorBackground(
				_rhythmCore.isHealBarActive()
					? app.color(85, 255, 185, 250)
					: app.color(45, 200, 90, 240)
			);
		}

		// 5. Resumen de estrategia de curación y amuletos (abajo-derecha)
		if (_uiItemLbl != null) {
			List<String> eq = GameState.getEquippedPowerUps();
			String chipName;
			if (eq.isEmpty()) {
				chipName = "Sin Amuleto";
			} else if (eq.size() == 1) {
				chipName = GameState.getItemName(eq.get(0));
			} else {
				chipName = eq.size() + " Amuletos";
			}
			_uiItemLbl.setText("Cura: " + GameState.healStrategy.label + "\n" + chipName);
		}

		// 6. Modal de Pausa / Victoria / Derrota
		syncModalUI(app);
	}

	private void syncModalUI(PApplet app) {
		if (_uiModalGroup == null) return;

		if (_rhythmCore.isPaused() && _inGameSettingsOpen) {
			_uiModalGroup.hide();
			if (_uiSettingsGroup != null) {
				_uiSettingsGroup.show();
				syncInGameSettingsUI(app);
			}
			return;
		}
		if (_uiSettingsGroup != null) {
			_uiSettingsGroup.hide();
		}

		ROTNCore.BattleState state = _rhythmCore.getBattleState();
		if (state == ROTNCore.BattleState.VICTORY) {
			float modalW = 276.0f;
			float modalH = 166.0f;
			_uiModalGroup.setPosition(scaleW(102.0f), scaleH(52.0f));
			_uiModalGroup.setWidth(Math.max(1, (int) scaleW(modalW)));
			_uiModalGroup.setHeight(Math.max(1, (int) scaleH(modalH)));
			_uiModalBg.setPosition(0, 0);
			_uiModalBg.setSize(Math.max(1, (int) scaleW(modalW)), Math.max(1, (int) scaleH(modalH)));
			_uiModalGroup.show();

			String titleText = "VICTORIA";
			float titleW = measurePhysicalTextWidth(app, titleText, Math.max(8, (int) scaleH(18.0f)));
			_uiModalTitle.setText(titleText);
			_uiModalTitle.setColorValue(app.color(90, 245, 140));
			_uiModalTitle.setPosition(Math.max(4.0f, (scaleW(modalW) - titleW) * 0.5f), scaleH(12.0f));

			_uiModalDesc.hide();
			_uiModalLeftScoreLbl.setPosition(scaleW(18.0f), scaleH(46.0f));
			_uiModalLeftScoreLbl.setText("Score: " + _rhythmCore.getScore());
			_uiModalLeftScoreLbl.show();
			_uiModalLeftComboLbl.setPosition(scaleW(18.0f), scaleH(64.0f));
			_uiModalLeftComboLbl.setText("Best Combo: " + _rhythmCore.getMaxCombo() + "x");
			_uiModalLeftComboLbl.show();

			if (_hasDbBest) {
				_uiModalDivider.setPosition(scaleW(138.0f), scaleH(44.0f));
				_uiModalDivider.setSize(Math.max(1, (int) scaleW(1.0f)), Math.max(1, (int) scaleH(38.0f)));
				_uiModalDivider.show();
				_uiModalRightScoreLbl.setPosition(scaleW(148.0f), scaleH(46.0f));
				_uiModalRightScoreLbl.setText("Max Score: " + _dbMaxScore);
				_uiModalRightScoreLbl.show();
				_uiModalRightComboLbl.setPosition(scaleW(148.0f), scaleH(64.0f));
				_uiModalRightComboLbl.setText("Max Combo: " + _dbMaxCombo + "x");
				_uiModalRightComboLbl.show();
			} else {
				_uiModalDivider.hide();
				_uiModalRightScoreLbl.hide();
				_uiModalRightComboLbl.hide();
			}

			String ptsText = "Puntos ganados: +" + _earnedShopPoints;
			float ptsW = measurePhysicalTextWidth(app, ptsText, Math.max(8, (int) scaleH(10.0f)));
			_uiModalPointsLbl.setText(ptsText);
			_uiModalPointsLbl.setColorValue(app.color(255, 215, 110));
			_uiModalPointsLbl.setPosition(Math.max(4.0f, (scaleW(modalW) - ptsW) * 0.5f), scaleH(92.0f));
			_uiModalPointsLbl.show();

			_uiModalBtn1.setPosition(scaleW(18.0f), scaleH(128.0f));
			_uiModalBtn1.setSize(Math.max(1, (int) scaleW(114.0f)), Math.max(1, (int) scaleH(24.0f)));
			_uiModalBtn1.setLabel((_modalFocusIdx == 0 ? "> " : "") + "Reiniciar");
			_uiModalBtn1.setColorBackground(_modalFocusIdx == 0 ? app.color(55, 130, 215) : app.color(35, 90, 155));
			_uiModalBtn1.show();

			_uiModalBtn2.setPosition(scaleW(144.0f), scaleH(128.0f));
			_uiModalBtn2.setSize(Math.max(1, (int) scaleW(114.0f)), Math.max(1, (int) scaleH(24.0f)));
			_uiModalBtn2.setLabel((_modalFocusIdx == 1 ? "> " : "") + "Salir");
			_uiModalBtn2.setColorBackground(_modalFocusIdx == 1 ? app.color(55, 165, 110) : app.color(35, 115, 78));
			_uiModalBtn2.show();

			if (_uiModalBtn3 != null) _uiModalBtn3.hide();
			if (_uiModalBtn4 != null) _uiModalBtn4.hide();
		} else if (state == ROTNCore.BattleState.DEFEAT) {
			float modalW = 276.0f;
			float modalH = 152.0f;
			_uiModalGroup.setPosition(scaleW(102.0f), scaleH(59.0f));
			_uiModalGroup.setWidth(Math.max(1, (int) scaleW(modalW)));
			_uiModalGroup.setHeight(Math.max(1, (int) scaleH(modalH)));
			_uiModalBg.setPosition(0, 0);
			_uiModalBg.setSize(Math.max(1, (int) scaleW(modalW)), Math.max(1, (int) scaleH(modalH)));
			_uiModalGroup.show();

			String titleText = "DROP OUT";
			float titleW = measurePhysicalTextWidth(app, titleText, Math.max(8, (int) scaleH(18.0f)));
			_uiModalTitle.setText(titleText);
			_uiModalTitle.setColorValue(app.color(195, 110, 255));
			_uiModalTitle.setPosition(Math.max(4.0f, (scaleW(modalW) - titleW) * 0.5f), scaleH(12.0f));

			_uiModalDesc.hide();
			_uiModalLeftScoreLbl.setPosition(scaleW(18.0f), scaleH(48.0f));
			_uiModalLeftScoreLbl.setText("Score: " + _rhythmCore.getScore());
			_uiModalLeftScoreLbl.show();
			_uiModalLeftComboLbl.setPosition(scaleW(18.0f), scaleH(66.0f));
			_uiModalLeftComboLbl.setText("Best Combo: " + _rhythmCore.getMaxCombo() + "x");
			_uiModalLeftComboLbl.show();

			if (_hasDbBest) {
				_uiModalDivider.setPosition(scaleW(138.0f), scaleH(46.0f));
				_uiModalDivider.setSize(Math.max(1, (int) scaleW(1.0f)), Math.max(1, (int) scaleH(38.0f)));
				_uiModalDivider.show();
				_uiModalRightScoreLbl.setPosition(scaleW(148.0f), scaleH(48.0f));
				_uiModalRightScoreLbl.setText("Max Score: " + _dbMaxScore);
				_uiModalRightScoreLbl.show();
				_uiModalRightComboLbl.setPosition(scaleW(148.0f), scaleH(66.0f));
				_uiModalRightComboLbl.setText("Max Combo: " + _dbMaxCombo + "x");
				_uiModalRightComboLbl.show();
			} else {
				_uiModalDivider.hide();
				_uiModalRightScoreLbl.hide();
				_uiModalRightComboLbl.hide();
			}

			_uiModalPointsLbl.hide();

			_uiModalBtn1.setPosition(scaleW(18.0f), scaleH(114.0f));
			_uiModalBtn1.setSize(Math.max(1, (int) scaleW(114.0f)), Math.max(1, (int) scaleH(24.0f)));
			_uiModalBtn1.setLabel((_modalFocusIdx == 0 ? "> " : "") + "Reiniciar");
			_uiModalBtn1.setColorBackground(_modalFocusIdx == 0 ? app.color(55, 130, 215) : app.color(35, 90, 155));
			_uiModalBtn1.show();

			_uiModalBtn2.setPosition(scaleW(144.0f), scaleH(114.0f));
			_uiModalBtn2.setSize(Math.max(1, (int) scaleW(114.0f)), Math.max(1, (int) scaleH(24.0f)));
			_uiModalBtn2.setLabel((_modalFocusIdx == 1 ? "> " : "") + "Salir");
			_uiModalBtn2.setColorBackground(_modalFocusIdx == 1 ? app.color(150, 58, 80) : app.color(95, 38, 52));
			_uiModalBtn2.show();

			if (_uiModalBtn3 != null) _uiModalBtn3.hide();
			if (_uiModalBtn4 != null) _uiModalBtn4.hide();
		} else if (_rhythmCore.isPaused()) {
			float modalW = 180.0f;
			float modalH = 164.0f;
			_uiModalGroup.setPosition(scaleW((BASE_W - modalW) * 0.5f), scaleH((BASE_H - modalH) * 0.5f));
			_uiModalGroup.setWidth(Math.max(1, (int) scaleW(modalW)));
			_uiModalGroup.setHeight(Math.max(1, (int) scaleH(modalH)));
			_uiModalBg.setPosition(0, 0);
			_uiModalBg.setSize(Math.max(1, (int) scaleW(modalW)), Math.max(1, (int) scaleH(modalH)));
			_uiModalGroup.show();

			String titleText = "PAUSA";
			float titleW = measurePhysicalTextWidth(app, titleText, Math.max(8, (int) scaleH(18.0f)));
			_uiModalTitle.setText(titleText);
			_uiModalTitle.setColorValue(app.color(255));
			_uiModalTitle.setPosition(Math.max(4.0f, (scaleW(modalW) - titleW) * 0.5f), scaleH(12.0f));

			_uiModalDesc.hide();
			_uiModalDivider.hide();
			_uiModalLeftScoreLbl.hide();
			_uiModalLeftComboLbl.hide();
			_uiModalRightScoreLbl.hide();
			_uiModalRightComboLbl.hide();
			_uiModalPointsLbl.hide();

			_uiModalBtn1.setPosition(scaleW(20.0f), scaleH(44.0f));
			_uiModalBtn1.setSize(Math.max(1, (int) scaleW(140.0f)), Math.max(1, (int) scaleH(22.0f)));
			_uiModalBtn1.setLabel((_modalFocusIdx == 0 ? "> " : "") + "Reanudar");
			_uiModalBtn1.setColorBackground(_modalFocusIdx == 0 ? app.color(55, 130, 215) : app.color(35, 90, 155));
			_uiModalBtn1.show();

			_uiModalBtn2.setPosition(scaleW(20.0f), scaleH(72.0f));
			_uiModalBtn2.setSize(Math.max(1, (int) scaleW(140.0f)), Math.max(1, (int) scaleH(22.0f)));
			_uiModalBtn2.setLabel((_modalFocusIdx == 1 ? "> " : "") + "Reiniciar");
			_uiModalBtn2.setColorBackground(_modalFocusIdx == 1 ? app.color(55, 130, 215) : app.color(35, 90, 155));
			_uiModalBtn2.show();

			if (_uiModalBtn3 != null) {
				_uiModalBtn3.setPosition(scaleW(20.0f), scaleH(100.0f));
				_uiModalBtn3.setSize(Math.max(1, (int) scaleW(140.0f)), Math.max(1, (int) scaleH(22.0f)));
				_uiModalBtn3.setLabel((_modalFocusIdx == 2 ? "> " : "") + "Configuracion");
				_uiModalBtn3.setColorBackground(_modalFocusIdx == 2 ? app.color(75, 118, 195) : app.color(45, 72, 118));
				_uiModalBtn3.show();
			}
			if (_uiModalBtn4 != null) {
				_uiModalBtn4.setPosition(scaleW(20.0f), scaleH(128.0f));
				_uiModalBtn4.setSize(Math.max(1, (int) scaleW(140.0f)), Math.max(1, (int) scaleH(22.0f)));
				_uiModalBtn4.setLabel((_modalFocusIdx == 3 ? "> " : "") + "Salir");
				_uiModalBtn4.setColorBackground(_modalFocusIdx == 3 ? app.color(150, 58, 80) : app.color(95, 38, 52));
				_uiModalBtn4.show();
			}
		} else {
			_uiModalGroup.hide();
		}
	}

	private void syncInGameSettingsUI(PApplet app) {
		if (_uiSettingsGroup == null || app == null) return;

		if (_uiSetBgmLbl != null) {
			boolean f = (_inGameSettingsFocus == INGAME_SET_BGM);
			_uiSetBgmLbl.setText((f ? "> " : "") + "BGM: " + Math.round(Config.BGMVolume * 100.0f) + "%" + (f ? "  [< / >]" : ""));
			_uiSetBgmLbl.setColorValue(f ? app.color(255, 225, 110) : app.color(220, 235, 255));
		}
		if (_uiSetBgmBarFill != null) {
			int w = Math.max(1, Math.round(scaleW(138) * Config.BGMVolume));
			_uiSetBgmBarFill.setSize(w, Math.max(1, (int) scaleH(12)));
			_uiSetBgmBarFill.setColorBackground((_inGameSettingsFocus == INGAME_SET_BGM) ? app.color(130, 225, 255) : app.color(80, 195, 255));
		}

		if (_uiSetSfxLbl != null) {
			boolean f = (_inGameSettingsFocus == INGAME_SET_SFX);
			_uiSetSfxLbl.setText((f ? "> " : "") + "SFX: " + Math.round(Config.SFXVolume * 100.0f) + "%" + (f ? "  [< / >]" : ""));
			_uiSetSfxLbl.setColorValue(f ? app.color(255, 225, 110) : app.color(220, 235, 255));
		}
		if (_uiSetSfxBarFill != null) {
			int w = Math.max(1, Math.round(scaleW(138) * Config.SFXVolume));
			_uiSetSfxBarFill.setSize(w, Math.max(1, (int) scaleH(12)));
			_uiSetSfxBarFill.setColorBackground((_inGameSettingsFocus == INGAME_SET_SFX) ? app.color(145, 255, 195) : app.color(95, 235, 165));
		}

		if (_uiSetMuteBtn != null) {
			boolean f = (_inGameSettingsFocus == INGAME_SET_MUTE);
			_uiSetMuteBtn.setLabel((f ? "> " : "") + "MUTE GENERAL: " + (Config.isMuted ? "ON" : "OFF"));
			_uiSetMuteBtn.setColorBackground(
				f ? (Config.isMuted ? app.color(190, 65, 80) : app.color(55, 110, 195))
				  : (Config.isMuted ? app.color(145, 45, 58) : app.color(32, 42, 66))
			);
		}
		if (_uiSetUnfocusMuteBtn != null) {
			boolean f = (_inGameSettingsFocus == INGAME_SET_UNFOCUS_MUTE);
			_uiSetUnfocusMuteBtn.setLabel((f ? "> " : "") + "MUTE SIN FOCO: " + (Config.muteOnUnfocus ? "ON" : "OFF"));
			_uiSetUnfocusMuteBtn.setColorBackground(
				f ? (Config.muteOnUnfocus ? app.color(190, 65, 80) : app.color(55, 110, 195))
				  : (Config.muteOnUnfocus ? app.color(145, 45, 58) : app.color(32, 42, 66))
			);
		}
		if (_uiSetResetBtn != null) {
			boolean f = (_inGameSettingsFocus == INGAME_SET_RESET);
			_uiSetResetBtn.setLabel((f ? "> " : "") + "RESTAURAR TECLAS");
			_uiSetResetBtn.setColorBackground(f ? app.color(130, 102, 55) : app.color(65, 52, 32));
		}
		if (_uiSetBackBtn != null) {
			boolean f = (_inGameSettingsFocus == INGAME_SET_BACK);
			_uiSetBackBtn.setLabel((f ? "> " : "") + "LISTO / VOLVER");
			_uiSetBackBtn.setColorBackground(f ? app.color(55, 165, 110) : app.color(35, 115, 78));
		}

		for (int i = 0; i < LANE_COUNT; i++) {
			Button b = _uiSetRhythmBtns[i];
			if (b != null) {
				boolean listening = (_rebindTarget == i);
				boolean f = (_inGameSettingsFocus == INGAME_SET_RHYTHM_START + i);
				b.setLabel((f ? "> " : "") + "C" + (i + 1) + ": " + (listening ? "[ ? ]" : Config.getKeyLabel(Config.RhythmButtons[i])));
				b.setColorBackground(
					listening ? app.color(175, 115, 25) : (f ? app.color(55, 110, 195) : app.color(28, 34, 52))
				);
			}
			Button ba = _uiSetRhythmAltBtns[i];
			if (ba != null) {
				boolean listening = (_rebindTarget == LANE_COUNT + i);
				boolean f = (_inGameSettingsFocus == INGAME_SET_RHYTHM_START + LANE_COUNT + i);
				ba.setLabel((f ? "> " : "") + "A" + (i + 1) + ": " + (listening ? "[ ? ]" : Config.getKeyLabel(Config.RhythmAltButtons[i])));
				ba.setColorBackground(
					listening ? app.color(175, 115, 25) : (f ? app.color(55, 110, 195) : app.color(28, 34, 52))
				);
			}
		}
	}

	// =========================================================================
	// 5. Bucle de actualización (update: pre-inicio, reproducción de canción,
	//    fade-out de victoria, entrada de teclado y disparo de partículas/efectos)
	// =========================================================================

	@Override
	public void update(float dt) {
		super.update(dt);
		GameState.updateNotification(dt);

		if (_rhythmCore == null) return;

		if (!_rhythmCore.isPaused()) {
			_bgAnimTime += dt;
		}
		if (_multAnimTimer > 0.0f) {
			_multAnimTimer = Math.max(0.0f, _multAnimTimer - dt);
		}
		int curMult = _rhythmCore.getScoreMultiplier();
		if (curMult > _prevMultiplier) {
			_multAnimTimer = 0.42f;
		}
		_prevMultiplier = curMult;

		// 2. Fin del encuentro (Victoria con fade-out de 3s o Derrota inmediata)
		ROTNCore.BattleState state = _rhythmCore.getBattleState();
		if (state != ROTNCore.BattleState.PLAYING) {
			updatePostBattleAudioAndModal(state, dt);
			syncCombatUI();
			return;
		}

		// 3. Cuenta regresiva de pre-inicio (3..2..1) antes de lanzar la pista BGM
		if (_introCountdown > 0.0f) {
			updateIntroCountdown(dt);
			syncCombatUI();
			return;
		}

		// 3b. Cuenta regresiva tras salir de pausa (retroceso 2s + espera 3s antes de reanudar)
		if (_resumeCountdown > 0.0f) {
			updateResumeCountdown(dt);
			syncCombatUI();
			return;
		}

		// 3c. Si está abierto el submenú de Configuración en Partida, retener ESC dentro del submenú
		if (_rhythmCore.isPaused() && _inGameSettingsOpen) {
			resolveCurrentSongTimeMs(dt);
			handleInGameSettingsInput(dt);
			syncCombatUI();
			return;
		}

		// 4. Sincronización del reloj de canción y actualización del núcleo rítmico
		boolean wasPausedBefore = _rhythmCore.isPaused();
		int currentSongTimeMs = resolveCurrentSongTimeMs(dt);
		if (!wasPausedBefore) {
			_lastSongTimeMs = currentSongTimeMs;
		}
		_rhythmCore.update(currentSongTimeMs, dt);
		boolean nowPaused = _rhythmCore.isPaused();

		if (!wasPausedBefore && nowPaused) {
			_pauseSongTimeMs = _lastSongTimeMs;
		} else if (wasPausedBefore && !nowPaused) {
			_modalWasOpen = false;
			_inGameSettingsOpen = false;
			_rebindTarget = -1;
			_settingsUpHold.reset();
			_settingsDownHold.reset();
			beginResumeCountdown();
			syncCombatUI();
			return;
		}

		// 5. Navegación en modal de pausa o efectos activos de combate (partículas y SFX)
		if (nowPaused) {
			if (!_modalWasOpen) {
				_modalFocusIdx = 0;
				_modalWasOpen = true;
			}
			if (!_inGameSettingsOpen) {
				handlePauseModalInput();
			}
		} else {
			_modalWasOpen = false;
			_inGameSettingsOpen = false;
			_rebindTarget = -1;
			updateActiveGameplayEffects(dt);
		}

		syncCombatUI();
	}

	/**
	 * Gestiona los 3 segundos de espera tras cerrar el menú de pausa con la pista retrocedida.
	 */
	private void updateResumeCountdown(float dt) {
		if (
			KBInput.wasKeyJustPressed(Config.UIButtons[Config.CANCEL]) ||
			KBInput.wasKeyJustPressed(Config.UIAltButtons[Config.CANCEL])
		) {
			_resumeCountdown = 0.0f;
			_pauseSongTimeMs = _resumeTargetMs;
			_rhythmCore.togglePause();
			_modalFocusIdx = 0;
			_modalWasOpen = true;
			return;
		}

		_resumeCountdown -= dt;
		_particles.update(dt);
		if (_resumeCountdown <= 0.0f) {
			_resumeCountdown = 0.0f;
			_songFallbackTimerMs = _resumeTargetMs;
			if (_bgm != null && _bgm.isLoaded()) {
				_bgm.setPausedPositionMs(_resumeTargetMs);
				_bgmPausedByScene = false;
				Audio.resumeBGM();
				Audio.syncTrackVolume(_bgm);
			}
		}
	}

	/**
	 * Gestiona el fade-out progresivo de 3 segundos de la música al ganar, la pausa
	 * de audio al perder y la entrada del modal de resultado.
	 */
	private void updatePostBattleAudioAndModal(ROTNCore.BattleState state, float dt) {
		if (state == ROTNCore.BattleState.VICTORY) {
			if (_bgm != null && _bgm.isLoaded() && _bgm.isPlaying()) {
				if (_bgmFadeOutTimer < 0.0f) {
					_bgmFadeOutTimer = BGM_FADE_OUT_DURATION;
				}
				_bgmFadeOutTimer = Math.max(0.0f, _bgmFadeOutTimer - dt);
				float fade = _bgmFadeOutTimer / BGM_FADE_OUT_DURATION;
				_bgm.setVolume(Audio.getBGMVolume() * fade);
				if (_bgmFadeOutTimer <= 0.0f) {
					Audio.pauseBGM();
					_bgmPausedByScene = true;
				}
			}
		} else if (_bgm != null && _bgm.isPlaying()) {
			Audio.pauseBGM();
			_bgmPausedByScene = true;
		}

		if (!_modalWasOpen) {
			_modalFocusIdx = (state == ROTNCore.BattleState.VICTORY) ? 1 : 0;
			_modalWasOpen = true;
		}
		handleEndBattleInput(state);
	}

	/**
	 * Actualiza la cuenta regresiva inicial de 3 segundos (permitiendo pausar antes de que inicie el audio).
	 */
	private void updateIntroCountdown(float dt) {
		if (_rhythmCore.isPaused() && _inGameSettingsOpen) {
			handleInGameSettingsInput(dt);
			return;
		}

		if (
			KBInput.wasKeyJustPressed(Config.UIButtons[Config.CANCEL]) ||
			KBInput.wasKeyJustPressed(Config.UIAltButtons[Config.CANCEL])
		) {
			_rhythmCore.togglePause();
		}

		if (_rhythmCore.isPaused()) {
			if (!_modalWasOpen) {
				_modalFocusIdx = 0;
				_modalWasOpen = true;
			}
			if (!_inGameSettingsOpen) {
				handlePauseModalInput();
			}
		} else {
			_modalWasOpen = false;
			_inGameSettingsOpen = false;
			_rebindTarget = -1;
			_introCountdown -= dt;
			if (_introCountdown <= 0.0f) {
				_introCountdown = 0.0f;
				if (_bgm != null && !_bgmStarted) {
					_bgmStarted = true;
					Audio.playBGM(_bgm, 0, false);
				}
			}
			_particles.update(dt);
		}
	}

	/**
	 * Obtiene la posición actual de reproducción en milisegundos desde la pista BGM
	 * (o desde el reloj de respaldo si no hay audio cargado), pausando/reanudando según el estado.
	 */
	private int resolveCurrentSongTimeMs(float dt) {
		if (_bgm != null && _bgm.isLoaded()) {
			if (_rhythmCore.isPaused()) {
				if (_bgm.isPlaying()) {
					Audio.pauseBGM();
					_bgmPausedByScene = true;
				}
			} else if (_bgmPausedByScene) {
				_bgmPausedByScene = false;
				Audio.resumeBGM();
			}
			return _bgm.getPositionMs();
		}

		if (!_rhythmCore.isPaused()) {
			_songFallbackTimerMs += Math.round(dt * 1000.0f);
		}
		return _songFallbackTimerMs;
	}

	/**
	 * Actualiza partículas, carteles flotantes de juicio, SFX de pulsación por carril
	 * y emisión continua de chispas mientras se mantiene una Hold Note.
	 */
	private void updateActiveGameplayEffects(float dt) {
		_particles.update(dt);

		int curHp = _rhythmCore.getPlayerHp();
		if (curHp > _lastPlayerHp) {
			int healed = curHp - _lastPlayerHp;
			FloatingRating healNotice = new FloatingRating();
			healNotice.text = "+" + healed + " HP";
			healNotice.x = 68.0f;
			healNotice.y = 32.0f;
			healNotice.vy = -16.0f;
			healNotice.life = 0.75f;
			healNotice.maxLife = 0.75f;
			healNotice.color = 0xFF55FF99;
			_floatingRatings.add(healNotice);
			_particles.emitBurst(21.0f, 178.0f, 10, 18.0f, 48.0f, 1.8f, 3.2f, 0.20f, 0.40f, 12.0f, 0xFF55FF99);
		}
		_lastPlayerHp = curHp;

		for (int i = _floatingRatings.size() - 1; i >= 0; i--) {
			FloatingRating fr = _floatingRatings.get(i);
			fr.life -= dt;
			if (fr.life <= 0.0f) {
				_floatingRatings.remove(i);
				continue;
			}
			fr.y += fr.vy * dt;
		}

		int lockedLane = _rhythmCore.getLockedLane();
		for (int lane = 0; lane < LANE_COUNT; lane++) {
			if (lane != lockedLane && Config.wasRhythmKeyJustPressed(lane)) {
				Audio.playSFX(_hitSfx);
			}
			if (_rhythmCore.isHoldingLane(lane)) {
				_holdParticleTimers[lane] -= dt;
				if (_holdParticleTimers[lane] <= 0.0f) {
					_holdParticleTimers[lane] = HOLD_PARTICLE_INTERVAL_SEC;
					float xi = laneToXi(lane);
					float cx = projectX(xi, 1.0f) + (float) (Math.random() * 8.0 - 4.0);
					float cy = projectY(xi, 1.0f, 1.0f) + PRESS_SINK_PX;
					_particles.emit(
						cx,
						cy,
						(float) (Math.random() * 36.0 - 18.0),
						-28.0f - (float) (Math.random() * 26.0),
						2.0f + (float) (Math.random() * 1.8),
						0.22f + (float) (Math.random() * 0.14),
						45.0f,
						0xFFFFFFFF
					);
				}
			} else {
				_holdParticleTimers[lane] = 0.0f;
			}
		}
	}

	@Override
	public void keyEvent(KeyEvent e) {
		if (_inGameSettingsOpen && _rebindTarget >= 0 && e.getAction() == KeyEvent.PRESS) {
			int code = e.getKeyCode();
			if (code == 27) { // ESC cancela la captura de tecla sin cerrar el submenú
				_rebindTarget = -1;
				_ignoreSettingsNavOnce = true;
				return;
			}
			int normalized = KBInput.normalizeKey(code);
			if (normalized > 0 && normalized < 256) {
				if (_rebindTarget < LANE_COUNT) {
					Config.RhythmButtons[_rebindTarget] = normalized;
				} else {
					int altIdx = _rebindTarget - LANE_COUNT;
					if (altIdx < Config.RhythmAltButtons.length) {
						Config.RhythmAltButtons[altIdx] = normalized;
					}
				}
				Config.saveConfig(".config");
				_rebindTarget = -1;
				_ignoreSettingsNavOnce = true;
			}
		}
	}

	private void handlePauseModalInput() {
		if (Config.wasUIKeyJustPressed(Config.UP)) {
			_modalFocusIdx = (_modalFocusIdx - 1 + 4) % 4;
		} else if (Config.wasUIKeyJustPressed(Config.DOWN)) {
			_modalFocusIdx = (_modalFocusIdx + 1) % 4;
		} else if (Config.wasUIKeyJustPressed(Config.INTERACT)) {
			switch (_modalFocusIdx) {
				case 0 -> unpauseBattle();
				case 1 -> restartBattleScene();
				case 2 -> openInGameSettings();
				case 3 -> returnToWorld();
			}
		}
	}

	private void handleInGameSettingsInput(float dt) {
		if (_ignoreSettingsNavOnce) {
			_ignoreSettingsNavOnce = false;
			return;
		}
		if (_rebindTarget >= 0) return;

		boolean up = _settingsUpHold.poll(Config.wasUIKeyJustPressed(Config.UP), Config.isUIKeyDown(Config.UP), dt);
		boolean down = _settingsDownHold.poll(Config.wasUIKeyJustPressed(Config.DOWN), Config.isUIKeyDown(Config.DOWN), dt);
		boolean left = Config.wasUIKeyJustPressed(Config.LEFT);
		boolean right = Config.wasUIKeyJustPressed(Config.RIGHT);
		boolean accept = Config.wasUIKeyJustPressed(Config.INTERACT);
		boolean back = Config.wasUIKeyJustPressed(Config.CANCEL);

		if (back) {
			closeInGameSettings();
			return;
		}

		// Columna izquierda (0..5): BGM, SFX, Mute, Mute sin foco, Restaurar, Volver
		if (_inGameSettingsFocus <= INGAME_SET_BACK) {
			if (up) {
				_inGameSettingsFocus = (_inGameSettingsFocus - 1 + 6) % 6;
			} else if (down) {
				_inGameSettingsFocus = (_inGameSettingsFocus + 1) % 6;
			} else {
				switch (_inGameSettingsFocus) {
					case INGAME_SET_BGM -> {
						if (left) adjustInGameBgmVolume(-0.1f);
						else if (right || accept) adjustInGameBgmVolume(0.1f);
					}
					case INGAME_SET_SFX -> {
						if (left) adjustInGameSfxVolume(-0.1f);
						else if (right || accept) adjustInGameSfxVolume(0.1f);
					}
					case INGAME_SET_MUTE -> {
						if (right) _inGameSettingsFocus = INGAME_SET_RHYTHM_START + 2;
						else if (accept) toggleInGameMute();
					}
					case INGAME_SET_UNFOCUS_MUTE -> {
						if (right) _inGameSettingsFocus = INGAME_SET_RHYTHM_START + 3;
						else if (accept) toggleInGameMuteOnUnfocus();
					}
					case INGAME_SET_RESET -> {
						if (right) _inGameSettingsFocus = INGAME_SET_RHYTHM_START + 4;
						else if (accept) {
							Config.resetDefaultControls();
							Config.saveConfig(".config");
						}
					}
					case INGAME_SET_BACK -> {
						if (right) _inGameSettingsFocus = INGAME_SET_RHYTHM_START + 5;
						else if (accept) closeInGameSettings();
					}
				}
			}
			return;
		}

		// Columna derecha (6..17): Carriles 6K (Primarios + Alternativos) en grilla 6x2
		int row = 0, col = 0;
		for (int r = 0; r < INGAME_SET_RIGHT_GRID.length; r++) {
			for (int c = 0; c < 2; c++) {
				if (INGAME_SET_RIGHT_GRID[r][c] == _inGameSettingsFocus) {
					row = r;
					col = c;
				}
			}
		}

		if (accept) {
			_rebindTarget = _inGameSettingsFocus - INGAME_SET_RHYTHM_START;
		} else if (left) {
			_inGameSettingsFocus = (col == 1) ? INGAME_SET_RIGHT_GRID[row][0] : row;
		} else if (right && col == 0) {
			_inGameSettingsFocus = INGAME_SET_RIGHT_GRID[row][1];
		} else if (up) {
			_inGameSettingsFocus = INGAME_SET_RIGHT_GRID[(row - 1 + INGAME_SET_RIGHT_GRID.length) % INGAME_SET_RIGHT_GRID.length][col];
		} else if (down) {
			_inGameSettingsFocus = INGAME_SET_RIGHT_GRID[(row + 1) % INGAME_SET_RIGHT_GRID.length][col];
		}
	}

	private void handleEndBattleInput(ROTNCore.BattleState state) {
		if ((state == ROTNCore.BattleState.VICTORY || state == ROTNCore.BattleState.DEFEAT) && !_pointsGranted) {
			_pointsGranted = true;
			float diffRating = (_chartVariant != null)
				? SongPackLoader.computeDifficultyRating(_chartVariant.noteCount, _chartVariant.bpm, _chartVariant.durationSec, GameState.songSpeedMultiplier)
				: 4.5f;
			int totalNotes = (_rhythmCore.getNotes() != null) ? _rhythmCore.getNotes().length : 0;
			_earnedShopPoints = GameState.computeSongRewardPoints(
				diffRating,
				totalNotes,
				_rhythmCore.getPerfectCount(),
				_rhythmCore.getGoodCount(),
				_rhythmCore.getSafeCount(),
				_rhythmCore.getMissCount(),
				_rhythmCore.getMaxCombo(),
				_rhythmCore.getAccuracyPercent(),
				state == ROTNCore.BattleState.VICTORY
			);
			GameState.addShopPoints(_earnedShopPoints);
			GameState.saveGame();
		}

		if ((state == ROTNCore.BattleState.VICTORY || state == ROTNCore.BattleState.DEFEAT) && !_songAttemptRecorded) {
			_songAttemptRecorded = true;
			String diffName = (_chartVariant != null && !_chartVariant.difficulty.isEmpty())
				? _chartVariant.difficulty
				: (_customSong != null && !_customSong.dificultad.isEmpty() ? _customSong.dificultad : "6K");
			GameState.recordSongAttempt(
				_trackId,
				_trackTitle,
				_trackSubtitle,
				diffName,
				_rhythmCore.getScore(),
				_rhythmCore.getMaxCombo(),
				_rhythmCore.getPerfectCount(),
				_rhythmCore.getGoodCount(),
				_rhythmCore.getSafeCount(),
				_rhythmCore.getMissCount(),
				state == ROTNCore.BattleState.VICTORY
			);
			if (_customSong != null && !_customAttemptRecorded) {
				_customAttemptRecorded = true;
				int prevBest = _customSong.puntajeMasAlto;
				int newScore = _rhythmCore.getScore();
				_customIsNewRecord = (newScore > prevBest) || (_customSong.vecesJugada == 0 && newScore >= 0);
				CustomSongRepository.recordAttempt(
					_customSong.idCancion,
					newScore,
					_rhythmCore.getMaxCombo(),
					_rhythmCore.getAccuracyPercent(),
					GameState.getEquippedPowerUps()
				);
			}
		}

		if ((state == ROTNCore.BattleState.VICTORY || state == ROTNCore.BattleState.DEFEAT) && !_dbBestChecked) {
			_dbBestChecked = true;
			int[] rec = SaveRepository.getBestSongRecord(GameState.activeSaveSlot, _trackId, _trackTitle);
			if (rec != null) {
				_hasDbBest = true;
				_dbMaxScore = rec[0];
				_dbMaxCombo = rec[1];
			} else if (_customSong != null) {
				CustomSongRepository.CustomSongSummary fresh = CustomSongRepository.getById(_customSong.idCancion);
				if (fresh != null && (fresh.vecesJugada > 0 || fresh.puntajeMasAlto > 0 || fresh.comboMasAlto > 0)) {
					_hasDbBest = true;
					_dbMaxScore = fresh.puntajeMasAlto;
					_dbMaxCombo = fresh.comboMasAlto;
				}
			}
		}

		if (
			(state == ROTNCore.BattleState.VICTORY || state == ROTNCore.BattleState.DEFEAT) && (
				Config.wasUIKeyJustPressed(Config.LEFT) ||
				Config.wasUIKeyJustPressed(Config.RIGHT) ||
				Config.wasUIKeyJustPressed(Config.UP) ||
				Config.wasUIKeyJustPressed(Config.DOWN)
			)
		) {
			_modalFocusIdx = (_modalFocusIdx == 0) ? 1 : 0;
		}

		boolean accept = Config.wasUIKeyJustPressed(Config.INTERACT);
		boolean retry = KBInput.wasKeyJustPressed('R');
		boolean back = Config.wasUIKeyJustPressed(Config.CANCEL);

		if (state == ROTNCore.BattleState.VICTORY || state == ROTNCore.BattleState.DEFEAT) {
			if (retry || (accept && _modalFocusIdx == 0)) {
				restartBattleScene();
			} else if (back || (accept && _modalFocusIdx == 1)) {
				returnToWorld();
			}
		}
	}

	// =========================================================================
	// 6. Proyección en perspectiva 3D y renderizado modular (draw)
	// =========================================================================

	private static float laneToXi(int lane) {
		return -1.0f + (lane + 0.5f) * (2.0f / 6.0f);
	}

	private static float scaleFromT(float t) {
		float z = Math.max(0.45f, Z_FAR - (Z_FAR - Z_HIT) * t);
		return 1.0f / z;
	}

	private static float uFromScale(float s) {
		return (s - S_FAR) / (1.0f - S_FAR);
	}

	private static float projectX(float xi, float s) {
		return CENTER_X + xi * HALF_W_HIT * s;
	}

	private static float projectY(float xi, float u, float s) {
		return Y_FAR + u * (Y_HIT - Y_FAR) - ARC_RISE * s * (xi * xi);
	}

	private static float arcTangentAngle(float xi, float s) {
		// dy/dxi = -2 * ARC_RISE * s * xi, dx/dxi = HALF_W_HIT * s => atan2(-2*ARC_RISE*xi, HALF_W_HIT)
		return (float) Math.atan2(-2.0f * ARC_RISE * xi, HALF_W_HIT);
	}

	private static float tiltRatioFromU(float u) {
		// Elipses más planas cerca del horizonte y algo más abiertas cerca de la cámara
		return PApplet.constrain(0.36f + u * 0.18f, 0.34f, 0.62f);
	}

	private static float horizonYAtX(float x) {
		float nx = -1.0f + 2.0f * (x / BASE_W);
		return Y_FAR - 4.0f + 18.0f * (nx * nx);
	}

	private float[] projectWorldPoint(float xi, float t) {
		float s = scaleFromT(t);
		float u = uFromScale(s);
		float lx = projectX(xi, s);
		float hy = horizonYAtX(lx);
		float ly = hy + u * (Y_HIT - Y_FAR + 14.0f);
		return new float[] { lx, ly, s, u };
	}

	private float getApproachTimeMs() {
		return BASE_APPROACH_TIME_MS / Math.max(0.08f, _scrollSpeed / DEFAULT_SCROLL_SPEED);
	}

	/**
	 * Calcula la posición actual dentro del compás según los TimingPoints del beatmap (.osu).
	 * Retorna {@code { beatPos, beatPhase (0..1), beatInMeasure (0..3), beatLengthMs, tpTime }}.
	 */
	private float[] computeBeatState(int songTimeMs) {
		int tpTime = 0;
		float beatLength = 500.0f; // 120 BPM por defecto

		if (_timingPoints != null && _timingPoints.length > 0) {
			boolean foundFirst = false;
			for (long tp : _timingPoints) {
				if (!TimingPointPacker.isUninherited(tp)) continue;
				float bl = TimingPointPacker.getBeatLength(tp);
				if (bl <= 0.0f) continue;
				int t = TimingPointPacker.getTime(tp);
				if (!foundFirst) {
					tpTime = t;
					beatLength = bl;
					foundFirst = true;
				}
				if (t <= songTimeMs) {
					tpTime = t;
					beatLength = bl;
				} else {
					break;
				}
			}
		}

		float beatPos = (songTimeMs - tpTime) / beatLength;
		float beatPhase = beatPos - (float) Math.floor(beatPos);
		int beatInMeasure = ((int) Math.floor(beatPos) % 4 + 4) % 4;
		return new float[] { beatPos, beatPhase, beatInMeasure, beatLength, tpTime };
	}

	@Override
	public void draw(PGraphics g) {
		g.pushStyle();

		int songTimeMs;
		if (_introCountdown > 0.0f) {
			songTimeMs = -Math.round(_introCountdown * 1000.0f);
		} else if (_resumeCountdown > 0.0f) {
			songTimeMs = _resumeTargetMs;
		} else {
			songTimeMs = (_bgm != null && _bgm.isLoaded()) ? _bgm.getPositionMs() : _songFallbackTimerMs;
		}

		drawArenaBackground(g, _bgAnimTime, songTimeMs);

		if (_rhythmCore != null) {
			drawPerspectiveHighway(g, songTimeMs);
			drawPerspectiveReceptors(g);
			drawPerspectiveNotes(g, songTimeMs);
			_particles.draw(g);
			drawFloatingRatings(g);
		}

		g.popStyle();
	}

	/**
	 * Renderiza el cielo oscuro inicial y el atardecer/amanecer progresivo sincronizado
	 * con la duración total de la canción: los círculos concéntricos suben detrás de la
	 * curvatura del planeta (siempre con borde visible) hasta que al final asoma apenas
	 * un borde muy pequeño del sol amarillo.
	 */
	private void drawArenaBackground(PGraphics g, float bgTimeSec, int songTimeMs) {
		float songDurationMs = (_rhythmCore != null && _rhythmCore.getLastNoteEndTimeMs() > 0L)
			? (float) _rhythmCore.getLastNoteEndTimeMs()
			: 60000.0f;
		float skyProgress = PApplet.constrain(Math.max(0, songTimeMs) / Math.max(1000.0f, songDurationMs), 0.0f, 1.0f);

		// 1. Cielo oscuro inicial que se entibia levemente conforme avanza el atardecer
		g.noStroke();
		g.background(
			Math.round(PApplet.lerp(6.0f, 18.0f, skyProgress)),
			Math.round(PApplet.lerp(7.0f, 10.0f, skyProgress)),
			Math.round(PApplet.lerp(15.0f, 24.0f, skyProgress))
		);
		for (int band = 0; band < 6; band++) {
			float y0 = band * 18.0f;
			float bandFactor = band / 5.0f;
			int r = Math.round(PApplet.lerp(9.0f + band * 2.0f, 26.0f + band * 6.0f, skyProgress * bandFactor));
			int gr = Math.round(PApplet.lerp(10.0f + band * 1.5f, 13.0f + band * 2.5f, skyProgress * bandFactor));
			int b = Math.round(PApplet.lerp(20.0f + band * 2.5f, 32.0f + band * 2.0f, skyProgress * bandFactor));
			g.fill(r, gr, b, 175);
			g.rect(0, y0, BASE_W, 18.0f);
		}

		// 2. Círculos concéntricos del atardecer subiendo a velocidad constante (80px / duración de la canción)
		//    Inicio (0%): ocultos debajo del horizonte (y = 88). Final (100%): atardecer completo y ~4px de sol amarillo.
		float sunCenterY = PApplet.lerp(186.0f, 106.0f, skyProgress);

		// Capas concéntricas de atmósfera (de púrpura-vino exterior a ámbar interior)
		g.fill(118, 22, 58, 38);
		g.ellipse(CENTER_X, sunCenterY, 390.0f, 188.0f);

		g.fill(168, 34, 48, 46);
		g.ellipse(CENTER_X, sunCenterY, 316.0f, 152.0f);

		g.fill(206, 62, 34, 54);
		g.ellipse(CENTER_X, sunCenterY, 244.0f, 120.0f);

		g.fill(236, 110, 34, 66);
		g.ellipse(CENTER_X, sunCenterY, 180.0f, 92.0f);

		g.fill(250, 165, 48, 86);
		g.ellipse(CENTER_X, sunCenterY, 124.0f, 68.0f);

		g.fill(255, 206, 78, 118);
		g.ellipse(CENTER_X, sunCenterY, 76.0f, 52.0f);

		// Sol amarillo detrás del horizonte: con ry=22 y sunCenterY final=106, solo asoman ~4px al terminar la canción
		g.fill(255, 226, 88, 235);
		g.ellipse(CENTER_X, sunCenterY, 48.0f, 44.0f);
		g.fill(255, 246, 170, 240);
		g.ellipse(CENTER_X, sunCenterY, 34.0f, 32.0f);

		// 3. Silueta curva de la Tierra delante del atardecer y el sol
		g.fill(8, 9, 15);
		g.beginShape();
		g.vertex(0, BASE_H);
		g.vertex(0, Y_FAR + 14.0f);
		for (int i = 0; i <= 24; i++) {
			float hx = i * (BASE_W / 24.0f);
			g.vertex(hx, horizonYAtX(hx));
		}
		g.vertex(BASE_W, BASE_H);
		g.endShape(PConstants.CLOSE);

		// 4. Borde atmosférico visible del planeta (frío nocturno al inicio -> cálido dorado al final)
		g.noFill();
		g.strokeWeight(2.0f);
		g.stroke(
			Math.round(PApplet.lerp(56.0f, 225.0f, skyProgress)),
			Math.round(PApplet.lerp(70.0f, 115.0f, skyProgress)),
			Math.round(PApplet.lerp(116.0f, 62.0f, skyProgress)),
			115
		);
		g.beginShape();
		for (int i = 0; i <= 24; i++) {
			float hx = i * (BASE_W / 24.0f);
			g.vertex(hx, horizonYAtX(hx));
		}
		g.endShape();

		g.strokeWeight(1.0f);
		g.stroke(
			Math.round(PApplet.lerp(115.0f, 255.0f, skyProgress)),
			Math.round(PApplet.lerp(132.0f, 200.0f, skyProgress)),
			Math.round(PApplet.lerp(178.0f, 112.0f, skyProgress)),
			200
		);
		g.beginShape();
		for (int i = 0; i <= 24; i++) {
			float hx = i * (BASE_W / 24.0f);
			g.vertex(hx, horizonYAtX(hx));
		}
		g.endShape();

		// 5. Clusters de luces de ciudad y caminos luminosos rotando sobre el planeta
		drawPlanetCityLights(g, bgTimeSec);
	}

	private void drawPlanetCityLights(PGraphics g, float bgTimeSec) {
		g.noStroke();
		float rotProgress = bgTimeSec * 0.042f;

		// Caminos de luces tenues entre conjuntos distantes
		for (int rp = 0; rp < CITY_ROAD_PAIRS.length; rp++) {
			int ia = CITY_ROAD_PAIRS[rp][0];
			int ib = CITY_ROAD_PAIRS[rp][1];
			float xiA = CITY_LIGHT_CLUSTERS[ia][0];
			float xiB = CITY_LIGHT_CLUSTERS[ib][0];
			float phaseA = CITY_LIGHT_CLUSTERS[ia][1];
			float phaseB = CITY_LIGHT_CLUSTERS[ib][1];
			final int roadDots = 14;
			for (int d = 2; d <= roadDots - 2; d++) {
				float frac = d / (float) roadDots;
				float tPhase = (PApplet.lerp(phaseA, phaseB, frac) + rotProgress) % 1.0f;
				float tWorld = tPhase * 1.15f;
				if (tWorld < 0.02f || tWorld > 1.15f) continue;

				float xiRoad = PApplet.lerp(xiA, xiB, frac) + (float) Math.sin(frac * Math.PI) * (xiA < 0 ? -0.14f : 0.14f);
				float[] pt = projectWorldPoint(xiRoad, tWorld);
				float lx = pt[0], ly = pt[1];
				if (lx < 1.0f || lx >= BASE_W - 1.0f || ly < horizonYAtX(lx) + 1.0f || ly >= BASE_H - 1.0f) continue;

				float fade = PApplet.constrain(tWorld / 0.16f, 0.0f, 1.0f);
				int alpha = Math.round((85 + 35 * (float) Math.sin(bgTimeSec * 2.0f + d)) * fade);
				g.fill(255, 210, 90, alpha);
				g.rect(Math.round(lx), Math.round(ly), 1, 1);
			}
		}

		// Grupos densos de luces de ciudad (1x1 px en perspectiva real)
		for (int c = 0; c < CITY_LIGHT_CLUSTERS.length; c++) {
			float xiCenter = CITY_LIGHT_CLUSTERS[c][0];
			float tCluster = (CITY_LIGHT_CLUSTERS[c][1] + rotProgress) % 1.0f;
			float tWorld = tCluster * 1.15f;
			float horizonFade = PApplet.constrain(tWorld / 0.14f, 0.0f, 1.0f);
			int count = (int) CITY_LIGHT_CLUSTERS[c][2];

			for (int k = 0; k < count; k++) {
				int hash = c * 37 + k * 19;
				float offXi = (((hash * 23) % 29) - 14) * 0.017f;
				float offT  = (((hash * 13) % 17) - 8) * 0.0055f;

				float lt = tWorld + offT;
				if (lt < 0.0f || lt > 1.15f) continue;

				float lxi = xiCenter + offXi;
				float[] pt = projectWorldPoint(lxi, lt);
				float lx = pt[0], ly = pt[1];
				if (lx < 1.0f || lx >= BASE_W - 1.0f || ly < 0.0f || ly >= BASE_H - 1.0f) continue;
				if (ly < horizonYAtX(lx) + 1.0f) continue;

				float twinkle = 0.65f + 0.35f * (float) Math.sin(bgTimeSec * (2.2f + (k % 4) * 0.5f) + hash);
				int alpha = Math.round((130 + 120 * twinkle) * horizonFade);
				g.fill(255, 222 + (k % 3) * 10, 95, alpha);
				g.rect(Math.round(lx), Math.round(ly), 1, 1);
			}
		}
	}

	/**
	 * Renderiza la geometría 3D de la autopista de 6 carriles: fondos trapezoidales,
	 * bloque rojo continuo de bloqueo de carril, ondas horizontales de compás, líneas
	 * divisorias y el arco blanco principal de impacto.
	 */
	private void drawPerspectiveHighway(PGraphics g, int songTimeMs) {
		int lockedLane = _rhythmCore.getLockedLane();

		float sFar = S_FAR;
		float uFar = 0.0f;
		float sBottom = scaleFromT(1.13f);
		float uBottom = uFromScale(sBottom);

		// 1. Fondos trapezoidales por carril
		for (int lane = 0; lane < LANE_COUNT; lane++) {
			float xiL = -1.0f + lane * (2.0f / 6.0f);
			float xiR = xiL + (2.0f / 6.0f);

			boolean keyDown = isGameplayInputActive() && (
				KBInput.isKeyDown(Config.RhythmButtons[lane]) ||
				KBInput.isKeyDown(Config.RhythmAltButtons[lane])
			);

			int fillCol = (lane % 2 == 0)
				? g.color(20, 22, 34, 115)
				: g.color(28, 30, 44, 115);

			if (keyDown && lane != lockedLane) {
				fillCol = g.color(255, 120, 130, 65);
			}

			g.noStroke();
			g.fill(fillCol);
			g.quad(
				projectX(xiL, sFar),    projectY(xiL, uFar, sFar),
				projectX(xiR, sFar),    projectY(xiR, uFar, sFar),
				projectX(xiR, sBottom), projectY(xiR, uBottom, sBottom),
				projectX(xiL, sBottom), projectY(xiL, uBottom, sBottom)
			);
		}

		// 2. Bloque rojo continuo viajando por la pista para el intervalo [lockStartMs, lockEndMs]
		drawLaneLockBlock(g, songTimeMs);

		// 3. Ondas horizontales (arcos de beat) viajando a la velocidad de las notas
		float[] beat = computeBeatState(songTimeMs);
		float phase = beat[1];
		float beatLengthMs = Math.max(50.0f, beat[3]);
		float tpTime = beat[4];
		float approachTimeMs = getApproachTimeMs();
		float beatImpulse = (float) Math.pow(1.0f - phase, 2.2);

		int nMin = (int) Math.floor(((songTimeMs - 0.12f * approachTimeMs) - tpTime) / beatLengthMs);
		int nMax = (int) Math.ceil(((songTimeMs + approachTimeMs) - tpTime) / beatLengthMs);
		for (int n = nMin; n <= nMax; n++) {
			float beatTimeMs = tpTime + n * beatLengthMs;
			float tArc = 1.0f - ((beatTimeMs - songTimeMs) / approachTimeMs);
			if (tArc < 0.04f || tArc > 1.12f) continue;

			float sArc = scaleFromT(tArc);
			float uArc = uFromScale(sArc);
			boolean downbeat = ((n % 4 + 4) % 4) == 0;
			int baseAlpha = downbeat ? 95 : 50;
			int alpha = (int) PApplet.constrain(baseAlpha + tArc * 95 + beatImpulse * 25, 45, 210);
			drawPerspectiveArc(g, uArc, sArc, BEAT_WAVE_ARC_MAX_XI, g.color(150, 155, 175, alpha), downbeat ? 1.3f : 1.0f);
		}

		// 4. 7 líneas divisorias de carriles que divergen hacia la cámara
		for (int edge = 0; edge <= LANE_COUNT; edge++) {
			float xi = -1.0f + edge * (2.0f / 6.0f);
			float xTop = projectX(xi, sFar);
			float yTop = projectY(xi, uFar, sFar);
			float xBot = projectX(xi, sBottom);
			float yBot = projectY(xi, uBottom, sBottom);

			boolean outer = (edge == 0 || edge == LANE_COUNT);
			g.stroke(outer ? g.color(210, 215, 230, 195) : g.color(195, 200, 220, 145));
			g.strokeWeight(outer ? 1.5f : 1.0f);
			g.line(xTop, yTop, xBot, yBot);
		}

		// 5. Línea blanca principal (arco de impacto t = 1.0, S = 1.0) pulsando al ritmo
		float hitWeight = 1.8f + beatImpulse * 1.1f;
		drawPerspectiveArc(g, 1.0f, 1.0f, HIT_LINE_ARC_MAX_XI, g.color(255, 255, 255, 245), hitWeight);
		g.strokeWeight(1.0f);
	}

	/**
	 * Renderiza el bloque rojo continuo que desciende por el carril afectado durante la
	 * advertencia y la duración activa del bloqueo ({@code [lockStartMs, lockEndMs]}).
	 */
	private void drawLaneLockBlock(PGraphics g, int songTimeMs) {
		int lockVisLane = _rhythmCore.getLockVisualLane();
		int lockStartMs = _rhythmCore.getLockStartMs();
		int lockEndMs = _rhythmCore.getLockEndMs();
		if (lockVisLane < 0 || lockVisLane >= LANE_COUNT || lockEndMs <= lockStartMs) {
			return;
		}

		float appMs = getApproachTimeMs();
		float tHead = 1.0f - ((lockStartMs - songTimeMs) / appMs);
		float tTail = 1.0f - ((lockEndMs - songTimeMs) / appMs);
		float tA = PApplet.constrain(tTail, 0.0f, 1.06f);
		float tB = PApplet.constrain(tHead, 0.0f, 1.06f);
		if (tB <= tA + 0.005f) {
			return;
		}

		float xiL = -1.0f + lockVisLane * (2.0f / 6.0f) + 0.025f;
		float xiR = -1.0f + (lockVisLane + 1) * (2.0f / 6.0f) - 0.025f;
		final int segs = 10;
		g.noStroke();
		for (int sIdx = 0; sIdx < segs; sIdx++) {
			float ft0 = PApplet.lerp(tA, tB, sIdx / (float) segs);
			float ft1 = PApplet.lerp(tA, tB, (sIdx + 1) / (float) segs);
			float sc0 = scaleFromT(ft0);
			float sc1 = scaleFromT(ft1);
			float uu0 = uFromScale(sc0);
			float uu1 = uFromScale(sc1);

			g.fill(215, 28, 48, 175);
			g.quad(
				projectX(xiL, sc0), projectY(xiL, uu0, sc0),
				projectX(xiR, sc0), projectY(xiR, uu0, sc0),
				projectX(xiR, sc1), projectY(xiR, uu1, sc1),
				projectX(xiL, sc1), projectY(xiL, uu1, sc1)
			);
		}

		// Borde brillante en los laterales y en el frente/cola del bloque rojo
		float scHead = scaleFromT(tB);
		float scTail = scaleFromT(tA);
		float uuHead = uFromScale(scHead);
		float uuTail = uFromScale(scTail);
		g.stroke(255, 95, 110, 235);
		g.strokeWeight(1.5f);
		g.line(projectX(xiL, scTail), projectY(xiL, uuTail, scTail), projectX(xiL, scHead), projectY(xiL, uuHead, scHead));
		g.line(projectX(xiR, scTail), projectY(xiR, uuTail, scTail), projectX(xiR, scHead), projectY(xiR, uuHead, scHead));
		g.line(projectX(xiL, scHead), projectY(xiL, uuHead, scHead), projectX(xiR, scHead), projectY(xiR, uuHead, scHead));
		g.line(projectX(xiL, scTail), projectY(xiL, uuTail, scTail), projectX(xiR, scTail), projectY(xiR, uuTail, scTail));
		g.strokeWeight(1.0f);
	}

	private void drawPerspectiveArc(PGraphics g, float u, float s, float maxXi, int col, float weight) {
		g.stroke(col);
		g.strokeWeight(weight);
		g.noFill();
		g.beginShape();
		final int steps = 28;
		for (int i = 0; i <= steps; i++) {
			float xi = -maxXi + (2.0f * maxXi * i) / steps;
			float x = projectX(xi, s);
			float y = projectY(xi, u, s);
			g.vertex(x, y);
		}
		g.endShape();
	}

	/**
	 * Dibuja una elipse en perspectiva real (escorzada y orientada al ángulo tangente de la pista)
	 * para que tanto los receptores como las notas luzcan apoyados en el plano 3D.
	 */
	private void drawPerspectiveEllipse(
		PGraphics g,
		float cx,
		float cy,
		float rx,
		float ry,
		float tiltAngle,
		int baseDepthColor,
		int rimColor,
		int fillColor,
		int coreColor
	) {
		g.pushMatrix();
		if (Engine.isPixelPerfect()) {
			g.translate(Math.round(cx), Math.round(cy));
		} else {
			g.translate(cx, cy);
		}
		g.rotate(tiltAngle);

		// Espesor 3D inferior del disco en perspectiva
		g.noStroke();
		g.fill(baseDepthColor);
		g.ellipse(0, Math.max(1.0f, ry * 0.24f), rx * 2.04f, ry * 2.04f);

		// Anillo exterior en perspectiva
		g.fill(rimColor);
		g.ellipse(0, 0, rx * 2.0f, ry * 2.0f);

		// Cuerpo interior de la elipse
		g.fill(fillColor);
		g.ellipse(0, 0, rx * 1.62f, ry * 1.62f);

		// Núcleo o brillo central
		if ((coreColor >>> 24) > 0) {
			g.fill(coreColor);
			g.ellipse(0, -ry * 0.08f, rx * 0.72f, ry * 0.65f);
		}

		g.popMatrix();
	}

	/**
	 * Renderiza los 6 receptores circulares escorzados sobre el arco de impacto (t = 1.0),
	 * incluyendo el hundimiento al pulsar y la cruz roja cuando un carril está bloqueado.
	 */
	private void drawPerspectiveReceptors(PGraphics g) {
		int lockedLane = _rhythmCore.getLockedLane();
		int warningLane = _rhythmCore.getWarningLane();
		boolean isCritical = _rhythmCore.getCombo() > 7;
		float tiltRatio = tiltRatioFromU(1.0f);

		for (int lane = 0; lane < LANE_COUNT; lane++) {
			float xi = laneToXi(lane);
			float cx = projectX(xi, 1.0f);
			float cy = projectY(xi, 1.0f, 1.0f);
			float ang = arcTangentAngle(xi, 1.0f);

			boolean keyDown = isGameplayInputActive() && (
				KBInput.isKeyDown(Config.RhythmButtons[lane]) ||
				KBInput.isKeyDown(Config.RhythmAltButtons[lane])
			);

			if (keyDown && lane != lockedLane) {
				cy += PRESS_SINK_PX;
			}

			float rx = keyDown ? 13.6f : 12.2f;
			float ry = rx * tiltRatio;

			int baseCol = g.color(18, 14, 24, 220);
			int rimCol = g.color(245, 245, 255, 245);
			int fillCol;
			int coreCol;

			if (lane == lockedLane) {
				rimCol = g.color(255, 95, 105);
				fillCol = g.color(85, 20, 30);
				coreCol = g.color(255, 95, 105);
			} else if (keyDown) {
				if (isCritical) {
					rimCol = g.color(255, 245, 200);
					fillCol = g.color(255, 165, 55);
					coreCol = g.color(255, 250, 215);
				} else {
					rimCol = g.color(255, 255, 255);
					fillCol = g.color(255, 210, 215);
					coreCol = g.color(255, 255, 255);
				}
			} else if (lane == warningLane) {
				rimCol = g.color(255, 220, 120);
				fillCol = g.color(210, 100, 30);
				coreCol = g.color(255, 220, 120, 160);
			} else if (isCritical) {
				rimCol = g.color(255, 205, 120, 250);
				fillCol = g.color(240, 115, 25);
				coreCol = g.color(255, 210, 130, 155);
			} else {
				fillCol = g.color(220, 42, 52);
				coreCol = g.color(255, 130, 135, 110);
			}

			drawPerspectiveEllipse(g, cx, cy, rx, ry, ang, baseCol, rimCol, fillCol, coreCol);

			if (lane == lockedLane) {
				g.stroke(255, 120, 130);
				g.strokeWeight(1.5f);
				g.line(cx - 5, cy - 3, cx + 5, cy + 3);
				g.line(cx - 5, cy + 3, cx + 5, cy - 3);
				g.strokeWeight(1.0f);
			}
		}
	}

	/**
	 * Renderiza todas las notas visibles en la ventana de aproximación: estelas azules
	 * de Hold Notes y cabezas elípticas en perspectiva (normales, a distancia y con cambio de carril).
	 */
	private void drawPerspectiveNotes(PGraphics g, int songTimeMs) {
		long[] notes = _rhythmCore.getNotes();
		if (notes == null) return;

		float approachTimeMs = getApproachTimeMs();

		for (int i = 0; i < notes.length; i++) {
			long packed = notes[i];
			int lane = NotePacker.getLane(packed);
			byte noteType = NotePacker.getNoteType(packed);
			long startTime = NotePacker.getStartTime(packed);
			long duration = NotePacker.getDuration(packed);

			boolean isBeingHeld = _rhythmCore.isHoldingLane(lane) && _rhythmCore.getHoldingNoteIndex(lane) == i;
			boolean isMissedHold = duration > 0 && _rhythmCore.isNoteMissed(i);
			if (_rhythmCore.isNoteProcessed(i) && !isBeingHeld && !isMissedHold) continue;

			float tHead = 1.0f - ((startTime - songTimeMs) / approachTimeMs);
			float tTail = 1.0f - (((startTime + duration) - songTimeMs) / approachTimeMs);

			if (tTail > 1.13f || tHead < 0.0f) continue;

			boolean keyDown = isGameplayInputActive() && (
				KBInput.isKeyDown(Config.RhythmButtons[lane]) ||
				KBInput.isKeyDown(Config.RhythmAltButtons[lane])
			);
			float targetXi = laneToXi(lane);

			// Trayectoria interpolada para notas con cambio de carril (SHIFT_UP / SHIFT_DOWN)
			float drawXi = targetXi;
			if (noteType == NotePacker.NOTE_SHIFT_UP && tHead < 0.65f && lane < 5) {
				float originXi = laneToXi(lane + 1);
				float shiftProg = PApplet.constrain(tHead / 0.65f, 0.0f, 1.0f);
				drawXi = originXi + (targetXi - originXi) * shiftProg;
			} else if (noteType == NotePacker.NOTE_SHIFT_DOWN && tHead < 0.65f && lane > 0) {
				float originXi = laneToXi(lane - 1);
				float shiftProg = PApplet.constrain(tHead / 0.65f, 0.0f, 1.0f);
				drawXi = originXi + (targetXi - originXi) * shiftProg;
			}

			// 1. Cinta en perspectiva para Hold Notes (azul translúcida o gris/rojiza si falló)
			if (duration > 0) {
				drawHoldNoteRibbon(g, targetXi, tHead, tTail, isBeingHeld, isMissedHold, keyDown);
			}

			// 2. Cabeza elíptica 3D de la nota
			if (!isBeingHeld && !isMissedHold && tHead >= 0.0f && tHead <= 1.12f) {
				drawNoteHead(g, noteType, duration, targetXi, drawXi, tHead, keyDown);
			}
		}
	}

	private void drawHoldNoteRibbon(
		PGraphics g,
		float targetXi,
		float tHead,
		float tTail,
		boolean isBeingHeld,
		boolean isMissedHold,
		boolean keyDown
	) {
		float segStart = Math.max(0.0f, tTail);
		float segEnd = isBeingHeld ? 1.0f : Math.min(1.12f, tHead);
		boolean sinkTail = isBeingHeld || (!isMissedHold && keyDown && segEnd >= 0.98f);
		if (segEnd <= segStart) return;

		final int slices = 8;
		final float halfLaneXi = 0.075f;
		g.noStroke();
		if (isMissedHold) {
			g.fill(125, 72, 85, 85);
		} else {
			g.fill(45, 165, 255, 115);
		}
		for (int sIdx = 0; sIdx < slices; sIdx++) {
			float ta = segStart + (segEnd - segStart) * (sIdx / (float) slices);
			float tb = segStart + (segEnd - segStart) * ((sIdx + 1) / (float) slices);
			float sa = scaleFromT(ta);
			float sb = scaleFromT(tb);
			float ua = uFromScale(sa);
			float ub = uFromScale(sb);
			float sinkA = sinkTail ? PRESS_SINK_PX * PApplet.constrain((ta - 0.75f) / 0.25f, 0.0f, 1.0f) : 0.0f;
			float sinkB = sinkTail ? PRESS_SINK_PX * PApplet.constrain((tb - 0.75f) / 0.25f, 0.0f, 1.0f) : 0.0f;
			g.quad(
				projectX(targetXi - halfLaneXi, sa), projectY(targetXi - halfLaneXi, ua, sa) + sinkA,
				projectX(targetXi + halfLaneXi, sa), projectY(targetXi + halfLaneXi, ua, sa) + sinkA,
				projectX(targetXi + halfLaneXi, sb), projectY(targetXi + halfLaneXi, ub, sb) + sinkB,
				projectX(targetXi - halfLaneXi, sb), projectY(targetXi - halfLaneXi, ub, sb) + sinkB
			);
		}
	}

	private void drawNoteHead(
		PGraphics g,
		byte noteType,
		long duration,
		float targetXi,
		float drawXi,
		float tHead,
		boolean keyDown
	) {
		float s = scaleFromT(tHead);
		float u = uFromScale(s);
		float cx = projectX(drawXi, s);
		float cy = projectY(drawXi, u, s);
		if (keyDown && tHead >= 0.85f) {
			cy += PRESS_SINK_PX * PApplet.constrain((tHead - 0.85f) / 0.15f, 0.0f, 1.0f);
		}
		float ang = arcTangentAngle(drawXi, s);
		float tiltRatio = tiltRatioFromU(u);

		// Línea guía e indicador en el receptor para ataques a distancia (RANGED_ORIGIN)
		if (noteType == NotePacker.NOTE_RANGED_ORIGIN && tHead < 1.0f) {
			float rxTarget = projectX(targetXi, 1.0f);
			float ryTarget = projectY(targetXi, 1.0f, 1.0f);
			g.stroke(255, 120, 80, 150);
			g.noFill();
			g.line(cx, cy, rxTarget, ryTarget);
			drawPerspectiveEllipse(
				g,
				rxTarget,
				ryTarget,
				15.0f,
				15.0f * tiltRatioFromU(1.0f),
				arcTangentAngle(targetXi, 1.0f),
				g.color(0, 0),
				g.color(255, 130, 80, 200),
				g.color(255, 110, 75, 45),
				g.color(0, 0)
			);
		}

		float rx = Math.max(2.6f, 12.2f * s);
		float ry = Math.max(1.2f, rx * tiltRatio);

		int baseCol = g.color(8, 8, 14, 210);
		int rimCol;
		int fillCol;
		int coreCol;

		switch (noteType) {
			case NotePacker.NOTE_RANGED_ORIGIN -> {
				rimCol = g.color(255, 185, 120);
				fillCol = g.color(255, 95, 60);
				coreCol = g.color(255, 240, 190);
			}
			case NotePacker.NOTE_SHIFT_UP -> {
				rimCol = g.color(200, 255, 215);
				fillCol = g.color(95, 225, 135);
				coreCol = g.color(230, 255, 235);
			}
			case NotePacker.NOTE_SHIFT_DOWN -> {
				rimCol = g.color(255, 235, 165);
				fillCol = g.color(245, 175, 55);
				coreCol = g.color(255, 245, 205);
			}
			default -> {
				if (duration > 0) {
					rimCol = g.color(170, 235, 255, 210);
					fillCol = g.color(45, 165, 245, 155);
					coreCol = g.color(150, 225, 255, 140);
				} else {
					rimCol = g.color(240, 225, 255);
					fillCol = g.color(0xE2, 0xE2, 0xE2);
					coreCol = g.color(245, 245, 245, 165);
				}
			}
		}

		drawPerspectiveEllipse(g, cx, cy, rx, ry, ang, baseCol, rimCol, fillCol, coreCol);

		// Chevron direccional escorzado para SHIFT_UP / SHIFT_DOWN
		if (noteType == NotePacker.NOTE_SHIFT_UP || noteType == NotePacker.NOTE_SHIFT_DOWN) {
			float dir = (noteType == NotePacker.NOTE_SHIFT_UP) ? -1.0f : 1.0f;
			g.stroke(15, 20, 28);
			g.strokeWeight(1.2f);
			g.line(cx + rx * 0.35f * dir, cy, cx - rx * 0.25f * dir, cy - ry * 0.32f);
			g.line(cx + rx * 0.35f * dir, cy, cx - rx * 0.25f * dir, cy + ry * 0.32f);
			g.strokeWeight(1.0f);
		}
	}

	/**
	 * Renderiza los carteles flotantes de juicio (PERFECT, GOOD, SAFE, MISS) sobre cada carril.
	 */
	private void drawFloatingRatings(PGraphics g) {
		if (_floatingRatings.isEmpty()) return;
		g.pushStyle();
		g.textAlign(PConstants.CENTER, PConstants.CENTER);
		if (Engine.descriptionFont != null) {
			g.textFont(Engine.descriptionFont, 9);
		} else {
			g.textSize(9);
		}
		for (int i = 0; i < _floatingRatings.size(); i++) {
			FloatingRating fr = _floatingRatings.get(i);
			float ratio = PApplet.constrain((fr.life / fr.maxLife) * 2.0f, 0.0f, 1.0f);
			int alpha = Math.round(255 * ratio);
			int rgb = fr.color & 0x00FFFFFF;

			g.fill(0, 0, 0, Math.round(190 * ratio));
			g.text(fr.text, fr.x + 1.0f, fr.y + 1.0f);

			g.fill((alpha << 24) | rgb);
			g.text(fr.text, fr.x, fr.y);
		}
		g.popStyle();
	}
}
