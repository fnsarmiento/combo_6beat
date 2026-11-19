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
import game.Config;
import game.core.GameState;
import game.core.db.CustomSongRepository;
import game.core.db.SaveRepository;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics;
import processing.core.PImage;
import processing.data.XML;
import processing.event.KeyEvent;

public class InitScene extends Scene {

	public enum ViewMode { MAIN, SAVE_SELECT, CAMPAIGN_SELECT, SONG_SELECT, SETTINGS }

	private static class SongEntry {
		String sceneAbsPath = "";
		String title = "Cancion";
		String artist = "";
		String difficulty = "6K";
		int noteCount = 0;
		String bossName = "Jefe";
		String bossSpecies = "";
		int bossHp = 450;
		int attackDamage = 12;
		int hitDamage = 6;
		float scrollSpeed = 0.22f;
	}

	private ViewMode _viewMode;
	private int _mainMenuIdx = 0;
	private int _saveRowIdx = 0; // 0..3 slots, 4 = volver
	private int _saveFocusCol = 0; // 0 = partida, 1 = borrar
	private int _confirmDeleteSlot = -1;
	private int _pendingNewGameSlot = 0;
	private int _campaignFocusIdx = 0; // 0 = Clásica (izq), 1 = Nueva (der), 2 = Volver
	private static int _savedSongTab = 0;
	private int _songTab = _savedSongTab; // 0 = Canciones Custom (BD), 1 = Jefes Historia
	private int _selectedCustomIdx = 0;
	private int _customScrollOffset = 0;
	private int _selectedSongIdx = 0;
	private int _storyScrollOffset = 0;
	private int _songFocusCol = 0; // 0 = lista de canciones, 1 = controles derechos
	private int _songRightIdx = 0; // 0 = Chip, 1 = Jefe, 2 = Cura, 3 = Velocidad, 4 = Iniciar, 5 = Volver
	private int _settingsFocusIdx = 0; // 0..6 izq, 7..12 ritmo, 13..17 rpg
	private static int _savedSettingsFocusIdx = 0;
	private int _songChipIdx = 0; // 0=Ninguno, 1=+10ms, 2=-7.5ms, 3=Escudo
	private boolean _songInvincibleMode = true;
	private int _songSpeedIdx = 1; // 0=0.85x, 1=1.0x, 2=1.25x
	private final String[] _chipIds = {
		"none",
		"powerup_reaction_plus",
		"powerup_reaction_minus",
		"powerup_invincibility"
	};
	private final float[] _speedOptions = { 0.85f, 1.0f, 1.25f };

	// Estado de rebinding de teclas en Settings
	// _rebindCategory: 0=ninguno, 1=Rhythm, 2=RPG
	private int _rebindCategory = 0;
	private int _rebindIndex = -1;
	private boolean _editingAltControls = false;
	private boolean _ignoreNavOnce = false;

	// Detección de cambio de resolución para reconstruir ControlP5 con el nuevo tamaño
	private int _builtAppWidth = 0;
	private int _builtAppHeight = 0;

	// Animación del fondo del menú (480x270)
	private float _animTime = 0.0f;
	private final float[] _starX = new float[28];
	private final float[] _starY = new float[28];
	private final float[] _starPhase = new float[28];

	// Catálogo escaneado de resources/songs/*.scene (Historia) y CANCIONES_PERSONALIZADAS (SQLite)
	private final List<SongEntry> _songs = new ArrayList<>();
	private final List<CustomSongRepository.CustomSongSummary> _customSongs = new ArrayList<>();
	private final Map<String, PImage> _coverCache = new HashMap<>();
	private volatile boolean _importDialogBusy = false;
	private volatile boolean _pendingCustomRefresh = false;
	private volatile String _songStatusMsg = "";

	// Controladores ControlP5 agrupados por vista
	private Group _mainGroup;
	private final Button[] _mainBtns = new Button[4];

	private Group _saveSelectGroup;
	private final Button[] _saveSlotBtns = new Button[GameState.SAVE_SLOT_COUNT];
	private final Button[] _saveDeleteBtns = new Button[GameState.SAVE_SLOT_COUNT];
	private Button _saveBackBtn;
	private Textlabel _saveInfoLbl;

	private Group _campaignSelectGroup;
	private Textlabel _campHeaderLbl;
	private Button _campClassicBtn;
	private Button _campExploreBtn;
	private Button _campBackBtn;

	private Group _songsGroup;
	private Button _songTabCustomBtn;
	private Button _songTabStoryBtn;
	private final Button[] _songListBtns = new Button[5];
	private Button _songImportBtn;
	private Button _songRescanBtn;
	private Textlabel _songListFooterLbl;
	private Textarea _songInfoArea;
	private Button _songChipBtn;
	private Button _songInvincibleBtn;
	private Button _songHealBtn;
	private Button _songSpeedBtn;
	private Button _songPlayBtn;
	private Button _songBackBtn;

	private Group _settingsGroup;
	private Textlabel _setBgmLbl;
	private Button _setBgmBarFill;
	private Textlabel _setSfxLbl;
	private Button _setSfxBarFill;
	private Button _setMuteBtn;
	private Button _setResBtn;
	private Button _setAltModeBtn;
	private Button _setResetBtn;
	private Button _setBackBtn;
	private final Button[] _setRhythmBtns = new Button[6];
	private final Button[] _setRpgBtns = new Button[6];

	private static final String[] MAIN_LABELS = {
		"JUGAR (HUB)",
		"SELECTOR DE CANCIONES",
		"CONFIGURACION",
		"SALIR"
	};

	public InitScene() { this(ViewMode.MAIN); }

	public InitScene(ViewMode initialView) {
		this._viewMode = (initialView != null) ? initialView : ViewMode.MAIN;
		if (this._viewMode == ViewMode.SETTINGS) {
			this._settingsFocusIdx = _savedSettingsFocusIdx;
		} else {
			_savedSettingsFocusIdx = 0;
			this._settingsFocusIdx = 0;
		}
	}

	@Override
	public void setup() {
		Engine.ensureVirtualScreenSize((int) BASE_W, (int) BASE_H);
		GameState.init();
		super.setup();

		var app = Engine.getApp();
		_builtAppWidth = app.width;
		_builtAppHeight = app.height;

		initBackgroundStars();
		scanSongsDirectory();
		refreshCustomSongs(true);
		syncChipSelectionFromState();
		setupMainUI();
		setupSaveSelectUI();
		setupCampaignSelectUI();
		setupSongSelectUI();
		setupSettingsUI();
		switchView(_viewMode);
	}

	private void initBackgroundStars() {
		for (int i = 0; i < _starX.length; i++) {
			_starX[i] = ((i * 73 + 19) % (int) (BASE_W - 12)) + 6;
			_starY[i] = ((i * 37 + 11) % 98) + 6;
			_starPhase[i] = i * 0.75f;
		}
	}

	private void syncChipSelectionFromState() {
		_songChipIdx = 0;
		for (int i = 0; i < _chipIds.length; i++) {
			if (_chipIds[i].equals(GameState.equippedPowerUp)) {
				_songChipIdx = i;
				break;
			}
		}
		_songSpeedIdx = 1;
		for (int i = 0; i < _speedOptions.length; i++) {
			if (Math.abs(_speedOptions[i] - GameState.songSpeedMultiplier) < 0.02f) {
				_songSpeedIdx = i;
				break;
			}
		}
	}

