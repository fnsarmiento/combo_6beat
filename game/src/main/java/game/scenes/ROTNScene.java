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
import engine.dialogs.DialogManager;
import engine.dialogs.GameDialog;
import engine.draw.ParticleSystem;
import engine.draw.Sprite2D;
import kuusisto.tinysound.Sound;
import game.Config;
import game.Global;
import game.core.GameState;
import game.core.db.CustomSongRepository;
import game.core.notes.NotePacker;
import game.core.notes.OsuManiaReader;
import game.core.notes.TimingPointPacker;
import java.io.File;
import java.util.ArrayList;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics;
import processing.core.PImage;
import processing.data.XML;

public class ROTNScene extends Scene {

	private final String _sceneFilePath;
	private final CustomSongRepository.CustomSongSummary _customSong;
	private final String _manifestChartId;
	private final XML _manifestPackNode;
	private final XML _manifestChartNode;
	private MusicTrack _bgm;
	private ROTNCore _rhythmCore;
	private long[] _timingPoints = new long[0];

	// Datos del Jefe y combate cargados desde .scene XML o .osu [Combo6Beat]
	private String _bossId = "boss_generic";
	private String _bossName = "Enemigo Rítmico";
	private String _bossSpecies = "";
	private Sprite2D _bossSprite;
	private PImage _customCoverImg;
	private String _returnScene = "";
	private String _defeatFlag = "";
	private String _rewardItem = "";
	private boolean _victoryRewardGranted = false;
	private boolean _customAttemptRecorded = false;
	private boolean _customIsNewRecord = false;

	// Parámetros de perspectiva 3D de la pista de 6 carriles (resolución virtual 480x270)
	private static final float CENTER_X = BASE_W * 0.5f; // 240.0f
	private static final float Y_FAR = 92.0f;            // Horizonte bajo el jefe
	private static final float Y_HIT = 218.0f;           // Arco blanco de impacto
	private static final float HALF_W_HIT = 126.0f;      // Semiancho total de 6 carriles en Y_HIT
	private static final float ARC_RISE = 16.0f;         // Curvatura hacia arriba en los extremos
	private static final float Z_FAR = 3.2f;             // Profundidad en t = 0
	private static final float Z_HIT = 1.0f;             // Profundidad en t = 1 (línea de impacto)
	private static final float S_FAR = 1.0f / Z_FAR;
	private static final float PRESS_SINK_PX = 3.5f;

	private float _scrollSpeed = 0.22f; // px/ms equivalente para calcular approachTimeMs
	private int _songFallbackTimerMs = 0;
	private boolean _bgmPausedByScene = false;
	private float _introCountdown = 3.0f;
	private boolean _bgmStarted = false;
	private Sound _hitSfx;
	private final ParticleSystem _particles = new ParticleSystem(320);
	private final float[] _holdParticleTimers = new float[6];
	private final ArrayList<FloatingRating> _floatingRatings = new ArrayList<>();

	private static class FloatingRating {
		String text;
		float x, y, vy;
		float life, maxLife;
		int color;
	}
	private String _lastRatingText = "";
	private int _lastRatingColor = 0xFFFFFFFF;
	private float _ratingTimer = 0.0f;

	// Controladores ControlP5 (toda la UI vive en ControlP5, nunca en el framebuffer)
	private Button _uiHeartIcon;
	private Button _uiHpBg;
	private Button _uiHpFill;
	private Textlabel _uiHpLbl;
	private Textlabel _uiShieldLbl;

	private Textlabel _uiBossLbl;
	private Button _uiBossHpBg;
	private Button _uiBossHpFill;
	private Textlabel _uiAlertLbl;

	private Textlabel _uiScoreLbl;
	private Textlabel _uiComboLbl;
	private Textlabel _uiRatingLbl;

	private Textlabel _uiHealRatioLbl;
	private Button _uiHealBg;
	private Button _uiHealFill;
	private Textlabel _uiHealT1Lbl;
	private Textlabel _uiHealT2Lbl;

	private Button _uiItemIcon;
	private Button _uiItemBadgeBg;
	private Textlabel _uiItemLbl;

	private Group _uiModalGroup;
	private Textlabel _uiModalTitle;
	private Textarea _uiModalDesc;
	private Button _uiModalBtn1;
	private Button _uiModalBtn2;
	private int _modalFocusIdx = 0;
	private boolean _modalWasOpen = false;

	public ROTNScene(String path) {
		super(path);
		this._customSong = null;
		this._manifestChartId = "";
		this._manifestPackNode = null;
		this._manifestChartNode = null;
		this._sceneFilePath = path != null ? path : "";
	}

	public ROTNScene(CustomSongRepository.CustomSongSummary customSong) {
		super((String) null);
		this._customSong = customSong;
		this._manifestChartId = "";
		this._manifestPackNode = null;
		this._manifestChartNode = null;
		this._sceneFilePath = (customSong != null && !customSong.rutaChart.isEmpty())
			? ResourcesManager.getAbsPathFromProject(customSong.rutaChart)
			: "";
	}

	private ROTNScene(XML packNode, XML chartNode, String chartId) {
		super((String) null);
		this._customSong = null;
		this._manifestChartId = chartId != null ? chartId : "";
		this._manifestPackNode = packNode;
		this._manifestChartNode = chartNode;
		this._sceneFilePath = ResourcesManager.getAbsPathFromProject(Global.SONGS_MANIFEST);
	}

	public static ROTNScene fromManifest(String chartId) {
		try {
			String manifestAbs = ResourcesManager.getAbsPathFromProject(Global.SONGS_MANIFEST);
			XML root = Engine.getApp().loadXML(manifestAbs);
			if (root != null) {
				for (XML pack : root.getChildren("Pack")) {
					for (XML chart : pack.getChildren("Chart")) {
						if (chartId != null && chartId.equals(chart.getString("id", ""))) {
							return new ROTNScene(pack, chart, chartId);
						}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Error cargando chart '" + chartId + "' desde songs.xml: " + e.getMessage());
		}
		return new ROTNScene((String) null);
	}

	@Override
	public void setup() {
		Engine.ensureVirtualScreenSize((int) BASE_W, (int) BASE_H);
		GameState.init();
		GameState.bindDialogHooks();
		Audio.pausePersistentBGM();
		super.setup();

		if (_customSong == null && _manifestChartNode == null && _xmlScene == null) return;

		int playerMaxHp = 200;
		int bossMaxHp = 1500;
		int bossAttackDamage = 12;
		int bossHitDamage = 6;
		int healThreshold = 50;
		float healActiveDuration = 6.0f;
		int laneLockInterval = 10000;
		int laneLockDuration = 2800;

		String bossSpritePath = "";
		int bossRows = 1;
		int bossCols = 1;
		float bossScaleX = 2.0f;
		float bossScaleY = 2.0f;
		float bossWidthPx = -1.0f;
		float bossHeightPx = -1.0f;
		float bossDelay = 0.1f;
		float bossX = CENTER_X;
		float bossY = 54.0f;

		OsuManiaReader reader = null;
		if (_customSong != null) {
			_bossId = "custom_" + _customSong.idCancion;
			_bossName = _customSong.titulo;
			_bossSpecies = _customSong.dificultad + " | " + Math.round(_customSong.bpm) + " BPM";
			_scrollSpeed = 0.22f * Math.max(0.5f, GameState.songSpeedMultiplier);

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
			if (!_customSong.rutaPortada.isEmpty()) {
				String coverAbs = ResourcesManager.getAbsPathFromProject(_customSong.rutaPortada);
				if (new File(coverAbs).exists() && Engine.getApp() != null) {
					_customCoverImg = Engine.getApp().loadImage(coverAbs);
				}
			}
		} else if (_manifestChartNode != null) {
			_returnScene = GameState.returnScenePath;
			_scrollSpeed = _manifestChartNode.getFloat("scroll-speed", 0.22f) * Math.max(0.5f, GameState.songSpeedMultiplier);

			String osuRel = _manifestChartNode.getString("file", "");
			if (!osuRel.isEmpty()) {
				String osuAbs = resolveSceneRelativeOrResource(osuRel);
				reader = new OsuManiaReader(osuAbs);
				_bgm = reader.getSong();
			}

			String coverRel = (_manifestPackNode != null) ? _manifestPackNode.getString("cover", "") : "";
			if (!coverRel.isEmpty() && Engine.getApp() != null) {
				String coverAbs = resolveSceneRelativeOrResource(coverRel);
				if (new File(coverAbs).exists()) {
					_customCoverImg = Engine.getApp().loadImage(coverAbs);
				}
			}

			_bossId = _manifestChartNode.getString("boss-id", _manifestChartId);
			_bossName = _manifestChartNode.getString("boss-name", _bossName);
			_bossSpecies = _manifestChartNode.getString("boss-species", "");
			bossMaxHp = _manifestChartNode.getInt("boss-hp", bossMaxHp);
			bossAttackDamage = _manifestChartNode.getInt("attack-damage", bossAttackDamage);
			bossHitDamage = _manifestChartNode.getInt("hit-damage", bossHitDamage);
			bossSpritePath = _manifestChartNode.getString("boss-sprite", bossSpritePath);
			_defeatFlag = _manifestChartNode.getString("defeat-flag", _defeatFlag);
			_rewardItem = _manifestChartNode.getString("reward-item", _rewardItem);
		} else {
			_returnScene = _xmlScene.getString("return-scene", GameState.returnScenePath);

			XML songNode = _xmlScene.getChild("Song");
			String osuRel = (songNode != null) ? songNode.getString("file", "") : "";
			if (songNode != null) {
				_scrollSpeed = songNode.getFloat("scroll-speed", 0.22f);
			}
			_scrollSpeed *= Math.max(0.5f, GameState.songSpeedMultiplier);

			if (!osuRel.isEmpty()) {
				String osuAbs = resolveSceneRelativeOrResource(osuRel);
				reader = new OsuManiaReader(osuAbs);
				_bgm = reader.getSong();
			}

			if (reader != null && reader.hasBossSection()) {
				if (!reader.getBossName().isEmpty()) _bossName = reader.getBossName();
				if (!reader.getBossSpecies().isEmpty()) _bossSpecies = reader.getBossSpecies();
				bossMaxHp = reader.getBossMaxHp();
				bossAttackDamage = reader.getBossAttackDamage();
				bossHitDamage = reader.getBossHitDamage();
				bossSpritePath = reader.getBossSprite();
				bossRows = reader.getBossRows();
				bossCols = reader.getBossCols();
				if (!reader.getReturnScene().isEmpty()) _returnScene = reader.getReturnScene();
				if (!reader.getDefeatFlag().isEmpty()) _defeatFlag = reader.getDefeatFlag();
				if (!reader.getRewardItem().isEmpty()) _rewardItem = reader.getRewardItem();
			}

			XML bossNode = _xmlScene.getChild("Boss");
			if (bossNode != null) {
				_bossId = bossNode.getString("id", _bossId);
				_bossName = bossNode.getString("name", _bossName);
				_bossSpecies = bossNode.getString("species", _bossSpecies);
				bossMaxHp = bossNode.getInt("max-hp", bossMaxHp);
				bossAttackDamage = bossNode.getInt("attack-damage", bossAttackDamage);
				bossHitDamage = bossNode.getInt("hit-damage", bossHitDamage);
				bossSpritePath = bossNode.getString("sprite", bossSpritePath);
				bossRows = bossNode.getInt("rows", bossRows);
				bossCols = bossNode.getInt("cols", bossCols);
				bossScaleX = bossNode.getFloat("scale-x", bossScaleX);
				bossScaleY = bossNode.getFloat("scale-y", bossScaleY);
				bossWidthPx = bossNode.getFloat("width", bossWidthPx);
				bossHeightPx = bossNode.getFloat("height", bossHeightPx);
				bossDelay = bossNode.getFloat("delay", bossDelay);
				bossX = bossNode.getFloat("position-x", bossX);
				bossY = bossNode.getFloat("position-y", bossY);
				_defeatFlag = bossNode.getString("defeat-flag", _defeatFlag);
				_rewardItem = bossNode.getString("reward-item", _rewardItem);
			}

			XML combatNode = _xmlScene.getChild("Combat");
			if (combatNode != null) {
				playerMaxHp = combatNode.getInt("player-max-hp", playerMaxHp);
				healThreshold = combatNode.getInt("heal-threshold", healThreshold);
				healActiveDuration = combatNode.getFloat("heal-active-duration", healActiveDuration);
				laneLockInterval = combatNode.getInt("lane-lock-interval", laneLockInterval);
				laneLockDuration = combatNode.getInt("lane-lock-duration", laneLockDuration);
			}
		}

		if (bossSpritePath != null && !bossSpritePath.isEmpty()) {
			_bossSprite = new Sprite2D(bossSpritePath, Math.max(1, bossRows), Math.max(1, bossCols), 10);
			_bossSprite.centerPosition = true;
			_bossSprite.position.set(bossX, bossY);
			_bossSprite.scale.set(bossScaleX, bossScaleY);
			if (bossWidthPx > 0.0f || bossHeightPx > 0.0f) {
				_bossSprite.setPixelSize(bossWidthPx, bossHeightPx);
			}
			_bossSprite.delay = bossDelay;
		}

		long[] loadedNotes = (reader != null) ? reader.getNotes() : new long[0];
		_timingPoints = (reader != null && reader.getTimingPoints() != null)
			? reader.getTimingPoints()
			: new long[0];
		_rhythmCore = new ROTNCore(loadedNotes);
		_rhythmCore.setBossInvincible(GameState.launchedFromMenu && GameState.bossInvincibleMode);
		_rhythmCore.configureCombat(
			playerMaxHp,
			bossMaxHp,
			bossAttackDamage,
			bossHitDamage,
			healThreshold,
			healActiveDuration,
			laneLockInterval,
			laneLockDuration
		);
		_rhythmCore.setOnHitListener((lane, rating, combo, detail) -> {
			int ratingColor = switch (rating) {
				case PERFECT -> 0xFF4FB3FF; // Azul
				case GOOD    -> 0xFF55E87A; // Verde
				case SAFE    -> 0xFFFFFFFF; // Blanco
				case MISS    -> 0xFFB86BFF; // Morado
			};
			_lastRatingText = rating.name();
			_ratingTimer = 0.65f;
			_lastRatingColor = ratingColor;

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

			// Partículas en el texto con el color del juicio
			_particles.emitBurst(fr.x, fr.y, 7, 14.0f, 44.0f, 1.8f, 3.2f, 0.18f, 0.36f, 18.0f, ratingColor);

			if (rating != ROTNCore.HitRating.MISS) {
				_particles.emitBurst(cx, cy + PRESS_SINK_PX, 10, 24.0f, 70.0f, 2.2f, 4.0f, 0.18f, 0.36f, 55.0f, 0xFFFFFFFF);
			}
		});

		_hitSfx = ResourcesManager.loadSFX("sfx/Normal-Hitnormal.wav");

		setupCombatUI();
		syncCombatUI();

		_introCountdown = 3.0f;
		_bgmStarted = false;
	}

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

	private void setupCombatUI() {
		PApplet app = Engine.getApp();

		ControlFont fontSmall = makeFont(6.5f, false);
		ControlFont fontMed = makeFont(8.0f, false);
		ControlFont fontBig = makeFont(10.5f, true);
		ControlFont fontModalTitle = makeFont(14.0f, true);

		// 1. Arriba-Izquierda: Corazón + Vida del Jugador + Escudo
		PImage heartImg = createPixelHeartIcon();
		_uiHeartIcon = addUIBox("ui_rotn_heart_icon", null, 10, 8, 24, 24, 0)
			.setSize(heartImg.width, heartImg.height)
			.setImage(heartImg);

		_uiHpLbl = addUILabel("ui_rotn_hp_lbl", null, "HP: 100/100", 38, 9, app.color(245, 235, 240), fontMed);
		_uiHpBg = addUIBox("ui_rotn_hp_bg", null, 38, 21, 96, 7, app.color(40, 20, 28, 220));
		_uiHpFill = addUIBox("ui_rotn_hp_fill", null, 38, 21, 96, 7, app.color(230, 65, 85, 245));
		_uiShieldLbl = addUILabel("ui_rotn_shield_lbl", null, "", 38, 31, app.color(110, 235, 255), fontSmall);

		// 2. Arriba-Centro: Nombre, HP del Jefe y Avisos de ataque
		_uiBossLbl = addUILabel("ui_rotn_boss_lbl", null, _bossName, 182, 6, app.color(255, 215, 145), fontMed);
		_uiBossHpBg = addUIBox("ui_rotn_boss_hp_bg", null, 175, 17, 130, 6, app.color(42, 18, 26, 220));
		_uiBossHpFill = addUIBox("ui_rotn_boss_hp_fill", null, 175, 17, 130, 6, app.color(235, 65, 85, 245));
		_uiAlertLbl = addUILabel("ui_rotn_alert_lbl", null, "", 170, 80, app.color(255, 190, 60), fontMed);

		// 3. Arriba-Derecha: Score, Combo y Juicio
		_uiScoreLbl = addUILabel("ui_rotn_score_lbl", null, "SCORE: 0", 356, 8, app.color(255), fontMed);
		_uiComboLbl = addUILabel("ui_rotn_combo_lbl", null, "COMBO: 0x", 356, 20, app.color(110, 225, 255), fontMed);
		_uiRatingLbl = addUILabel("ui_rotn_rating_lbl", null, "", 356, 33, app.color(255, 230, 110), fontBig);

		// 4. Abajo-Izquierda: Barra Vertical de Curación Dinámica (como en la referencia)
		_uiHealRatioLbl = addUILabel("ui_rotn_heal_ratio_lbl", null, "0/50", 12, 162, app.color(230, 60, 60), fontBig);
		_uiHealBg = addUIBox("ui_rotn_heal_bg", null, 14, 178, 14, 76, app.color(24, 30, 38, 225));
		_uiHealFill = addUIBox("ui_rotn_heal_fill", null, 14, 252, 14, 1, app.color(45, 200, 90, 245));
		_uiHealT2Lbl = addUILabel("ui_rotn_heal_t2", null, "2", 31, 200, app.color(220, 235, 220), fontSmall);
		_uiHealT1Lbl = addUILabel("ui_rotn_heal_t1", null, "1", 31, 238, app.color(220, 235, 220), fontSmall);

		// 5. Abajo-Derecha: Ícono pixel art de curación + Modo + Chip equipado
		_uiItemBadgeBg = addUIBox("ui_rotn_item_badge", null, 346, 232, 124, 30, app.color(18, 16, 28, 205));
		PImage healImg = createPixelHealIcon();
		_uiItemIcon = addUIBox("ui_rotn_item_icon", null, 442, 235, 24, 24, 0)
			.setSize(healImg.width, healImg.height)
			.setImage(healImg);
		_uiItemLbl = addUILabel("ui_rotn_item_lbl", null, "Cura: Optimo", 352, 238, app.color(235, 240, 255), fontSmall);

		// 6. Modal ControlP5 para Pausa / Victoria / Derrota
		_uiModalGroup = addUIGroup("ui_rotn_modal_group", (BASE_W - 260) * 0.5f, (BASE_H - 150) * 0.5f, 260, 150);
		addUIBox("ui_rotn_modal_bg", _uiModalGroup, 0, 0, 260, 150, app.color(12, 14, 24, 238));
		_uiModalTitle = addUILabel("ui_rotn_modal_title", _uiModalGroup, "PAUSA", 20, 16, app.color(255), fontModalTitle);
		_uiModalDesc = addUITextarea("ui_rotn_modal_desc", _uiModalGroup, "", 20, 44, 220, 56, app.color(230, 238, 250), app.color(18, 22, 34), fontMed);

		_uiModalBtn1 = addUIButton(
			"ui_rotn_modal_btn1", _uiModalGroup, "Reanudar", 20, 110, 104, 24,
			app.color(35, 90, 155), app.color(60, 130, 215), fontSmall,
			() -> { _modalFocusIdx = 0; onModalPrimaryClick(); }
		);
		_uiModalBtn2 = addUIButton(
			"ui_rotn_modal_btn2", _uiModalGroup, "Volver al Mapa", 136, 110, 104, 24,
			app.color(95, 38, 52), app.color(145, 55, 75), fontSmall,
			() -> { _modalFocusIdx = 1; returnToWorld(); }
		);

		_uiModalGroup.hide();
	}

	private void onModalPrimaryClick() {
		if (_rhythmCore == null) return;
		ROTNCore.BattleState state = _rhythmCore.getBattleState();
		if (state == ROTNCore.BattleState.VICTORY) {
			returnToWorld();
		} else if (state == ROTNCore.BattleState.DEFEAT) {
			restartBattleScene();
		} else if (_rhythmCore.isPaused()) {
			_rhythmCore.togglePause();
		}
	}

	private void restartBattleScene() {
		if (_customSong != null) {
			CustomSongRepository.CustomSongSummary latest = CustomSongRepository.getById(_customSong.idCancion);
			Engine.changeScene(new ROTNScene(latest != null ? latest : _customSong));
		} else if (_manifestChartId != null && !_manifestChartId.isEmpty()) {
			Engine.changeScene(ROTNScene.fromManifest(_manifestChartId));
		} else {
			Engine.changeScene(new ROTNScene(_sceneFilePath));
		}
	}

	private String resolveSceneRelativeOrResource(String relPath) {
		String fromRes = ResourcesManager.getResourceAbsPath(relPath);
		if (new File(fromRes).exists()) return fromRes;

		if (!_sceneFilePath.isEmpty()) {
			File parent = new File(_sceneFilePath).getParentFile();
			if (parent != null) {
				File f = new File(parent, relPath);
				if (f.exists()) return f.getAbsolutePath();
			}
		}
		return ResourcesManager.getAbsPathFromProject(relPath);
	}

	@Override
	public void update(float dt) {
		super.update(dt);
		GameState.updateNotification(dt);

		if (DialogManager.isActive()) {
			if (_bgm != null && _bgm.isPlaying()) {
				Audio.pauseBGM();
				_bgmPausedByScene = true;
			}
			syncCombatUI();
			return;
		}

		if (_bossSprite != null) {
			_bossSprite.updateDraw(dt);
		}

		if (_rhythmCore == null) return;

		ROTNCore.BattleState state = _rhythmCore.getBattleState();
		if (state != ROTNCore.BattleState.PLAYING) {
			if (_bgm != null && _bgm.isPlaying()) {
				Audio.pauseBGM();
				_bgmPausedByScene = true;
			}
			if (!_modalWasOpen) {
				_modalFocusIdx = 0;
				_modalWasOpen = true;
			}
			handleEndBattleInput(state);
			syncCombatUI();
			return;
		}

		if (_introCountdown > 0.0f) {
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
				handlePauseModalInput();
			} else {
				_modalWasOpen = false;
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
			syncCombatUI();
			return;
		}

		int currentSongTimeMs;
		if (_bgm != null) {
			if (_rhythmCore.isPaused()) {
				if (_bgm.isPlaying()) {
					Audio.pauseBGM();
					_bgmPausedByScene = true;
				}
			} else if (_bgmPausedByScene) {
				_bgmPausedByScene = false;
				Audio.resumeBGM();
			}
			currentSongTimeMs = _bgm.getPositionMs();
		} else {
			if (!_rhythmCore.isPaused()) {
				_songFallbackTimerMs += Math.round(dt * 1000.0f);
			}
			currentSongTimeMs = _songFallbackTimerMs;
		}

		_rhythmCore.update(currentSongTimeMs, dt);

		if (_rhythmCore.isPaused()) {
			if (!_modalWasOpen) {
				_modalFocusIdx = 0;
				_modalWasOpen = true;
			}
			handlePauseModalInput();
		} else {
			_modalWasOpen = false;
			_particles.update(dt);
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
			for (int lane = 0; lane < 6; lane++) {
				if (lane != lockedLane && Config.wasRhythmKeyJustPressed(lane)) {
					Audio.playSFX(_hitSfx);
				}
				if (_rhythmCore.isHoldingLane(lane)) {
					_holdParticleTimers[lane] -= dt;
					if (_holdParticleTimers[lane] <= 0.0f) {
						_holdParticleTimers[lane] = 0.025f;
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

		if (_ratingTimer > 0) {
			_ratingTimer -= dt;
		}

		syncCombatUI();
	}

	private void handlePauseModalInput() {
		if (
			Config.wasUIKeyJustPressed(Config.LEFT) ||
			Config.wasUIKeyJustPressed(Config.RIGHT) ||
			Config.wasUIKeyJustPressed(Config.UP) ||
			Config.wasUIKeyJustPressed(Config.DOWN)
		) {
			_modalFocusIdx = (_modalFocusIdx == 0) ? 1 : 0;
		} else if (Config.wasUIKeyJustPressed(Config.INTERACT) || Config.wasRPGKeyJustPressed(Config.INTERACT)) {
			if (_modalFocusIdx == 0) {
				_rhythmCore.togglePause();
			} else {
				returnToWorld();
			}
		}
	}

	private void handleEndBattleInput(ROTNCore.BattleState state) {
		if (_customSong != null && !_customAttemptRecorded &&
			(state == ROTNCore.BattleState.VICTORY || state == ROTNCore.BattleState.DEFEAT)) {
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

		if (state == ROTNCore.BattleState.VICTORY && !_victoryRewardGranted) {
			_victoryRewardGranted = true;
			if (_customSong == null && !GameState.launchedFromMenu) {
				if (_defeatFlag != null && !_defeatFlag.isEmpty()) {
					GameState.defineFlag(_defeatFlag);
				}
				if (_rewardItem != null && !_rewardItem.isEmpty()) {
					String itemFlag = "REWARD_" + _defeatFlag + "_" + _rewardItem;
					if (!GameState.hasFlag(itemFlag)) {
						GameState.defineFlag(itemFlag);
						GameState.addItem(_rewardItem, 1);
					}
				}
				if (!GameState.isDebugSession) {
					GameState.recordBattleVictory(
						_bossId,
						_bossName,
						_bossSpecies,
						_defeatFlag,
						_sceneFilePath,
						resolveReturnTargetScene(),
						_rewardItem,
						_rhythmCore.getScore(),
						_rhythmCore.getMaxCombo(),
						_rhythmCore.getPerfectCount(),
						_rhythmCore.getGoodCount(),
						_rhythmCore.getSafeCount(),
						_rhythmCore.getMissCount()
					);
					GameState.saveGame(resolveReturnTargetScene(), GameState.returnPlayerX, GameState.returnPlayerY);
				}
			}
		}

		if (
			state == ROTNCore.BattleState.DEFEAT && (
				Config.wasUIKeyJustPressed(Config.LEFT) ||
				Config.wasUIKeyJustPressed(Config.RIGHT) ||
				Config.wasUIKeyJustPressed(Config.UP) ||
				Config.wasUIKeyJustPressed(Config.DOWN)
			)
		) {
			_modalFocusIdx = (_modalFocusIdx == 0) ? 1 : 0;
		}

		boolean accept = Config.wasUIKeyJustPressed(Config.INTERACT) || Config.wasRPGKeyJustPressed(Config.INTERACT);
		boolean retry = KBInput.wasKeyJustPressed('R');
		boolean back = Config.wasUIKeyJustPressed(Config.CANCEL);

		if (state == ROTNCore.BattleState.VICTORY) {
			if (accept || back) {
				returnToWorld();
			}
		} else if (state == ROTNCore.BattleState.DEFEAT) {
			if (retry || (accept && _modalFocusIdx == 0)) {
				restartBattleScene();
			} else if (back || (accept && _modalFocusIdx == 1)) {
				returnToWorld();
			}
		}
	}

	private String resolveReturnTargetScene() {
		if (!GameState.launchedFromMenu && !GameState.isDebugSession && GameState.returnScenePath != null && !GameState.returnScenePath.isEmpty()) {
			return GameState.returnScenePath;
		}
		return (_returnScene != null && !_returnScene.isEmpty())
			? _returnScene
			: GameState.returnScenePath;
	}

	private void returnToWorld() {
		if (GameState.launchedFromMenu) {
			GameState.launchedFromMenu = false;
			if (!GameState.loadGame()) {
				GameState.reset();
			}
			Engine.changeScene(new InitScene(InitScene.ViewMode.SONG_SELECT));
			return;
		}
		boolean won = (_rhythmCore != null && _rhythmCore.getBattleState() == ROTNCore.BattleState.VICTORY);
		DialogManager.resolveBattleOutcome(won);
		if (won) {
			GameState.defineFlag("LAST_BATTLE_WON");
			GameState.clearFlag("LAST_BATTLE_LOST");
		} else {
			GameState.defineFlag("LAST_BATTLE_LOST");
			GameState.clearFlag("LAST_BATTLE_WON");
		}
		GameState.autoSaveIfStory();
		Engine.changeScene(new HubScene());
	}

	private void syncCombatUI() {
		if (_rhythmCore == null) return;
		PApplet app = Engine.getApp();

		// HP Jugador
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

		// HP Jefe y Alerta de ataque de carril
		boolean bossInvincible = _rhythmCore.isBossInvincible();
		float bHpRatio = bossInvincible
			? 1.0f
			: (float) _rhythmCore.getBossHp() / Math.max(1, _rhythmCore.getBossMaxHp());
		bHpRatio = PApplet.constrain(bHpRatio, 0.0f, 1.0f);
		if (_uiBossLbl != null) {
			String sp = _bossSpecies.isEmpty() ? "" : " (" + _bossSpecies + ")";
			String hpText = bossInvincible
				? "[INVENCIBLE]"
				: (_rhythmCore.getBossHp() + "/" + _rhythmCore.getBossMaxHp());
			_uiBossLbl.setText(_bossName + sp + "  " + hpText);
		}
		if (_uiBossHpFill != null) {
			int bw = Math.max(1, Math.round(scaleW(130) * bHpRatio));
			_uiBossHpFill.setSize(bw, Math.max(1, (int) scaleH(6)));
			_uiBossHpFill.setColorBackground(
				bossInvincible
					? app.color(175, 110, 255, 245)
					: app.color(235, 65, 85, 245)
			);
		}
		if (_uiAlertLbl != null) {
			int lockedLane = _rhythmCore.getLockedLane();
			int warningLane = _rhythmCore.getWarningLane();
			if (lockedLane >= 0) {
				_uiAlertLbl.setText("CARRIL " + (lockedLane + 1) + " BLOQUEADO!");
				_uiAlertLbl.setColorValue(app.color(255, 95, 110));
			} else if (warningLane >= 0) {
				_uiAlertLbl.setText("ALERTA CARRIL " + (warningLane + 1));
				_uiAlertLbl.setColorValue(app.color(255, 190, 60));
			} else {
				_uiAlertLbl.setText("");
			}
		}

		// Score, Combo y Juicio
		if (_uiScoreLbl != null) {
			_uiScoreLbl.setText("SCORE: " + _rhythmCore.getScore());
		}
		if (_uiComboLbl != null) {
			_uiComboLbl.setText("COMBO: " + _rhythmCore.getCombo() + "x");
		}
		if (_uiRatingLbl != null) {
			_uiRatingLbl.setText("");
		}

		// Barra vertical de Curación Dinámica (abajo-izquierda)
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

		// Ítem / Amuletos abajo-derecha
		if (_uiItemLbl != null) {
			java.util.List<String> eq = GameState.getEquippedPowerUps();
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

		// Modal de Pausa / Victoria / Derrota
		if (_uiModalGroup != null) {
			ROTNCore.BattleState state = _rhythmCore.getBattleState();
			if (state == ROTNCore.BattleState.VICTORY) {
				_uiModalGroup.show();
				_uiModalTitle.setText(_customSong != null ? "CANCION COMPLETADA!" : "VICTORIA!");
				_uiModalTitle.setColorValue(app.color(110, 255, 155));
				String extraLine = "";
				if (_customSong != null) {
					extraLine = "\nPrecision: " + String.format("%.1f%%", _rhythmCore.getAccuracyPercent()) +
						(_customIsNewRecord ? "  [NUEVO RECORD!]" : "");
				} else if (_rewardItem != null && !_rewardItem.isEmpty()) {
					extraLine = "\nRecompensa: " + GameState.getItemName(_rewardItem);
				}
				_uiModalDesc.setText(
					"Score: " + _rhythmCore.getScore() +
					" | Max Combo: " + _rhythmCore.getMaxCombo() + "x" +
					extraLine
				);
				_uiModalBtn1.setLabel(GameState.launchedFromMenu ? "> Volver al Selector" : "> Volver al Mapa");
				_uiModalBtn1.setColorBackground(app.color(55, 130, 215));
				_uiModalBtn2.hide();
			} else if (state == ROTNCore.BattleState.DEFEAT) {
				_uiModalGroup.show();
				_uiModalTitle.setText("DERROTA");
				_uiModalTitle.setColorValue(app.color(255, 100, 110));
				_uiModalDesc.setText(
					"Score: " + _rhythmCore.getScore() +
					"\nHP Jefe restante: " + _rhythmCore.getBossHp()
				);
				_uiModalBtn1.setLabel((_modalFocusIdx == 0 ? "> " : "") + "Reintentar");
				_uiModalBtn1.setColorBackground(_modalFocusIdx == 0 ? app.color(55, 130, 215) : app.color(35, 90, 155));
				_uiModalBtn2.setLabel((_modalFocusIdx == 1 ? "> " : "") + "Volver");
				_uiModalBtn2.setColorBackground(_modalFocusIdx == 1 ? app.color(150, 58, 80) : app.color(95, 38, 52));
				_uiModalBtn2.show();
			} else if (_rhythmCore.isPaused()) {
				_uiModalGroup.show();
				_uiModalTitle.setText("PAUSA");
				_uiModalTitle.setColorValue(app.color(255));
				_uiModalDesc.setText(
					"Score: " + _rhythmCore.getScore() +
					" | Ventanas: " + (int)_rhythmCore.getPerfectWindowMs() + "/" +
					(int)_rhythmCore.getGoodWindowMs() + "/" + (int)_rhythmCore.getSafeWindowMs() + "ms"
				);
				_uiModalBtn1.setLabel((_modalFocusIdx == 0 ? "> " : "") + "Reanudar");
				_uiModalBtn1.setColorBackground(_modalFocusIdx == 0 ? app.color(55, 130, 215) : app.color(35, 90, 155));
				_uiModalBtn2.setLabel((_modalFocusIdx == 1 ? "> " : "") + "Salir al Mapa");
				_uiModalBtn2.setColorBackground(_modalFocusIdx == 1 ? app.color(150, 58, 80) : app.color(95, 38, 52));
				_uiModalBtn2.show();
			} else {
				_uiModalGroup.hide();
			}
		}
	}

	// =========================================================================
	// Proyección 3D en Perspectiva y Renderizado de Arena (Framebuffer 480x270)
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

	@Override
	public void draw(PGraphics g) {
		g.pushStyle();

		drawArenaBackground(g);

		if (_rhythmCore != null) {
			int songTimeMs;
			if (_introCountdown > 0.0f) {
				songTimeMs = -Math.round(_introCountdown * 1000.0f);
			} else {
				songTimeMs = (_bgm != null) ? _bgm.getPositionMs() : _songFallbackTimerMs;
			}
			drawPerspectiveHighway(g, songTimeMs);
			drawPerspectiveReceptors(g);
			drawPerspectiveNotes(g, songTimeMs);
			_particles.draw(g);
			drawFloatingRatings(g);
			if (_introCountdown > 0.0f) {
				int sec = (int) Math.ceil(_introCountdown);
				sec = PApplet.constrain(sec, 1, 3);
				g.pushStyle();
				g.textAlign(PConstants.CENTER, PConstants.CENTER);
				if (GameDialog.titleFont != null) {
					g.textFont(GameDialog.titleFont, 28);
				} else {
					g.textSize(28);
				}
				String label = String.valueOf(sec);
				float cy = 128.0f;
				g.fill(0, 0, 0, 210);
				g.text(label, CENTER_X + 2.0f, cy + 2.0f);
				g.fill(255, 235, 110, 255);
				g.text(label, CENTER_X, cy);
				g.popStyle();
			}
		}

		g.popStyle();
	}

	private void drawFloatingRatings(PGraphics g) {
		if (_floatingRatings.isEmpty()) return;
		g.pushStyle();
		g.textAlign(PConstants.CENTER, PConstants.CENTER);
		if (GameDialog.descriptionFont != null) {
			g.textFont(GameDialog.descriptionFont, 9);
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

	private void drawArenaBackground(PGraphics g) {
		// Cielo nocturno oscuro púrpura-vino (estilo arena de combate de la referencia)
		g.noStroke();
		g.background(14, 7, 22);
		for (int band = 0; band < 6; band++) {
			float y0 = band * 18.0f;
			g.fill(26 + band * 3, 10 + band * 2, 34 + band * 3, 160);
			g.rect(0, y0, BASE_W, 18.0f);
		}

		// Halo carmesí detrás del jefe
		for (int r = 5; r >= 1; r--) {
			g.fill(180, 32, 36, 18 + (6 - r) * 8);
			g.ellipse(CENTER_X, 54.0f, r * 30.0f, r * 20.0f);
		}

		// Horizonte curvo de la arena debajo del jefe
		g.fill(10, 11, 18);
		g.beginShape();
		g.vertex(0, BASE_H);
		g.vertex(0, Y_FAR + 14.0f);
		for (int i = 0; i <= 24; i++) {
			float nx = -1.0f + (2.0f * i) / 24.0f;
			float hx = i * (BASE_W / 24.0f);
			float hy = Y_FAR - 4.0f + 18.0f * (nx * nx);
			g.vertex(hx, hy);
		}
		g.vertex(BASE_W, BASE_H);
		g.endShape(PConstants.CLOSE);

		// Borde difuso del horizonte curvo
		g.stroke(42, 26, 48, 170);
		g.noFill();
		g.beginShape();
		for (int i = 0; i <= 24; i++) {
			float nx = -1.0f + (2.0f * i) / 24.0f;
			float hx = i * (BASE_W / 24.0f);
			float hy = Y_FAR - 4.0f + 18.0f * (nx * nx);
			g.vertex(hx, hy);
		}
		g.endShape();

		// Sprite animado del jefe o portada de canción custom centrado al fondo
		if (_bossSprite != null) {
			if (_rhythmCore != null && _rhythmCore.getBossFlashTimer() > 0) {
				g.noStroke();
				g.fill(255, 240, 240, 120);
				g.ellipse(_bossSprite.position.x, _bossSprite.position.y, 44, 34);
			}
			_bossSprite.draw(g);
		} else if (_customCoverImg != null && _customCoverImg.width > 0) {
			g.stroke(245, 225, 150, 220);
			g.strokeWeight(1.5f);
			g.fill(16, 12, 24);
			g.rect(CENTER_X - 20.0f, 34.0f, 40.0f, 40.0f);
			g.image(_customCoverImg, CENTER_X - 18.0f, 36.0f, 36.0f, 36.0f);
			g.strokeWeight(1.0f);
		} else {
			g.noStroke();
			g.fill(190, 40, 50);
			g.ellipse(CENTER_X, 54.0f, 38, 28);
		}
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

	private void drawPerspectiveHighway(PGraphics g, int songTimeMs) {
		int warningLane = _rhythmCore.getWarningLane();
		int lockedLane = _rhythmCore.getLockedLane();

		float sFar = S_FAR;
		float uFar = 0.0f;
		float sBottom = scaleFromT(1.13f);
		float uBottom = uFromScale(sBottom);

		// 1. Fondos trapezoidales por carril (warning, locked o tecla presionada)
		for (int lane = 0; lane < 6; lane++) {
			float xiL = -1.0f + lane * (2.0f / 6.0f);
			float xiR = xiL + (2.0f / 6.0f);

			boolean keyDown = !_rhythmCore.isPaused() && (
							  KBInput.isKeyDown(Config.RhythmButtons[lane]) ||
							  KBInput.isKeyDown(Config.RhythmAltButtons[lane]));

			int fillCol = (lane % 2 == 0)
				? g.color(20, 22, 34, 115)
				: g.color(28, 30, 44, 115);

			if (lane == lockedLane) {
				fillCol = g.color(185, 25, 40, 125);
			} else if (lane == warningLane) {
				float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() * 0.02));
				fillCol = g.color(255, 165, 30, 45 + pulse * 85);
			} else if (keyDown) {
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

		// 2. Líneas horizontales (arcos de beat) viajando a la misma velocidad exacta que las notas
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
			drawPerspectiveArc(g, uArc, sArc, 2.0f, g.color(150, 155, 175, alpha), downbeat ? 1.3f : 1.0f);
		}

		// 3. 7 líneas divisorias de carriles que divergen hacia la cámara
		for (int edge = 0; edge <= 6; edge++) {
			float xi = -1.0f + edge * (2.0f / 6.0f);
			float xTop = projectX(xi, sFar);
			float yTop = projectY(xi, uFar, sFar);
			float xBot = projectX(xi, sBottom);
			float yBot = projectY(xi, uBottom, sBottom);

			boolean outer = (edge == 0 || edge == 6);
			g.stroke(outer ? g.color(210, 215, 230, 195) : g.color(195, 200, 220, 145));
			g.strokeWeight(outer ? 1.5f : 1.0f);
			g.line(xTop, yTop, xBot, yBot);
		}

		// 4. Línea blanca principal (arco de impacto t = 1.0, S = 1.0) pulsando al ritmo
		float hitWeight = 1.8f + beatImpulse * 1.1f;
		drawPerspectiveArc(g, 1.0f, 1.0f, 2.15f, g.color(255, 255, 255, 245), hitWeight);
		g.strokeWeight(1.0f);
	}

	private float getApproachTimeMs() {
		return 1400.0f / Math.max(0.08f, _scrollSpeed / 0.22f);
	}

	/**
	 * Calcula la posición actual dentro del compás según los TimingPoints del beatmap (.osu).
	 * Retorna { beatPos, beatPhase (0..1), beatInMeasure (0..3), beatLengthMs, tpTime }.
	 */
	private float[] computeBeatState(int songTimeMs) {
		int tpTime = 0;
		float beatLength = 500.0f; // 120 BPM fallback

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

	/**
	 * Barra de ritmo bajo la vida del jefe: muestra el pulso del beat y el avance del compás 4/4.
	 */
	private void drawRhythmBeatBar(PGraphics g, int songTimeMs) {
		float[] beat = computeBeatState(songTimeMs);
		float beatPos = beat[0];
		float beatPhase = beat[1];
		int beatInMeasure = (int) beat[2];

		float barX = 175.0f;
		float barY = 26.0f;
		float barW = 130.0f;
		float barH = 5.0f;
		float beatImpulse = (float) Math.pow(1.0f - beatPhase, 2.2);
		boolean downbeat = (beatInMeasure == 0);

		g.noStroke();
		g.fill(18, 16, 30, 215);
		g.rect(barX, barY, barW, barH);

		// Progreso continuo dentro del compás de 4 tiempos (0..1)
		float measureProgress = PApplet.constrain((beatInMeasure + beatPhase) / 4.0f, 0.0f, 1.0f);
		int fillCol = downbeat
			? g.color(255, 225, 110, (int) (185 + beatImpulse * 70))
			: g.color(95, 215, 255, (int) (155 + beatImpulse * 90));
		g.fill(fillCol);
		g.rect(barX, barY, barW * measureProgress, barH);

		// Marcas de los 4 tiempos del compás + destello de pulso central
		g.stroke(255, 255, 255, 90);
		for (int b = 1; b < 4; b++) {
			float mx = barX + barW * (b / 4.0f);
			g.line(mx, barY, mx, barY + barH);
		}

		// Cursor brillante que recorre la barra al ritmo de la canción
		float cursorX = barX + barW * measureProgress;
		g.noStroke();
		g.fill(255, 255, 255, (int) (180 + beatImpulse * 75));
		g.rect(cursorX - 1.5f, barY - 1.0f, 3.0f, barH + 2.0f);

		// Pulso simétrico en los extremos de la barra en cada beat
		float wingW = 3.0f + beatImpulse * 5.0f;
		g.fill(downbeat ? g.color(255, 225, 110, (int) (130 + beatImpulse * 125))
		                : g.color(110, 225, 255, (int) (110 + beatImpulse * 125)));
		g.rect(barX - wingW - 2.0f, barY, wingW, barH);
		g.rect(barX + barW + 2.0f, barY, wingW, barH);

		// Borde de la barra
		g.noFill();
		g.stroke(220, 230, 255, 130);
		g.rect(barX, barY, barW, barH);
	}

	/**
	 * Dibuja una elipse en perspectiva real (escorzada y orientada al ángulo de la pista)
	 * para que tanto los receptores como las notas luzcan apoyados en el suelo 3D y no planos.
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

	private void drawPerspectiveReceptors(PGraphics g) {
		int lockedLane = _rhythmCore.getLockedLane();
		int warningLane = _rhythmCore.getWarningLane();
		boolean isCritical = _rhythmCore.getCombo() > 7;
		float tiltRatio = tiltRatioFromU(1.0f);

		for (int lane = 0; lane < 6; lane++) {
			float xi = laneToXi(lane);
			float cx = projectX(xi, 1.0f);
			float cy = projectY(xi, 1.0f, 1.0f);
			float ang = arcTangentAngle(xi, 1.0f);

			boolean keyDown = !_rhythmCore.isPaused() && (
							  KBInput.isKeyDown(Config.RhythmButtons[lane]) ||
							  KBInput.isKeyDown(Config.RhythmAltButtons[lane]));

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

			// X roja si el carril esta bloqueado
			if (lane == lockedLane) {
				g.stroke(255, 120, 130);
				g.strokeWeight(1.5f);
				g.line(cx - 5, cy - 3, cx + 5, cy + 3);
				g.line(cx - 5, cy + 3, cx + 5, cy - 3);
				g.strokeWeight(1.0f);
			}
		}
	}

	private void drawPerspectiveNotes(PGraphics g, int songTimeMs) {
		long[] notes = _rhythmCore.getNotes();
		if (notes == null) return;

		int lockedLane = _rhythmCore.getLockedLane();
		float approachTimeMs = getApproachTimeMs();

		for (int i = 0; i < notes.length; i++) {
			long packed = notes[i];
			int lane = NotePacker.getLane(packed);
			if (lane == lockedLane) continue;

			byte noteType = NotePacker.getNoteType(packed);
			long startTime = NotePacker.getStartTime(packed);
			long duration = NotePacker.getDuration(packed);

			boolean isBeingHeld = _rhythmCore.isHoldingLane(lane) && _rhythmCore.getHoldingNoteIndex(lane) == i;
			if (_rhythmCore.isNoteProcessed(i) && !isBeingHeld) continue;

			float tHead = 1.0f - ((startTime - songTimeMs) / approachTimeMs);
			float tTail = 1.0f - (((startTime + duration) - songTimeMs) / approachTimeMs);

			if (tTail > 1.13f || tHead < 0.0f) continue;

			boolean keyDown = !_rhythmCore.isPaused() && (
							  KBInput.isKeyDown(Config.RhythmButtons[lane]) ||
							  KBInput.isKeyDown(Config.RhythmAltButtons[lane]));
			float targetXi = laneToXi(lane);

			// Trayectoria para notas con cambio de carril (SHIFT_UP / SHIFT_DOWN)
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

			// 1. Hold note ribbon en perspectiva (azul y transparente; se hunde al mantener la tecla)
			if (duration > 0) {
				float segStart = Math.max(0.0f, tTail);
				float segEnd = isBeingHeld ? 1.0f : Math.min(1.12f, tHead);
				boolean sinkTail = isBeingHeld || (keyDown && segEnd >= 0.98f);
				if (segEnd > segStart) {
					final int slices = 8;
					g.noStroke();
					g.fill(45, 165, 255, 115);
					for (int sIdx = 0; sIdx < slices; sIdx++) {
						float ta = segStart + (segEnd - segStart) * (sIdx / (float) slices);
						float tb = segStart + (segEnd - segStart) * ((sIdx + 1) / (float) slices);
						float sa = scaleFromT(ta);
						float sb = scaleFromT(tb);
						float ua = uFromScale(sa);
						float ub = uFromScale(sb);
						float sinkA = sinkTail ? PRESS_SINK_PX * PApplet.constrain((ta - 0.75f) / 0.25f, 0.0f, 1.0f) : 0.0f;
						float sinkB = sinkTail ? PRESS_SINK_PX * PApplet.constrain((tb - 0.75f) / 0.25f, 0.0f, 1.0f) : 0.0f;
						float halfLaneXi = 0.075f;
						g.quad(
							projectX(targetXi - halfLaneXi, sa), projectY(targetXi - halfLaneXi, ua, sa) + sinkA,
							projectX(targetXi + halfLaneXi, sa), projectY(targetXi + halfLaneXi, ua, sa) + sinkA,
							projectX(targetXi + halfLaneXi, sb), projectY(targetXi + halfLaneXi, ub, sb) + sinkB,
							projectX(targetXi - halfLaneXi, sb), projectY(targetXi - halfLaneXi, ub, sb) + sinkB
						);
					}
				}
			}

			if (!isBeingHeld && tHead >= 0.0f && tHead <= 1.12f) {
				float s = scaleFromT(tHead);
				float u = uFromScale(s);
				float cx = projectX(drawXi, s);
				float cy = projectY(drawXi, u, s);
				if (keyDown && tHead >= 0.85f) {
					cy += PRESS_SINK_PX * PApplet.constrain((tHead - 0.85f) / 0.15f, 0.0f, 1.0f);
				}
				float ang = arcTangentAngle(drawXi, s);
				float tiltRatio = tiltRatioFromU(u);

				// Indicador en el receptor para ataques a distancia (RANGED_ORIGIN)
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

				// Indicador direccional escorzado para SHIFT_UP / SHIFT_DOWN
				if (noteType == NotePacker.NOTE_SHIFT_UP || noteType == NotePacker.NOTE_SHIFT_DOWN) {
					float dir = (noteType == NotePacker.NOTE_SHIFT_UP) ? -1.0f : 1.0f;
					g.stroke(15, 20, 28);
					g.strokeWeight(1.2f);
					g.line(cx + rx * 0.35f * dir, cy, cx - rx * 0.25f * dir, cy - ry * 0.32f);
					g.line(cx + rx * 0.35f * dir, cy, cx - rx * 0.25f * dir, cy + ry * 0.32f);
					g.strokeWeight(1.0f);
				}
			}
		}
	}

	@Override
	protected void onDialogLoadRequest(String scenePath) {
		String absPath = ResourcesManager.getAbsPathFromProject(scenePath);
		Engine.changeScene(new ROTNScene(absPath));
	}

	@Override
	public void close() {
		super.close();
		Audio.stopTransientBGM();
	}
}