	private void scanSongsDirectory() {
		_songs.clear();
		File songsDir = new File(ResourcesManager.getAbsPathFromProject("resources/songs"));
		if (!songsDir.exists() || !songsDir.isDirectory()) return;

		File[] files = songsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".scene"));
		if (files == null) return;
		Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

		for (File sceneFile : files) {
			SongEntry entry = parseSongEntry(sceneFile);
			if (entry != null) {
				_songs.add(entry);
			}
		}
		if (_selectedSongIdx >= _songs.size()) {
			_selectedSongIdx = Math.max(0, _songs.size() - 1);
		}
	}

	private SongEntry parseSongEntry(File sceneFile) {
		try {
			XML xml = Engine.getApp().loadXML(sceneFile.getAbsolutePath());
			if (xml == null) return null;

			SongEntry entry = new SongEntry();
			entry.sceneAbsPath = sceneFile.getAbsolutePath();
			entry.title = sceneFile.getName().replace(".scene", "");

			XML songNode = xml.getChild("Song");
			String osuRel = "";
			if (songNode != null) {
				osuRel = songNode.getString("file", "");
				entry.scrollSpeed = songNode.getFloat("scroll-speed", 0.22f);
			}

			XML bossNode = xml.getChild("Boss");
			if (bossNode != null) {
				entry.bossName = bossNode.getString("name", entry.bossName);
				entry.bossSpecies = bossNode.getString("species", "");
				entry.bossHp = bossNode.getInt("max-hp", entry.bossHp);
				entry.attackDamage = bossNode.getInt("attack-damage", entry.attackDamage);
				entry.hitDamage = bossNode.getInt("hit-damage", entry.hitDamage);
			}

			if (!osuRel.isEmpty()) {
				File osuFile = resolveOsuFile(sceneFile, osuRel);
				if (osuFile != null && osuFile.exists()) {
					readOsuSummary(osuFile, entry);
				}
			}
			return entry;
		} catch (Exception e) {
			return null;
		}
	}

	private File resolveOsuFile(File sceneFile, String relPath) {
		File direct = new File(ResourcesManager.getAbsPathFromProject("resources/" + relPath));
		if (direct.exists()) return direct;
		File parentRel = new File(sceneFile.getParentFile(), relPath);
		if (parentRel.exists()) return parentRel;
		return new File(ResourcesManager.getAbsPathFromProject(relPath));
	}

	private void readOsuSummary(File osuFile, SongEntry entry) {
		try (BufferedReader br = new BufferedReader(new FileReader(osuFile))) {
			String line;
			String section = "";
			int notes = 0;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("//")) continue;
				if (line.startsWith("[") && line.endsWith("]")) {
					section = line;
					continue;
				}
				if ("[Metadata]".equals(section)) {
					int idx = line.indexOf(':');
					if (idx > 0) {
						String k = line.substring(0, idx).trim();
						String v = line.substring(idx + 1).trim();
						switch (k) {
							case "Title" -> entry.title = v;
							case "Artist" -> entry.artist = v;
							case "Version" -> entry.difficulty = v;
						}
					}
				} else if ("[HitObjects]".equals(section)) {
					notes++;
				}
			}
			entry.noteCount = notes;
		} catch (Exception ignored) {}
	}

	// =========================================================================
	// Construcción UI ControlP5: Menú Principal
	// =========================================================================

	private void setupMainUI() {
		PApplet app = Engine.getApp();
		ControlFont fontTitle = makeFont(22.0f, true);
		ControlFont fontBtn = makeFont(9.0f, false);

		_mainGroup = addUIGroup("ui_main_group", 0, 0, BASE_W, BASE_H);
		Button titleBanner = addUIBox("ui_menu_banner", _mainGroup, 85, 18, 310, 50, app.color(14, 10, 26, 200));
		titleBanner.setLabel("Combo 6 Beat");
		titleBanner.setColorLabel(app.color(255, 225, 130));
		if (fontTitle != null) titleBanner.setFont(fontTitle);
		titleBanner.getCaptionLabel().align(PConstants.CENTER, PConstants.CENTER);

		float btnW = 186.0f, btnH = 26.0f, btnX = (BASE_W - btnW) * 0.5f, startY = 92.0f, gap = 10.0f;
		for (int i = 0; i < _mainBtns.length; i++) {
			final int idx = i;
			_mainBtns[i] = addUIButton("ui_main_btn_" + i, _mainGroup, MAIN_LABELS[i],
				btnX, startY + i * (btnH + gap), btnW, btnH,
				app.color(24, 28, 46, 230), app.color(50, 95, 165, 245), fontBtn, () -> {
					_mainMenuIdx = idx;
					activateMainButton(idx);
				});
		}
	}

	private void activateMainButton(int idx) {
		switch (idx) {
			case 0 -> switchView(ViewMode.SAVE_SELECT);
			case 1 -> switchView(ViewMode.SONG_SELECT);
			case 2 -> switchView(ViewMode.SETTINGS);
			case 3 -> Engine.getApp().exit();
		}
	}

	// =========================================================================
	// Construcción UI ControlP5: Cargador de 4 Partidas
	// =========================================================================

	private void setupSaveSelectUI() {
		PApplet app = Engine.getApp();
		ControlFont fontTitle = makeFont(12.0f, true);
		ControlFont fontMed = makeFont(7.5f, false);
		ControlFont fontSmall = makeFont(6.5f, false);

		_saveSelectGroup = addUIGroup("ui_save_group", 0, 0, BASE_W, BASE_H);
		addUIBox("ui_save_panel_bg", _saveSelectGroup, 26, 16, BASE_W - 52, BASE_H - 32, app.color(14, 16, 28, 228));
		addUILabel("ui_save_header", _saveSelectGroup, "SELECCIONAR PARTIDA", 38, 24, app.color(255, 225, 130), fontTitle);

		for (int i = 0; i < _saveSlotBtns.length; i++) {
			final int slot = i;
			int rowY = 50 + i * 36;
			_saveSlotBtns[i] = addUIButton("ui_save_slot_btn_" + i, _saveSelectGroup, "", 38, rowY, 326, 30,
				app.color(26, 32, 48), app.color(50, 95, 165), fontSmall, () -> {
					_saveRowIdx = slot;
					_saveFocusCol = 0;
					launchSaveSlot(slot);
				});

			_saveDeleteBtns[i] = addUIButton("ui_save_del_btn_" + i, _saveSelectGroup, "Borrar", 370, rowY, 72, 30,
				app.color(75, 30, 42), app.color(145, 48, 66), fontSmall, () -> {
					_saveRowIdx = slot;
					_saveFocusCol = 1;
					deleteSaveSlotAction(slot);
				});
		}

		_saveInfoLbl = addUILabel("ui_save_info_lbl", _saveSelectGroup, "", 38, 198, app.color(175, 200, 235), fontSmall);

		_saveBackBtn = addUIButton("ui_save_back_btn", _saveSelectGroup, "VOLVER AL MENU", 140, 214, 200, 24,
			app.color(85, 36, 48), app.color(130, 52, 70), fontMed, () -> switchView(ViewMode.MAIN));
	}

	private void launchSaveSlot(int slot) {
		_confirmDeleteSlot = -1;
		GameState.launchedFromMenu = false;
		GameState.isDebugSession = false;
		GameState.songSpeedMultiplier = 1.0f;
		if (!GameState.hasSaveFile(slot) || !GameState.loadGame(slot)) {
			GameState.startNewGame(slot, GameState.CampaignMode.CLASSIC);
		}
		Engine.changeScene(new HubScene());
	}

	private void setupCampaignSelectUI() {
		PApplet app = Engine.getApp();
		ControlFont fontTitle = makeFont(11.5f, true);
		ControlFont fontBtn = makeFont(9.0f, true);
		ControlFont fontMed = makeFont(7.5f, false);
		ControlFont fontSmall = makeFont(6.5f, false);

		_campaignSelectGroup = addUIGroup("ui_camp_group", 0, 0, BASE_W, BASE_H);
		addUIBox("ui_camp_panel_bg", _campaignSelectGroup, 26, 26, BASE_W - 52, BASE_H - 52, app.color(14, 16, 28, 232));
		_campHeaderLbl = addUILabel("ui_camp_header", _campaignSelectGroup, "NUEVA PARTIDA - ELIGE CAMPAÑA", 40, 38, app.color(255, 225, 130), fontTitle);

		// Lado izquierdo: Campaña normal actual
		addUIBox("ui_camp_left_box", _campaignSelectGroup, 40, 62, 192, 126, app.color(22, 30, 50, 220));
		_campClassicBtn = addUIButton("ui_camp_classic_btn", _campaignSelectGroup, "CAMPANA CLASICA", 48, 72, 176, 36,
			app.color(36, 68, 125), app.color(56, 108, 190), fontBtn, () -> {
				_campaignFocusIdx = 0;
				startCampaignInPendingSlot(GameState.CampaignMode.CLASSIC);
			});
		addUILabel("ui_camp_left_d1", _campaignSelectGroup, "Aventura normal actual:", 50, 120, app.color(200, 225, 255), fontSmall);
		addUILabel("ui_camp_left_d2", _campaignSelectGroup, "Hub -> Pantano -> Ruinas", 50, 136, app.color(150, 185, 225), fontSmall);
		addUILabel("ui_camp_left_d3", _campaignSelectGroup, "(Progresion clasica)", 50, 152, app.color(130, 160, 195), fontSmall);

		// Lado derecho: Nueva Campaña (Exploración tipo RE + Dash/Pulso)
		addUIBox("ui_camp_right_box", _campaignSelectGroup, 248, 62, 192, 126, app.color(20, 38, 42, 220));
		_campExploreBtn = addUIButton("ui_camp_explore_btn", _campaignSelectGroup, "NUEVA CAMPANA", 256, 72, 176, 36,
			app.color(32, 95, 82), app.color(48, 150, 128), fontBtn, () -> {
				_campaignFocusIdx = 1;
				startCampaignInPendingSlot(GameState.CampaignMode.EXPLORATION);
			});
		addUILabel("ui_camp_right_d1", _campaignSelectGroup, "Nivel unico estilo RE:", 258, 120, app.color(195, 255, 230), fontSmall);
		addUILabel("ui_camp_right_d2", _campaignSelectGroup, "Llaves, atajos y puzles", 258, 136, app.color(145, 225, 195), fontSmall);
		addUILabel("ui_camp_right_d3", _campaignSelectGroup, "SHIFT: Dash+Correr | SPACE: Pulso", 258, 152, app.color(255, 230, 145), fontSmall);

		_campBackBtn = addUIButton("ui_camp_back_btn", _campaignSelectGroup, "VOLVER A RANURAS", 140, 204, 200, 24,
			app.color(85, 36, 48), app.color(130, 52, 70), fontMed, () -> switchView(ViewMode.SAVE_SELECT));
	}

	private void startCampaignInPendingSlot(GameState.CampaignMode mode) {
		GameState.launchedFromMenu = false;
		GameState.isDebugSession = false;
		GameState.songSpeedMultiplier = 1.0f;
		GameState.startNewGame(_pendingNewGameSlot, mode);
		// TODO: lanzar VNScene de introducción del modo historia
		_savedSongTab = 1;
		switchView(ViewMode.SONG_SELECT);
	}

	private void deleteSaveSlotAction(int slot) {
		if (!GameState.hasSaveFile(slot)) {
			_confirmDeleteSlot = -1;
			syncSaveSelectUI();
			return;
		}
		if (_confirmDeleteSlot != slot) {
			_confirmDeleteSlot = slot;
			syncSaveSelectUI();
			return;
		}
		GameState.deleteSaveSlot(slot);
		_confirmDeleteSlot = -1;
		_saveFocusCol = 0;
		syncSaveSelectUI();
		syncMainUI();
	}

	// =========================================================================
	// Construcción UI ControlP5: Selector de Canciones (Custom BD vs Historia)
	// =========================================================================

	private Button _songCoverBox;
	private Textarea _songBadgeArea;

	private void setupSongSelectUI() {
		PApplet app = Engine.getApp();
		ControlFont fontTitle = makeFont(10.5f, true);
		ControlFont fontMed = makeFont(7.5f, false);
		ControlFont fontSmall = makeFont(6.5f, false);

		_songsGroup = addUIGroup("ui_songs_group", 0, 0, BASE_W, BASE_H);
		addUIBox("ui_songs_panel_bg", _songsGroup, 12, 10, BASE_W - 24, BASE_H - 20, app.color(14, 16, 28, 225));
		addUILabel("ui_songs_header", _songsGroup, "SELECTOR DE CANCIONES", 22, 15, app.color(255, 225, 130), fontTitle);

		_songTabCustomBtn = addUIButton("ui_song_tab_custom", _songsGroup, "CANCIONES CUSTOM", 220, 14, 116, 18,
			app.color(45, 95, 165), app.color(65, 130, 215), fontSmall, () -> setSongTab(0));
		_songTabStoryBtn = addUIButton("ui_song_tab_story", _songsGroup, "JEFES HISTORIA", 342, 14, 116, 18,
			app.color(28, 34, 52), app.color(55, 95, 165), fontSmall, () -> setSongTab(1));

		for (int i = 0; i < _songListBtns.length; i++) {
			final int visibleRow = i;
			_songListBtns[i] = addUIButton("ui_song_slot_" + i, _songsGroup, "", 22, 40 + i * 31, 188, 27,
				app.color(26, 30, 46), app.color(50, 95, 165), fontSmall, () -> {
					int actualIdx = (_songTab == 0 ? _customScrollOffset : _storyScrollOffset) + visibleRow;
					if (_songTab == 0) {
						if (actualIdx < _customSongs.size()) {
							_selectedCustomIdx = actualIdx;
							_songFocusCol = 0;
							syncSongSelectUI();
						}
					} else {
						if (actualIdx < _songs.size()) {
							_selectedSongIdx = actualIdx;
							_songFocusCol = 0;
							syncSongSelectUI();
						}
					}
				});
		}

		_songImportBtn = addUIButton("ui_song_import_btn", _songsGroup, "+ IMPORTAR .OSU", 22, 198, 122, 20,
			app.color(35, 95, 130), app.color(55, 140, 185), fontSmall, this::openImportOsuDialog);
		_songRescanBtn = addUIButton("ui_song_rescan_btn", _songsGroup, "ACTUALIZAR", 148, 198, 62, 20,
			app.color(32, 42, 66), app.color(55, 95, 155), fontSmall, () -> {
				refreshCustomSongs(true);
				_songStatusMsg = "Catalogo custom actualizado (" + _customSongs.size() + ").";
				syncSongSelectUI();
			});

		_songListFooterLbl = addUILabel("ui_song_footer_lbl", _songsGroup, "", 22, 224, app.color(175, 200, 235), fontSmall);

		_songInfoArea = addUITextarea("ui_song_info_area", _songsGroup, "Sin canciones", 220, 40, 164, 108,
			app.color(235, 240, 255), app.color(20, 24, 38), fontSmall);

		_songCoverBox = addUIBox("ui_song_cover_box", _songsGroup, 390, 40, 68, 68, app.color(20, 24, 38));
		_songBadgeArea = addUITextarea("ui_song_badge_area", _songsGroup, "", 390, 112, 68, 36,
			app.color(140, 225, 255), app.color(20, 24, 38), fontSmall);

		_songChipBtn = addUIButton("ui_song_chip_btn", _songsGroup, "Chip: Ninguno", 220, 154, 116, 18,
			app.color(32, 42, 66), app.color(55, 95, 155), fontSmall, () -> {
				_songFocusCol = 1;
				_songRightIdx = 0;
				_songChipIdx = (_songChipIdx + 1) % _chipIds.length;
				syncSongSelectUI();
			});

		_songInvincibleBtn = addUIButton("ui_song_invincible_btn", _songsGroup, "Jefe: Invencible", 342, 154, 116, 18,
			app.color(32, 42, 66), app.color(55, 95, 155), fontSmall, () -> {
				_songFocusCol = 1;
				_songRightIdx = 1;
				_songInvincibleMode = !_songInvincibleMode;
				syncSongSelectUI();
			});

		_songHealBtn = addUIButton("ui_song_heal_btn", _songsGroup, "Cura: Optimo", 220, 176, 116, 18,
			app.color(32, 42, 66), app.color(55, 95, 155), fontSmall, () -> {
				_songFocusCol = 1;
				_songRightIdx = 2;
				GameState.toggleHealStrategy();
				syncSongSelectUI();
			});

		_songSpeedBtn = addUIButton("ui_song_speed_btn", _songsGroup, "Velocidad: 1.00x", 342, 176, 116, 18,
			app.color(32, 42, 66), app.color(55, 95, 155), fontSmall, () -> {
				_songFocusCol = 1;
				_songRightIdx = 3;
				_songSpeedIdx = (_songSpeedIdx + 1) % _speedOptions.length;
				syncSongSelectUI();
			});

		_songPlayBtn = addUIButton("ui_song_play_btn", _songsGroup, "INICIAR COMBATE", 220, 202, 238, 24,
			app.color(35, 125, 85), app.color(55, 170, 115), fontMed, () -> {
				_songFocusCol = 1;
				_songRightIdx = 4;
				launchSelectedSong();
			});

		_songBackBtn = addUIButton("ui_song_back_btn", _songsGroup, "VOLVER AL MENU", 220, 231, 238, 19,
			app.color(85, 36, 48), app.color(130, 52, 70), fontSmall, () -> switchView(ViewMode.MAIN));
	}

	private void setSongTab(int tab) {
		_songTab = (tab == 1) ? 1 : 0;
		_savedSongTab = _songTab;
		_songFocusCol = 0;
		_songStatusMsg = "";
		syncSongSelectUI();
	}

	private void refreshCustomSongs(boolean syncFolder) {
		if (syncFolder) {
			CustomSongRepository.syncCustomSongsFolder();
		}
		_customSongs.clear();
		_customSongs.addAll(CustomSongRepository.listAll());
		if (_selectedCustomIdx >= _customSongs.size()) {
			_selectedCustomIdx = Math.max(0, _customSongs.size() - 1);
		}
		ensureSelectionVisible();
	}

	private void ensureSelectionVisible() {
		int cap = _songListBtns.length;
		if (_songTab == 0) {
			if (_selectedCustomIdx < _customScrollOffset) _customScrollOffset = _selectedCustomIdx;
			else if (_selectedCustomIdx >= _customScrollOffset + cap) _customScrollOffset = _selectedCustomIdx - cap + 1;
			_customScrollOffset = Math.max(0, Math.min(_customScrollOffset, Math.max(0, _customSongs.size() - cap)));
		} else {
			if (_selectedSongIdx < _storyScrollOffset) _storyScrollOffset = _selectedSongIdx;
			else if (_selectedSongIdx >= _storyScrollOffset + cap) _storyScrollOffset = _selectedSongIdx - cap + 1;
			_storyScrollOffset = Math.max(0, Math.min(_storyScrollOffset, Math.max(0, _songs.size() - cap)));
		}
	}

	private void openImportOsuDialog() {
		if (_importDialogBusy) return;
		_importDialogBusy = true;
		_songStatusMsg = "Abriendo selector de archivo .osu...";
		syncSongSelectUI();

		Thread picker = new Thread(() -> {
			File chosen = pickOsuFileNative();
			if (chosen != null) {
				try {
					CustomSongRepository.CustomSongSummary imported = CustomSongRepository.importExternalOsu(chosen);
					if (imported != null) {
						_songStatusMsg = "Importada: " + imported.titulo;
					} else {
						_songStatusMsg = "No se pudo registrar el archivo .osu.";
					}
				} catch (Exception ex) {
					_songStatusMsg = "Error importando: " + ex.getMessage();
				}
				_pendingCustomRefresh = true;
			} else {
				_songStatusMsg = "Importacion cancelada.";
				_pendingCustomRefresh = true;
			}
			_importDialogBusy = false;
		}, "custom-osu-picker");
		picker.setDaemon(true);
		picker.start();
	}

	private File pickOsuFileNative() {
		String startDir = ResourcesManager.getAbsPathFromProject("resources/songs");
		try {
			ProcessBuilder pb = new ProcessBuilder(
				"zenity", "--file-selection",
				"--title=Importar beatmap .osu a Canciones Custom",
				"--filename=" + startDir + "/",
				"--file-filter=osu! beatmaps (*.osu) | *.osu"
			);
			Process p = pb.start();
			try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
				String line = br.readLine();
				int code = p.waitFor();
				if (code == 0 && line != null && !line.trim().isEmpty()) {
					return new File(line.trim());
				}
				if (code == 1) return null;
			}
		} catch (Exception ignored) {}

		try {
			ProcessBuilder pb = new ProcessBuilder(
				"kdialog", "--getopenfilename", startDir, "*.osu|osu! beatmaps (*.osu)"
			);
			Process p = pb.start();
			try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
				String line = br.readLine();
				int code = p.waitFor();
				if (code == 0 && line != null && !line.trim().isEmpty()) {
					return new File(line.trim());
				}
				if (code == 1) return null;
			}
		} catch (Exception ignored) {}

		try {
			JFileChooser chooser = new JFileChooser(startDir);
			chooser.setDialogTitle("Importar beatmap .osu");
			chooser.setFileFilter(new FileNameExtensionFilter("osu! beatmap (*.osu)", "osu"));
			if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
				return chooser.getSelectedFile();
			}
		} catch (Exception ignored) {}
		return null;
	}

	private PImage getScaledCoverImage(String relPath) {
		int targetW = Math.max(16, (int) scaleW(68));
		int targetH = Math.max(16, (int) scaleH(68));
		String cacheKey = (relPath == null ? "" : relPath) + "@" + targetW + "x" + targetH;
		if (_coverCache.containsKey(cacheKey)) {
			return _coverCache.get(cacheKey);
		}

		PApplet app = Engine.getApp();
		PImage result = null;
		if (relPath != null && !relPath.isEmpty()) {
			File f = new File(ResourcesManager.getAbsPathFromProject(relPath));
			if (f.exists() && f.isFile()) {
				PImage loaded = app.loadImage(f.getAbsolutePath());
				if (loaded != null && loaded.width > 0 && loaded.height > 0) {
					result = loaded.copy();
					result.resize(targetW, targetH);
				}
			}
		}
		if (result == null) {
			result = createFallbackCoverImage(targetW, targetH);
		}
		_coverCache.put(cacheKey, result);
		return result;
	}

	private PImage createFallbackCoverImage(int w, int h) {
		PApplet app = Engine.getApp();
		PImage img = app.createImage(w, h, PConstants.ARGB);
		img.loadPixels();
		float cx = w * 0.5f;
		float cy = h * 0.5f;
		float rOuter = Math.min(w, h) * 0.40f;
		float rInner = Math.min(w, h) * 0.13f;
		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				float dx = px - cx;
				float dy = py - cy;
				float dist = (float) Math.sqrt(dx * dx + dy * dy);
				int col;
				if (px < 2 || py < 2 || px >= w - 2 || py >= h - 2) {
					col = app.color(75, 110, 170);
				} else if (dist <= rInner) {
					col = app.color(245, 220, 125);
				} else if (dist <= rOuter) {
					col = (dist > rOuter - 3) ? app.color(115, 185, 255) : app.color(32, 42, 68);
				} else {
					col = app.color(18, 22, 34);
				}
				img.pixels[py * w + px] = col;
			}
		}
		img.updatePixels();
		return img;
	}

	private void launchSelectedSong() {
		GameState.ensureFreeplayPowerUps();
		GameState.equipPowerUp(_chipIds[_songChipIdx]);
		GameState.songSpeedMultiplier = _speedOptions[_songSpeedIdx];
		GameState.bossInvincibleMode = _songInvincibleMode;
		GameState.launchedFromMenu = true;
		_savedSongTab = _songTab;

		if (_songTab == 0) {
			if (_customSongs.isEmpty() || _selectedCustomIdx < 0 || _selectedCustomIdx >= _customSongs.size()) return;
			CustomSongRepository.CustomSongSummary custom = _customSongs.get(_selectedCustomIdx);
			CustomSongRepository.CustomSongSummary fresh = CustomSongRepository.getById(custom.idCancion);
			Engine.changeScene(new ROTNScene(fresh != null ? fresh : custom));
		} else {
			if (_songs.isEmpty() || _selectedSongIdx < 0 || _selectedSongIdx >= _songs.size()) return;
			SongEntry entry = _songs.get(_selectedSongIdx);
			Engine.changeScene(new ROTNScene(entry.sceneAbsPath));
		}
	}

	// =========================================================================
	// Construcción UI ControlP5: Settings (Audio / Video / Controles)
	// =========================================================================

	private void setupSettingsUI() {
		PApplet app = Engine.getApp();
		ControlFont fontTitle = makeFont(11.0f, true);
		ControlFont fontSmall = makeFont(6.5f, false);

		_settingsGroup = addUIGroup("ui_settings_group", 0, 0, BASE_W, BASE_H);
		addUIBox("ui_settings_bg", _settingsGroup, 12, 10, BASE_W - 24, BASE_H - 20, app.color(14, 16, 28, 228));
		addUILabel("ui_settings_header", _settingsGroup, "Configuracion", 22, 16, app.color(255, 225, 130), fontTitle);

		// --- Columna Izquierda: Audio + Video ---
		_setBgmLbl = addUILabel("ui_set_bgm_lbl", _settingsGroup, "BGM: 50%", 22, 38, app.color(220, 235, 255), fontSmall);
		addUIButton("ui_set_bgm_minus", _settingsGroup, "-", 22, 50, 22, 16, app.color(35, 45, 70), app.color(55, 85, 140), fontSmall,
			() -> { _settingsFocusIdx = 0; adjustBgmVolume(-0.1f); });
		addUIBox("ui_set_bgm_bar_bg", _settingsGroup, 48, 52, 146, 12, app.color(26, 30, 42));
		_setBgmBarFill = addUIBox("ui_set_bgm_bar_fill", _settingsGroup, 48, 52, 73, 12, app.color(80, 195, 255));
		addUIButton("ui_set_bgm_plus", _settingsGroup, "+", 198, 50, 22, 16, app.color(35, 45, 70), app.color(55, 85, 140), fontSmall,
			() -> { _settingsFocusIdx = 0; adjustBgmVolume(0.1f); });

		_setSfxLbl = addUILabel("ui_set_sfx_lbl", _settingsGroup, "SFX: 50%", 22, 74, app.color(220, 235, 255), fontSmall);
		addUIButton("ui_set_sfx_minus", _settingsGroup, "-", 22, 86, 22, 16, app.color(35, 45, 70), app.color(55, 85, 140), fontSmall,
			() -> { _settingsFocusIdx = 1; adjustSfxVolume(-0.1f); });
		addUIBox("ui_set_sfx_bar_bg", _settingsGroup, 48, 88, 146, 12, app.color(26, 30, 42));
		_setSfxBarFill = addUIBox("ui_set_sfx_bar_fill", _settingsGroup, 48, 88, 73, 12, app.color(95, 235, 165));
		addUIButton("ui_set_sfx_plus", _settingsGroup, "+", 198, 86, 22, 16, app.color(35, 45, 70), app.color(55, 85, 140), fontSmall,
			() -> { _settingsFocusIdx = 1; adjustSfxVolume(0.1f); });

		_setMuteBtn = addUIButton("ui_set_mute_btn", _settingsGroup, "MUTE GENERAL: OFF", 22, 110, 198, 20,
			app.color(32, 42, 66), app.color(55, 95, 155), fontSmall, () -> { _settingsFocusIdx = 2; toggleMuteSetting(); });

		addUILabel("ui_set_video_hdr", _settingsGroup, "Video", 22, 138, app.color(140, 215, 255), fontSmall);
		_setResBtn = addUIButton("ui_set_res_btn", _settingsGroup, "Resolucion: 1920x1080 (4x)", 22, 150, 198, 22,
			app.color(32, 42, 66), app.color(55, 95, 155), fontSmall, () -> { _settingsFocusIdx = 3; toggleWindowResolution(); });
		_setAltModeBtn = addUIButton("ui_set_alt_mode_btn", _settingsGroup, "CONTROLES: PRINCIPALES", 22, 176, 198, 22,
			app.color(32, 42, 66), app.color(55, 95, 155), fontSmall, () -> { _settingsFocusIdx = 4; toggleAltControlsMode(); });
		_setResetBtn = addUIButton("ui_set_reset_btn", _settingsGroup, "RESTAURAR CONTROLES", 22, 204, 198, 20,
			app.color(65, 52, 32), app.color(110, 88, 52), fontSmall, () -> { _settingsFocusIdx = 5; resetAllControlsSetting(); });
		_setBackBtn = addUIButton("ui_set_back_btn", _settingsGroup, "GUARDAR Y VOLVER", 22, 230, 198, 22,
			app.color(35, 115, 78), app.color(55, 160, 110), fontSmall, () -> {
				_settingsFocusIdx = 6;
				_rebindCategory = 0;
				Config.saveConfig(".config");
				switchView(ViewMode.MAIN);
			});

		// --- Columna Derecha: Rebinding de Teclas (C1 C4 / C2 C5 / C3 C6) ---
		addUILabel("ui_set_rhythm_hdr", _settingsGroup, "CARRILES RITMO 6K", 236, 38, app.color(140, 215, 255), fontSmall);
		for (int i = 0; i < 6; i++) {
			final int lane = i;
			int col = i / 3; // 0: C1..C3, 1: C4..C6
			int row = i % 3;
			_setRhythmBtns[i] = addUIButton("ui_set_rhythm_" + i, _settingsGroup,
				"C" + (i + 1) + ": " + Config.getKeyLabel(Config.RhythmButtons[i]),
				236 + col * 114, 52 + row * 25, 108, 21,
				app.color(28, 34, 52), app.color(50, 95, 165), fontSmall, () -> {
					_settingsFocusIdx = 7 + lane;
					_rebindCategory = 1;
					_rebindIndex = lane;
					syncSettingsUI();
				});
		}

		addUILabel("ui_set_rpg_hdr", _settingsGroup, "CONTROLES", 236, 136, app.color(140, 215, 255), fontSmall);
		for (int i = 0; i < _setRpgBtns.length; i++) {
			final int idx = i;
			_setRpgBtns[i] = addUIButton("ui_set_rpg_" + i, _settingsGroup, "",
				236 + (i % 2) * 114, 150 + (i / 2) * 24, 108, 20,
				app.color(28, 34, 52), app.color(50, 95, 165), fontSmall, () -> {
					_settingsFocusIdx = 13 + idx;
					_rebindCategory = 2;
					_rebindIndex = idx;
					syncSettingsUI();
				});
		}
	}

	private void adjustBgmVolume(float delta) {
		Config.BGMVolume = PApplet.constrain(Math.round((Config.BGMVolume + delta) * 10.0f) / 10.0f, 0.0f, 1.0f);
		Config.applyAudioConfig();
		Config.saveConfig(".config");
		syncSettingsUI();
	}

	private void adjustSfxVolume(float delta) {
		Config.SFXVolume = PApplet.constrain(Math.round((Config.SFXVolume + delta) * 10.0f) / 10.0f, 0.0f, 1.0f);
		Config.applyAudioConfig();
		Config.saveConfig(".config");
		syncSettingsUI();
	}

	private void toggleMuteSetting() {
		Config.isMuted = !Config.isMuted;
		Config.applyAudioConfig();
		Config.saveConfig(".config");
		syncSettingsUI();
	}

	private void toggleAltControlsMode() {
		_rebindCategory = 0;
		_rebindIndex = -1;
		_editingAltControls = !_editingAltControls;
		syncSettingsUI();
	}

	private void resetAllControlsSetting() {
		_rebindCategory = 0;
		_rebindIndex = -1;
		Config.resetDefaultControls();
		Config.saveConfig(".config");
		syncSettingsUI();
	}

	private void toggleWindowResolution() {
		_savedSettingsFocusIdx = _settingsFocusIdx;
		if (Config.windowWidth == 1920) {
			Config.windowWidth = 1280;
			Config.windowHeight = 720;
		} else {
			Config.windowWidth = 1920;
			Config.windowHeight = 1080;
		}
		Config.saveConfig(".config");
		Config.applyVideoConfig();
		Engine.changeScene(new InitScene(ViewMode.SETTINGS));
	}

	private void switchView(ViewMode mode) {
		_viewMode = mode;
		_rebindCategory = 0;
		_rebindIndex = -1;
		_confirmDeleteSlot = -1;
		if (mode == ViewMode.SAVE_SELECT) {
			_saveRowIdx = 0;
			_saveFocusCol = 0;
		} else if (mode == ViewMode.SONG_SELECT) {
			_songTab = _savedSongTab;
			_songFocusCol = 0;
			_songRightIdx = 0;
			refreshCustomSongs(false);
		} else if (mode == ViewMode.SETTINGS) {
			_settingsFocusIdx = _savedSettingsFocusIdx;
		}

		if (_mainGroup != null) {
			if (mode == ViewMode.MAIN) _mainGroup.show();
			else _mainGroup.hide();
		}
		if (_saveSelectGroup != null) {
			if (mode == ViewMode.SAVE_SELECT) _saveSelectGroup.show();
			else _saveSelectGroup.hide();
		}
		if (_campaignSelectGroup != null) {
			if (mode == ViewMode.CAMPAIGN_SELECT) _campaignSelectGroup.show();
			else _campaignSelectGroup.hide();
		}
		if (_songsGroup != null) {
			if (mode == ViewMode.SONG_SELECT) _songsGroup.show();
			else _songsGroup.hide();
		}
		if (_settingsGroup != null) {
			if (mode == ViewMode.SETTINGS) _settingsGroup.show();
			else _settingsGroup.hide();
		}

		syncMainUI();
		syncSaveSelectUI();
		syncCampaignSelectUI();
		syncSongSelectUI();
		syncSettingsUI();
	}

	private void syncCampaignSelectUI() {
		PApplet app = Engine.getApp();
		if (_campHeaderLbl != null) {
			_campHeaderLbl.setText("NUEVA PARTIDA (RANURA " + (_pendingNewGameSlot + 1) + ") - ELIGE MODO");
		}
		if (_campClassicBtn != null) {
			boolean f = (_campaignFocusIdx == 0);
			_campClassicBtn.setLabel((f ? "> " : "") + "CAMPANA CLASICA");
			_campClassicBtn.setColorBackground(f ? app.color(56, 112, 200) : app.color(32, 54, 96));
		}
		if (_campExploreBtn != null) {
			boolean f = (_campaignFocusIdx == 1);
			_campExploreBtn.setLabel((f ? "> " : "") + "NUEVA CAMPANA");
			_campExploreBtn.setColorBackground(f ? app.color(45, 155, 128) : app.color(28, 86, 74));
		}
		if (_campBackBtn != null) {
			boolean f = (_campaignFocusIdx == 2);
			_campBackBtn.setLabel((f ? "> " : "") + "VOLVER A RANURAS");
			_campBackBtn.setColorBackground(f ? app.color(145, 58, 78) : app.color(85, 36, 48));
		}
	}

	private void syncMainUI() {
		PApplet app = Engine.getApp();
		for (int i = 0; i < _mainBtns.length; i++) {
			if (_mainBtns[i] == null) continue;
			boolean sel = (i == _mainMenuIdx);
			_mainBtns[i].setLabel((sel ? "> " : "") + MAIN_LABELS[i]);
			_mainBtns[i].setColorBackground(
				sel ? app.color(45, 88, 155, 245) : app.color(24, 28, 46, 225)
			);
		}
	}

	private void syncSaveSelectUI() {
		PApplet app = Engine.getApp();
		for (int i = 0; i < _saveSlotBtns.length; i++) {
			GameState.SaveSlotSummary sum = GameState.getSaveSlotSummary(i);
			boolean rowFocused = (_saveRowIdx == i);
			boolean mainFocused = rowFocused && (_saveFocusCol == 0);
			boolean delFocused = rowFocused && (_saveFocusCol == 1);

			Button slotBtn = _saveSlotBtns[i];
			if (slotBtn != null) {
				String text;
				if (sum.exists) {
					text = "PARTIDA " + (i + 1) + " - " + sum.sceneLabel +
						   " | " + Math.round(sum.progressPercent) + "%" +
						   " | Jefes: " + sum.battlesWonCount +
						   " | Items: " + sum.itemCount;
				} else {
					text = "PARTIDA " + (i + 1) + " - [Vacio - Nueva Partida]";
				}
				slotBtn.setLabel((mainFocused ? "> " : "  ") + text);
				if (mainFocused) {
					slotBtn.setColorBackground(app.color(52, 108, 192));
				} else if (sum.exists) {
					slotBtn.setColorBackground(app.color(30, 44, 74));
				} else {
					slotBtn.setColorBackground(app.color(24, 28, 40));
				}
				slotBtn.setColorLabel(sum.exists ? app.color(245, 248, 255) : app.color(165, 175, 195));
			}

			Button delBtn = _saveDeleteBtns[i];
			if (delBtn != null) {
				if (!sum.exists) {
					delBtn.setLabel("---");
					delBtn.setColorBackground(app.color(24, 24, 32));
					delBtn.setColorLabel(app.color(95, 100, 115));
				} else {
					boolean confirming = (_confirmDeleteSlot == i);
					String label = confirming ? "Confirmar" : "Borrar";
					delBtn.setLabel((delFocused ? "> " : "") + label);
					if (confirming) {
						delBtn.setColorBackground(app.color(195, 45, 62));
						delBtn.setColorLabel(app.color(255, 245, 175));
					} else if (delFocused) {
						delBtn.setColorBackground(app.color(155, 48, 68));
						delBtn.setColorLabel(app.color(255));
					} else {
						delBtn.setColorBackground(app.color(75, 30, 42));
						delBtn.setColorLabel(app.color(235, 200, 210));
					}
				}
			}
		}

		if (_saveBackBtn != null) {
			boolean backFocused = (_saveRowIdx == GameState.SAVE_SLOT_COUNT);
			_saveBackBtn.setLabel((backFocused ? "> " : "") + "VOLVER AL MENU");
			_saveBackBtn.setColorBackground(backFocused ? app.color(145, 58, 78) : app.color(85, 36, 48));
		}

		if (_saveInfoLbl != null) {
			if (_confirmDeleteSlot >= 0) {
				_saveInfoLbl.setText("Pulsa Confirmar otra vez para borrar la Partida " + (_confirmDeleteSlot + 1) + ".");
				_saveInfoLbl.setColorValue(app.color(255, 155, 155));
			} else if (_saveRowIdx >= 0 && _saveRowIdx < GameState.SAVE_SLOT_COUNT && GameState.hasSaveFile(_saveRowIdx)) {
				List<SaveRepository.BattleRecordView> battles = SaveRepository.getBattleRecordsForSlot(_saveRowIdx);
				List<SaveRepository.ItemHistoryView> history = SaveRepository.getItemHistoryForSlot(_saveRowIdx, 1);
				String lastBoss = battles.isEmpty() ? "Ninguno" : (battles.get(0).bossName + " (" + battles.get(0).maxScore + " pts)");
				String lastEvent = history.isEmpty() ? "-" : (history.get(0).eventType + ": " + history.get(0).itemName);
				_saveInfoLbl.setText(
					"Ult. jefe: " + lastBoss +
					"  |  Historial: " + lastEvent
				);
				_saveInfoLbl.setColorValue(app.color(175, 220, 255));
			} else {
				_saveInfoLbl.setText("");
			}
		}
	}

	private void syncSongSelectUI() {
		PApplet app = Engine.getApp();
		boolean isCustomTab = (_songTab == 0);
		ensureSelectionVisible();

		if (_songTabCustomBtn != null) {
			_songTabCustomBtn.setLabel((isCustomTab ? "* " : "") + "CANCIONES CUSTOM");
			_songTabCustomBtn.setColorBackground(isCustomTab ? app.color(45, 95, 165) : app.color(26, 30, 46));
		}
		if (_songTabStoryBtn != null) {
			_songTabStoryBtn.setLabel((!isCustomTab ? "* " : "") + "JEFES HISTORIA");
			_songTabStoryBtn.setColorBackground(!isCustomTab ? app.color(45, 95, 165) : app.color(26, 30, 46));
		}

		if (isCustomTab) {
			if (_songImportBtn != null) _songImportBtn.show();
			if (_songRescanBtn != null) _songRescanBtn.show();

			for (int i = 0; i < _songListBtns.length; i++) {
				Button btn = _songListBtns[i];
				if (btn == null) continue;
				int idx = _customScrollOffset + i;
				if (idx < _customSongs.size()) {
					CustomSongRepository.CustomSongSummary entry = _customSongs.get(idx);
					boolean sel = (idx == _selectedCustomIdx);
					boolean focused = sel && (_songFocusCol == 0);
					String label = entry.titulo + " [" + entry.dificultad + "]";
					if (label.length() > 26) label = label.substring(0, 23) + "...";
					btn.setLabel((focused ? "> " : (sel ? "* " : "  ")) + label);
					btn.setColorBackground(
						focused ? app.color(55, 110, 195) : (sel ? app.color(36, 62, 108) : app.color(26, 30, 46))
					);
					btn.show();
				} else {
					btn.hide();
				}
			}

			if (_songInfoArea != null) {
				_songInfoArea.setSize(Math.max(1, (int) scaleW(164)), Math.max(1, (int) scaleH(108)));
				if (_customSongs.isEmpty()) {
					_songInfoArea.setText("No hay canciones custom.\nUsa '+ IMPORTAR .OSU' o coloca archivos en resources/songs/custom/");
				} else {
					CustomSongRepository.CustomSongSummary c = _customSongs.get(_selectedCustomIdx);
					_songInfoArea.setText(
						"Cancion: " + c.titulo + "\n" +
						"Artista: " + (c.artista.isEmpty() ? "-" : c.artista) + "\n" +
						"Dificultad: " + c.dificultad + " | Notas: " + c.cantidadNotas + "\n" +
						"Record: " + c.puntajeMasAlto + " pts (" + c.comboMasAlto + "x, " + String.format("%.1f%%", c.precisionMasAlta) + ")\n" +
						"Obj. Record: " + c.objetosRecord + "\n" +
						"Ultimo: " + c.puntajeUltimoIntento + " pts\n" +
						"Obj. Ultimo: " + c.objetosUltimoIntento
					);
				}
			}

			if (_songCoverBox != null && _songBadgeArea != null) {
				if (_customSongs.isEmpty()) {
					PImage fallback = getScaledCoverImage("");
					_songCoverBox.setSize(fallback.width, fallback.height).setImage(fallback).show();
					_songBadgeArea.setText("BPM: -\nJugadas: 0").show();
				} else {
					CustomSongRepository.CustomSongSummary c = _customSongs.get(_selectedCustomIdx);
					PImage cover = getScaledCoverImage(c.rutaPortada);
					_songCoverBox.setSize(cover.width, cover.height).setImage(cover).show();
					_songBadgeArea.setText(
						"BPM: " + String.format("%.1f", c.bpm) + "\n" +
						"Jugadas: " + c.vecesJugada
					).show();
				}
			}

			if (_songListFooterLbl != null) {
				if (!_songStatusMsg.isEmpty()) {
					_songListFooterLbl.setText(_songStatusMsg);
				} else if (_customSongs.isEmpty()) {
					_songListFooterLbl.setText("0 custom | TAB: Jefes Historia");
				} else {
					_songListFooterLbl.setText("Custom " + (_selectedCustomIdx + 1) + "/" + _customSongs.size() + " | TAB: Jefes");
				}
			}
		} else {
			if (_songImportBtn != null) _songImportBtn.hide();
			if (_songRescanBtn != null) _songRescanBtn.hide();
			if (_songCoverBox != null) _songCoverBox.hide();
			if (_songBadgeArea != null) _songBadgeArea.hide();

			for (int i = 0; i < _songListBtns.length; i++) {
				Button btn = _songListBtns[i];
				if (btn == null) continue;
				int idx = _storyScrollOffset + i;
				if (idx < _songs.size()) {
					SongEntry entry = _songs.get(idx);
					boolean sel = (idx == _selectedSongIdx);
					boolean focused = sel && (_songFocusCol == 0);
					String label = entry.bossName + " - " + entry.title;
					if (label.length() > 26) label = label.substring(0, 23) + "...";
					btn.setLabel((focused ? "> " : (sel ? "* " : "  ")) + label);
					btn.setColorBackground(
						focused ? app.color(55, 110, 195) : (sel ? app.color(36, 62, 108) : app.color(26, 30, 46))
					);
					btn.show();
				} else {
					btn.hide();
				}
			}

			if (_songInfoArea != null) {
				_songInfoArea.setSize(Math.max(1, (int) scaleW(238)), Math.max(1, (int) scaleH(108)));
				if (_songs.isEmpty()) {
					_songInfoArea.setText("No se encontraron archivos .scene en resources/songs/");
				} else {
					SongEntry e = _songs.get(_selectedSongIdx);
					String sp = e.bossSpecies.isEmpty() ? "" : " (" + e.bossSpecies + ")";
					_songInfoArea.setText(
						"Cancion: " + e.title + "\n" +
						"Artista: " + (e.artist.isEmpty() ? "-" : e.artist) + " [" + e.difficulty + "]\n" +
						"Notas: " + e.noteCount + "  |  Vel. base: " + String.format("%.2f", e.scrollSpeed) + "\n\n" +
						"Jefe: " + e.bossName + sp + "\n" +
						"HP Jefe: " + e.bossHp + "  |  Danio: " + e.attackDamage + " / Golpe: " + e.hitDamage
					);
				}
			}

			if (_songListFooterLbl != null) {
				if (_songs.isEmpty()) {
					_songListFooterLbl.setText("0 jefes | TAB: Canciones Custom");
				} else {
					_songListFooterLbl.setText("Jefes " + (_selectedSongIdx + 1) + "/" + _songs.size() + " | TAB: Custom");
				}
			}
		}

		boolean rightActive = (_songFocusCol == 1);
		if (_songChipBtn != null) {
			String chipId = _chipIds[_songChipIdx];
			String chipLabel = "none".equals(chipId) ? "Ninguno" : GameState.getItemName(chipId);
			boolean f = rightActive && (_songRightIdx == 0);
			_songChipBtn.setLabel((f ? "> " : "") + "Chip: " + chipLabel);
			_songChipBtn.setColorBackground(f ? app.color(55, 110, 195) : app.color(32, 42, 66));
		}
		if (_songInvincibleBtn != null) {
			boolean f = rightActive && (_songRightIdx == 1);
			_songInvincibleBtn.setLabel((f ? "> " : "") + "Jefe: " + (_songInvincibleMode ? "Invencible" : "Normal"));
			_songInvincibleBtn.setColorBackground(f ? app.color(55, 110, 195) : app.color(32, 42, 66));
		}
		if (_songHealBtn != null) {
			boolean f = rightActive && (_songRightIdx == 2);
			_songHealBtn.setLabel((f ? "> " : "") + "Cura: " + GameState.healStrategy.label);
			_songHealBtn.setColorBackground(f ? app.color(55, 110, 195) : app.color(32, 42, 66));
		}
		if (_songSpeedBtn != null) {
			boolean f = rightActive && (_songRightIdx == 3);
			_songSpeedBtn.setLabel((f ? "> " : "") + "Velocidad: " + String.format("%.2fx", _speedOptions[_songSpeedIdx]));
			_songSpeedBtn.setColorBackground(f ? app.color(55, 110, 195) : app.color(32, 42, 66));
		}
		if (_songPlayBtn != null) {
			boolean f = rightActive && (_songRightIdx == 4);
			_songPlayBtn.setLabel((f ? "> " : "") + "INICIAR COMBATE");
			_songPlayBtn.setColorBackground(f ? app.color(55, 175, 115) : app.color(35, 125, 85));
		}
		if (_songBackBtn != null) {
			boolean f = rightActive && (_songRightIdx == 5);
			_songBackBtn.setLabel((f ? "> " : "") + "VOLVER AL MENU");
			_songBackBtn.setColorBackground(f ? app.color(145, 58, 78) : app.color(85, 36, 48));
		}
	}

	private void syncSettingsUI() {
		PApplet app = Engine.getApp();

		if (_setBgmLbl != null) {
			boolean f = (_settingsFocusIdx == 0);
			_setBgmLbl.setText((f ? "> " : "") + "BGM: " + Math.round(Config.BGMVolume * 100.0f) + "%" + (f ? "  [< / >]" : ""));
			_setBgmLbl.setColorValue(f ? app.color(255, 225, 110) : app.color(220, 235, 255));
		}
		if (_setBgmBarFill != null) {
			int w = Math.max(1, Math.round(scaleW(146) * Config.BGMVolume));
			_setBgmBarFill.setSize(w, Math.max(1, (int) scaleH(12)));
			_setBgmBarFill.setColorBackground((_settingsFocusIdx == 0) ? app.color(130, 225, 255) : app.color(80, 195, 255));
		}

		if (_setSfxLbl != null) {
			boolean f = (_settingsFocusIdx == 1);
			_setSfxLbl.setText((f ? "> " : "") + "SFX: " + Math.round(Config.SFXVolume * 100.0f) + "%" + (f ? "  [< / >]" : ""));
			_setSfxLbl.setColorValue(f ? app.color(255, 225, 110) : app.color(220, 235, 255));
		}
		if (_setSfxBarFill != null) {
			int w = Math.max(1, Math.round(scaleW(146) * Config.SFXVolume));
			_setSfxBarFill.setSize(w, Math.max(1, (int) scaleH(12)));
			_setSfxBarFill.setColorBackground((_settingsFocusIdx == 1) ? app.color(145, 255, 195) : app.color(95, 235, 165));
		}

		if (_setMuteBtn != null) {
			boolean f = (_settingsFocusIdx == 2);
			_setMuteBtn.setLabel((f ? "> " : "") + "MUTE GENERAL: " + (Config.isMuted ? "ON" : "OFF"));
			_setMuteBtn.setColorBackground(
				f ? (Config.isMuted ? app.color(190, 65, 80) : app.color(55, 110, 195))
				  : (Config.isMuted ? app.color(145, 45, 58) : app.color(32, 42, 66))
			);
		}

		if (_setResBtn != null) {
			boolean f = (_settingsFocusIdx == 3);
			String tag = (Config.windowWidth == 1920) ? "1920x1080 (1080p - 4x)" : "1280x720 (720p)";
			_setResBtn.setLabel((f ? "> " : "") + "Resolucion: " + tag);
			_setResBtn.setColorBackground(f ? app.color(55, 110, 195) : app.color(32, 42, 66));
		}

		if (_setAltModeBtn != null) {
			boolean f = (_settingsFocusIdx == 4);
			_setAltModeBtn.setLabel((f ? "> " : "") + "CONTROLES: " + (_editingAltControls ? "ALTERNATIVOS" : "PRINCIPALES"));
			_setAltModeBtn.setColorBackground(
				f ? app.color(55, 110, 195)
				  : (_editingAltControls ? app.color(85, 60, 130) : app.color(32, 42, 66))
			);
		}

		if (_setResetBtn != null) {
			boolean f = (_settingsFocusIdx == 5);
			_setResetBtn.setLabel((f ? "> " : "") + "RESTAURAR CONTROLES");
			_setResetBtn.setColorBackground(f ? app.color(130, 102, 55) : app.color(65, 52, 32));
		}

		if (_setBackBtn != null) {
			boolean f = (_settingsFocusIdx == 6);
			_setBackBtn.setLabel((f ? "> " : "") + "GUARDAR Y VOLVER");
			_setBackBtn.setColorBackground(f ? app.color(55, 165, 110) : app.color(35, 115, 78));
		}

		int[] rhythmArr = _editingAltControls ? Config.RhythmAltButtons : Config.RhythmButtons;
		for (int i = 0; i < _setRhythmBtns.length; i++) {
			Button b = _setRhythmBtns[i];
			if (b == null) continue;
			boolean listening = (_rebindCategory == 1 && _rebindIndex == i);
			boolean f = (_settingsFocusIdx == 7 + i);
			b.setLabel((f ? "> " : "") + "C" + (i + 1) + ": " + (listening ? "[ ? ]" : Config.getKeyLabel(rhythmArr[i])));
			b.setColorBackground(
				listening ? app.color(175, 115, 25) : (f ? app.color(55, 110, 195) : app.color(28, 34, 52))
			);
		}

		int[] rpgArr = _editingAltControls ? Config.RPGAltButtons : Config.RPGButtons;
		String[] rpgLabels = { "ARRIBA", "ABAJO", "IZQ", "DER", "ACCION", "CORRER" };
		for (int i = 0; i < _setRpgBtns.length; i++) {
			Button b = _setRpgBtns[i];
			if (b == null) continue;
			boolean listening = (_rebindCategory == 2 && _rebindIndex == i);
			boolean f = (_settingsFocusIdx == 13 + i);
			b.setLabel((f ? "> " : "") + rpgLabels[i] + ": " + (listening ? "[ ? ]" : Config.getKeyLabel(rpgArr[i])));
			b.setColorBackground(
				listening ? app.color(175, 115, 25) : (f ? app.color(55, 110, 195) : app.color(28, 34, 52))
			);
		}
	}

	@Override
	public void keyEvent(KeyEvent e) {
		if (_viewMode == ViewMode.SETTINGS && _rebindCategory != 0 && e.getAction() == KeyEvent.PRESS) {
			int code = e.getKeyCode();
			if (code == 27) { // ESC cancela captura sin volver al menú en este frame
				_rebindCategory = 0;
				_rebindIndex = -1;
				_ignoreNavOnce = true;
				syncSettingsUI();
				return;
			}
			int normalized = KBInput.normalizeKey(code);
			if (normalized > 0 && normalized < 256) {
				int[] rhythmArr = _editingAltControls ? Config.RhythmAltButtons : Config.RhythmButtons;
				int[] rpgArr = _editingAltControls ? Config.RPGAltButtons : Config.RPGButtons;
				if (_rebindCategory == 1 && _rebindIndex >= 0 && _rebindIndex < rhythmArr.length) {
					rhythmArr[_rebindIndex] = normalized;
				} else if (_rebindCategory == 2 && _rebindIndex >= 0 && _rebindIndex < rpgArr.length) {
					rpgArr[_rebindIndex] = normalized;
				}
				Config.syncDialogKeys();
				Config.saveConfig(".config");
				_rebindCategory = 0;
				_rebindIndex = -1;
				_ignoreNavOnce = true;
				syncSettingsUI();
			}
		}
	}

	@Override
	public void update(float dt) {
		_animTime += dt;

		// Reconstruir los controladores ControlP5 si cambió la resolución de ventana
		var app = Engine.getApp();
		if (app.width != _builtAppWidth || app.height != _builtAppHeight) {
			if (_viewMode == ViewMode.SETTINGS) {
				_savedSettingsFocusIdx = _settingsFocusIdx;
			}
			Engine.changeScene(new InitScene(_viewMode));
			return;
		}

		if (_ignoreNavOnce) {
			_ignoreNavOnce = false;
			return;
		}

		boolean up = Config.wasUIKeyJustPressed(Config.UP);
		boolean down = Config.wasUIKeyJustPressed(Config.DOWN);
		boolean left = Config.wasUIKeyJustPressed(Config.LEFT);
		boolean right = Config.wasUIKeyJustPressed(Config.RIGHT);
		boolean accept = Config.wasUIKeyJustPressed(Config.INTERACT) || Config.wasRPGKeyJustPressed(Config.INTERACT);
		boolean back = Config.wasUIKeyJustPressed(Config.CANCEL);

		switch (_viewMode) {
			case MAIN -> {
				if (up) {
					_mainMenuIdx = (_mainMenuIdx - 1 + _mainBtns.length) % _mainBtns.length;
					syncMainUI();
				} else if (down) {
					_mainMenuIdx = (_mainMenuIdx + 1) % _mainBtns.length;
					syncMainUI();
				} else if (accept) {
					activateMainButton(_mainMenuIdx);
				}
			}
			case SAVE_SELECT -> {
				if (back) {
					if (_confirmDeleteSlot >= 0) {
						_confirmDeleteSlot = -1;
						syncSaveSelectUI();
					} else {
						switchView(ViewMode.MAIN);
					}
					return;
				}
				int rows = GameState.SAVE_SLOT_COUNT + 1; // 0..3 slots + 4 volver
				if (up) {
					_confirmDeleteSlot = -1;
					_saveRowIdx = (_saveRowIdx - 1 + rows) % rows;
					if (_saveRowIdx >= GameState.SAVE_SLOT_COUNT || !GameState.hasSaveFile(_saveRowIdx)) {
						_saveFocusCol = 0;
					}
					syncSaveSelectUI();
				} else if (down) {
					_confirmDeleteSlot = -1;
					_saveRowIdx = (_saveRowIdx + 1) % rows;
					if (_saveRowIdx >= GameState.SAVE_SLOT_COUNT || !GameState.hasSaveFile(_saveRowIdx)) {
						_saveFocusCol = 0;
					}
					syncSaveSelectUI();
				} else if (left && _saveRowIdx < GameState.SAVE_SLOT_COUNT && _saveFocusCol != 0) {
					_confirmDeleteSlot = -1;
					_saveFocusCol = 0;
					syncSaveSelectUI();
				} else if (right && _saveRowIdx < GameState.SAVE_SLOT_COUNT && GameState.hasSaveFile(_saveRowIdx) && _saveFocusCol != 1) {
					_confirmDeleteSlot = -1;
					_saveFocusCol = 1;
					syncSaveSelectUI();
				} else if (accept) {
					if (_saveRowIdx >= GameState.SAVE_SLOT_COUNT) {
						switchView(ViewMode.MAIN);
					} else if (_saveFocusCol == 1) {
						deleteSaveSlotAction(_saveRowIdx);
					} else {
						launchSaveSlot(_saveRowIdx);
					}
				}
			}
			case CAMPAIGN_SELECT -> {
				if (back) {
					switchView(ViewMode.SAVE_SELECT);
					return;
				}
				if (left && _campaignFocusIdx == 1) {
					_campaignFocusIdx = 0;
					syncCampaignSelectUI();
				} else if (right && _campaignFocusIdx == 0) {
					_campaignFocusIdx = 1;
					syncCampaignSelectUI();
				} else if (down && _campaignFocusIdx < 2) {
					_campaignFocusIdx = 2;
					syncCampaignSelectUI();
				} else if (up && _campaignFocusIdx == 2) {
					_campaignFocusIdx = 0;
					syncCampaignSelectUI();
				} else if (accept) {
					if (_campaignFocusIdx == 0) {
						startCampaignInPendingSlot(GameState.CampaignMode.CLASSIC);
					} else if (_campaignFocusIdx == 1) {
						startCampaignInPendingSlot(GameState.CampaignMode.EXPLORATION);
					} else {
						switchView(ViewMode.SAVE_SELECT);
					}
				}
			}
			case SONG_SELECT -> {
				if (_pendingCustomRefresh) {
					_pendingCustomRefresh = false;
					refreshCustomSongs(false);
					syncSongSelectUI();
				}
				if (KBInput.wasKeyJustPressed(9)) { // TAB cambia entre Canciones Custom y Jefes Historia
					setSongTab(_songTab == 0 ? 1 : 0);
					return;
				}
				if (back) {
					switchView(ViewMode.MAIN);
					return;
				}
				if (_songFocusCol == 0) {
					int listSize = (_songTab == 0) ? _customSongs.size() : _songs.size();
					if (up && listSize > 0) {
						if (_songTab == 0) {
							_selectedCustomIdx = (_selectedCustomIdx - 1 + listSize) % listSize;
						} else {
							_selectedSongIdx = (_selectedSongIdx - 1 + listSize) % listSize;
						}
						syncSongSelectUI();
					} else if (down && listSize > 0) {
						if (_songTab == 0) {
							_selectedCustomIdx = (_selectedCustomIdx + 1) % listSize;
						} else {
							_selectedSongIdx = (_selectedSongIdx + 1) % listSize;
						}
						syncSongSelectUI();
					} else if (right) {
						_songFocusCol = 1;
						syncSongSelectUI();
					} else if (accept) {
						launchSelectedSong();
					}
				} else {
					if (up) {
						_songRightIdx = (_songRightIdx - 1 + 6) % 6;
						syncSongSelectUI();
					} else if (down) {
						_songRightIdx = (_songRightIdx + 1) % 6;
						syncSongSelectUI();
					} else {
						switch (_songRightIdx) {
							case 0 -> {
								if (left) {
									_songChipIdx = (_songChipIdx - 1 + _chipIds.length) % _chipIds.length;
									syncSongSelectUI();
								} else if (right || accept) {
									_songChipIdx = (_songChipIdx + 1) % _chipIds.length;
									syncSongSelectUI();
								}
							}
							case 1 -> {
								if (left || right || accept) {
									_songInvincibleMode = !_songInvincibleMode;
									syncSongSelectUI();
								}
							}
							case 2 -> {
								if (left || right || accept) {
									GameState.toggleHealStrategy();
									syncSongSelectUI();
								}
							}
							case 3 -> {
								if (left) {
									_songSpeedIdx = (_songSpeedIdx - 1 + _speedOptions.length) % _speedOptions.length;
									syncSongSelectUI();
								} else if (right || accept) {
									_songSpeedIdx = (_songSpeedIdx + 1) % _speedOptions.length;
									syncSongSelectUI();
								}
							}
							case 4 -> {
								if (left) {
									_songFocusCol = 0;
									syncSongSelectUI();
								} else if (accept) {
									launchSelectedSong();
								}
							}
							case 5 -> {
								if (left) {
									_songFocusCol = 0;
									syncSongSelectUI();
								} else if (accept) {
									switchView(ViewMode.MAIN);
								}
							}
						}
					}
				}
			}
			case SETTINGS -> {
				if (_rebindCategory != 0) return;
				if (back) {
					_savedSettingsFocusIdx = 0;
					Config.saveConfig(".config");
					switchView(ViewMode.MAIN);
					return;
				}
				updateSettingsNavigation(up, down, left, right, accept);
			}
		}
	}

	private void updateSettingsNavigation(boolean up, boolean down, boolean left, boolean right, boolean accept) {
		if (_settingsFocusIdx <= 6) {
			if (up) {
				_settingsFocusIdx = (_settingsFocusIdx - 1 + 7) % 7;
				syncSettingsUI();
			} else if (down) {
				_settingsFocusIdx = (_settingsFocusIdx + 1) % 7;
				syncSettingsUI();
			} else {
				switch (_settingsFocusIdx) {
					case 0 -> {
						if (left) adjustBgmVolume(-0.1f);
						else if (right || accept) adjustBgmVolume(0.1f);
					}
					case 1 -> {
						if (left) adjustSfxVolume(-0.1f);
						else if (right || accept) adjustSfxVolume(0.1f);
					}
					case 2 -> {
						if (right) { _settingsFocusIdx = 7; syncSettingsUI(); }
						else if (accept) toggleMuteSetting();
					}
					case 3 -> {
						if (right) { _settingsFocusIdx = 8; syncSettingsUI(); }
						else if (accept) toggleWindowResolution();
					}
					case 4 -> {
						if (right) { _settingsFocusIdx = 9; syncSettingsUI(); }
						else if (accept) toggleAltControlsMode();
					}
					case 5 -> {
						if (right) { _settingsFocusIdx = 13; syncSettingsUI(); }
						else if (accept) resetAllControlsSetting();
					}
					case 6 -> {
						if (right) { _settingsFocusIdx = 15; syncSettingsUI(); }
						else if (accept) {
							_savedSettingsFocusIdx = 0;
							Config.saveConfig(".config");
							switchView(ViewMode.MAIN);
						}
					}
				}
			}
			return;
		}

		// Columna derecha:
		// Ritmo (7..12): col 0 = 7,8,9 (C1,C2,C3), col 1 = 10,11,12 (C4,C5,C6)
		// Controles (13..18): 3 filas x 2 cols (13,14 / 15,16 / 17,18)
		if (accept) {
			if (_settingsFocusIdx <= 12) {
				_rebindCategory = 1;
				_rebindIndex = _settingsFocusIdx - 7;
			} else {
				_rebindCategory = 2;
				_rebindIndex = _settingsFocusIdx - 13;
			}
			syncSettingsUI();
			return;
		}

		int[][] rightGrid = {
			{ 7, 10 },
			{ 8, 11 },
			{ 9, 12 },
			{ 13, 14 },
			{ 15, 16 },
			{ 17, 18 }
		};
		int row = 0, col = 0;
		for (int r = 0; r < rightGrid.length; r++) {
			for (int c = 0; c < 2; c++) {
				if (rightGrid[r][c] == _settingsFocusIdx) {
					row = r;
					col = c;
				}
			}
		}

		if (left) {
			if (col == 1) {
				_settingsFocusIdx = rightGrid[row][0];
			} else {
				_settingsFocusIdx = switch (row) {
					case 0 -> 2;
					case 1 -> 3;
					case 2 -> 4;
					case 3 -> 5;
					default -> 6;
				};
			}
			syncSettingsUI();
		} else if (right) {
			if (col == 0) {
				_settingsFocusIdx = rightGrid[row][1];
				syncSettingsUI();
			}
		} else if (up) {
			int nextRow = (row - 1 + rightGrid.length) % rightGrid.length;
			_settingsFocusIdx = rightGrid[nextRow][col];
			syncSettingsUI();
		} else if (down) {
			int nextRow = (row + 1) % rightGrid.length;
			_settingsFocusIdx = rightGrid[nextRow][col];
			syncSettingsUI();
		}
	}

	// =========================================================================
	// Fondo Animado del Menú (toda la UI en ControlP5, sin sprites laterales)
	// =========================================================================

	@Override
	public void draw(PGraphics g) {
		g.pushStyle();
		g.background(12, 8, 22);

		// Bandas de cielo degradado
		g.noStroke();
		for (int i = 0; i < 6; i++) {
			g.fill(18 + i * 4, 10 + i * 2, 32 + i * 4);
			g.rect(0, i * 22, BASE_W, 22);
		}

		// Estrellas parpadeantes
		for (int i = 0; i < _starX.length; i++) {
			float blink = 0.5f + 0.5f * (float) Math.sin(_animTime * 2.4f + _starPhase[i]);
			int alpha = (int) (75 + blink * 165);
			g.fill(235, 225, 255, alpha);
			int sx = Math.round(_starX[i]);
			int sy = Math.round(_starY[i]);
			g.rect(sx, sy, (i % 4 == 0) ? 2 : 1, (i % 4 == 0) ? 2 : 1);
		}

		// Resplandor central en el horizonte
		float yHorizon = 134.0f;
		for (int r = 4; r >= 1; r--) {
			g.fill(165, 36, 58, 16 + (5 - r) * 8);
			g.ellipse(BASE_W * 0.5f, yHorizon, r * 48, r * 14);
		}

		// Suelo oscuro con pista de perspectiva animada
		g.fill(9, 10, 18);
		g.rect(0, yHorizon, BASE_W, BASE_H - yHorizon);

		// Arcos horizontales desplazándose hacia la cámara en perspectiva
		float phase = (_animTime * 1.1f) % 1.0f;
		for (int k = 0; k < 7; k++) {
			float t = (k + phase) / 7.0f;
			float z = 3.2f - 2.2f * t;
			float s = 1.0f / z;
			float u = (s - (1.0f / 3.2f)) / (1.0f - (1.0f / 3.2f));
			float y = yHorizon + u * (BASE_H - yHorizon);
			g.stroke(60, 85, 130, (int) (45 + t * 110));
			g.line(0, Math.round(y), BASE_W, Math.round(y));
		}

		// 7 líneas convergentes de 6 carriles
		float topHalf = 46.0f;
		float botHalf = 190.0f;
		for (int edge = 0; edge <= 6; edge++) {
			float nx = -1.0f + edge * (2.0f / 6.0f);
			g.stroke(edge == 0 || edge == 6 ? g.color(140, 185, 240, 130) : g.color(85, 105, 155, 95));
			g.line(
				Math.round(BASE_W * 0.5f + nx * topHalf),
				Math.round(yHorizon),
				Math.round(BASE_W * 0.5f + nx * botHalf),
				BASE_H
			);
		}

		g.popStyle();
	}
}
