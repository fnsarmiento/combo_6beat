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
import game.Config;
import game.Global;
import game.core.GameState;
import game.core.db.CustomSongRepository;
import game.core.db.SaveRepository;
import game.core.notes.SongPackLoader;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics;
import processing.core.PImage;
import processing.event.KeyEvent;

/**
 * Escena de menú inicial de Combo 6Beat.
 * Gestiona las vistas de Menú Principal, Selección de Partida (4 ranuras SQLite),
 * Selector de Canciones por partida (Canciones unificadas Base + Custom y Tienda de Amuletos)
 * y Configuración (Audio y Controles 6K).
 */
public class InitScene extends Scene {

	/** Vistas disponibles dentro de la escena del menú inicial. */
	public enum ViewMode { MAIN, SAVE_SELECT, SONG_SELECT, SETTINGS }

	// =========================================================================
	// 1. Constantes de UI y navegación
	// =========================================================================

	// --- Menú Principal ---
	private static final String[] MAIN_LABELS = {
		"JUGAR",
		"CONFIGURACION",
		"SALIR"
	};
	private static final int MAIN_OPT_PLAY = 0;
	private static final int MAIN_OPT_SETTINGS = 1;
	private static final int MAIN_OPT_EXIT = 2;

	// --- Selección de Partida ---
	private static final int SAVE_COL_SLOT = 0;
	private static final int SAVE_COL_DELETE = 1;

	// --- Selector de Canciones y Tienda ---
	private static final int SONG_TAB_SONGS = 0;
	private static final int SONG_TAB_SHOP = 1;
	private static final int SONG_VISIBLE_ROWS = 6;
	private static final int AMULET_DROP_VISIBLE_ROWS = 4;
	private static final int SONG_COL_LIST = 0;
	private static final int SONG_COL_OPTIONS = 1;

	// Índices de foco del panel derecho en el Selector de Canciones (_songRightIdx)
	private static final int RIGHT_FOCUS_DIFF = 0; // Selector de dificultad
	private static final int RIGHT_FOCUS_M1 = 1;   // Modificador 1: Lista desplegable de Amuletos
	private static final int RIGHT_FOCUS_M2 = 2;   // Modificador 2: Modo Jefe (Invencible / Normal)
	private static final int RIGHT_FOCUS_M3 = 3;   // Modificador 3: Estrategia de Cura
	private static final int RIGHT_FOCUS_M4 = 4;   // Modificador 4: Multiplicador de Velocidad
	private static final int RIGHT_FOCUS_PLAY = 5; // Botón PLAY

	private static final float[] SPEED_OPTIONS = { 0.85f, 1.0f, 1.25f };

	// --- Configuración (Audio y Controles 6K) ---
	private static final int SET_FOCUS_BGM = 0;
	private static final int SET_FOCUS_SFX = 1;
	private static final int SET_FOCUS_MUTE = 2;
	private static final int SET_FOCUS_ALT_MODE = 3;
	private static final int SET_FOCUS_RESET = 4;
	private static final int SET_FOCUS_BACK = 5;
	private static final int SET_LEFT_COL_COUNT = 6; // Índices 0..5 en la columna izquierda
	private static final int SET_FOCUS_RHYTHM_START = 7;  // Carriles 6K: 7..12
	private static final int SET_FOCUS_RHYTHM_END = 12;

	private static final int REBIND_NONE = 0;
	private static final int REBIND_RHYTHM = 1;

	/** Grilla 3x2 de índices de foco en la columna derecha de Configuración (Carriles 6K). */
	private static final int[][] SETTINGS_RIGHT_GRID = {
		{ 7, 10 },
		{ 8, 11 },
		{ 9, 12 }
	};

	// =========================================================================
	// 2. Estado de la escena y referencias de controles ControlP5
	// =========================================================================

	// --- Estado general y animación del fondo (480x270) ---
	private ViewMode _viewMode;
	private int _builtAppWidth = 0;
	private int _builtAppHeight = 0;
	private float _animTime = 0.0f;
	private final float[] _starX = new float[28];
	private final float[] _starY = new float[28];
	private final float[] _starPhase = new float[28];

	// --- Vista: Menú Principal ---
	private int _mainMenuIdx = 0;
	private Group _mainGroup;
	private final Button[] _mainBtns = new Button[MAIN_LABELS.length];

	// --- Vista: Selección de Partida (4 ranuras) ---
	private int _saveRowIdx = 0;   // 0..3 ranuras, 4 = volver
	private int _saveFocusCol = SAVE_COL_SLOT;
	private int _confirmDeleteSlot = -1;
	private Group _saveSelectGroup;
	private final Button[] _saveSlotBtns = new Button[GameState.SAVE_SLOT_COUNT];
	private final Button[] _saveDeleteBtns = new Button[GameState.SAVE_SLOT_COUNT];
	private Button _saveBackBtn;
	private Textlabel _saveInfoLbl;

	// --- Vista: Selector de Canciones y Tienda de Amuletos ---
	private static int _savedSongTab = SONG_TAB_SONGS;
	private int _songTab = _savedSongTab;
	private int _selectedSongIdx = 0;
	private int _selectedDiffIdx = 0;
	private int _songScrollOffset = 0;
	private int _shopSelectedIdx = 0;
	private int _shopScrollOffset = 0;
	private int _songFocusCol = SONG_COL_LIST;
	private boolean _songOptionsOpen = false;
	private int _songRightIdx = RIGHT_FOCUS_DIFF;
	private boolean _songInvincibleMode = true;
	private int _songSpeedIdx = 1;

	// Estado de la lista desplegable de amuletos en la pestaña CANCIONES
	private boolean _amuletDropdownOpen = false;
	private int _amuletDropIdx = 0;
	private int _amuletDropScrollOffset = 0;
	private int _amuletLastFocusedItemIdx = 0;

	private final List<SongPackLoader.SongPack> _basePacks = new ArrayList<>();
	private final List<SongPackLoader.SongPack> _customPacks = new ArrayList<>();
	private final List<SongPackLoader.SongPack> _allPacks = new ArrayList<>();
	private static final Map<String, PImage> _coverCache = new HashMap<>();
	private volatile boolean _importDialogBusy = false;
	private volatile boolean _pendingCustomRefresh = false;
	private volatile String _songStatusMsg = "";

	private Group _songsGroup;
	private Textlabel _songsHeaderLbl;
	private Button _songTabCustomBtn;
	private Button _songTabStoryBtn;
	private final Button[] _songListBtns = new Button[SONG_VISIBLE_ROWS];
	private Button _songImportBtn;
	private Button _songRescanBtn;
	private Textlabel _songListFooterLbl;
	private Textarea _songInfoArea;
	private Button _songCoverBox;
	private Textarea _songBadgeArea;
	private Button _songDiffBtn;
	private Button _songChipBtn;
	private Button _songInvincibleBtn;
	private Button _songHealBtn;
	private Button _songSpeedBtn;
	private Button _songPlayBtn;

	// Controles de la tarjeta de presentación en la pestaña TIENDA (ControlP5)
	private Button _shopCardBg;
	private Textarea _shopNameArea;
	private Textarea _shopPriceArea;
	private Button _shopDividerBox;
	private Textarea _shopDescArea;

	// Controles de la lista desplegable de amuletos
	private Textlabel _amuletDropHeaderLbl;
	private final Button[] _amuletDropBtns = new Button[AMULET_DROP_VISIBLE_ROWS];
	private Textarea _amuletDropDescArea;
	private Button _amuletDropCloseBtn;

	// --- Vista: Configuración ---
	private int _settingsFocusIdx = 0;
	private static int _savedSettingsFocusIdx = 0;
	private int _rebindCategory = REBIND_NONE;
	private int _rebindIndex = -1;
	private boolean _editingAltControls = false;
	private boolean _ignoreNavOnce = false;

	private Group _settingsGroup;
	private Textlabel _setBgmLbl;
	private Button _setBgmBarFill;
	private Textlabel _setSfxLbl;
	private Button _setSfxBarFill;
	private Button _setMuteBtn;
	private Button _setAltModeBtn;
	private Button _setResetBtn;
	private Button _setBackBtn;
	private final Button[] _setRhythmBtns = new Button[6];

	// =========================================================================
	// 3. Constructores, ciclo de vida y dibujo por vista
	// =========================================================================

	/** Crea la escena inicial en la vista del Menú Principal. */
	public InitScene() {
		this(ViewMode.MAIN);
	}

	/** Crea la escena inicial abriendo directamente la vista indicada. */
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
		Audio.ensurePersistentBGM(ResourcesManager.getAbsPathFromProject(Global.MENU_HUB_BGM));

		PApplet app = Engine.getApp();
		_builtAppWidth = app.width;
		_builtAppHeight = app.height;

		initBackgroundStars();
		scanSongsDirectory(false);
		refreshCustomSongs(false);
		syncSpeedSelectionFromState();

		buildMainView();
		buildSaveSelectView();
		buildSongSelectView();
		buildSettingsView();
		switchView(_viewMode);
	}

	@Override
	public void update(float dt) {
		_animTime += dt;

		// Reconstruir los controladores ControlP5 si cambió la resolución de ventana
		PApplet app = Engine.getApp();
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

		handleKeyNavigation();
	}

	@Override
	public void draw(PGraphics g) {
		g.pushStyle();
		drawAnimatedBackground(g);

		switch (_viewMode) {
			case MAIN -> drawMainMenuOverlay(g);
			case SAVE_SELECT -> drawSaveSelectOverlay(g);
			case SONG_SELECT -> drawSongSelectOverlay(g);
			case SETTINGS -> drawSettingsOverlay(g);
		}
		g.popStyle();
	}

	/** Dibuja el cielo estrellado y la pista de 6 carriles en perspectiva del fondo. */
	private void drawAnimatedBackground(PGraphics g) {
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
			int size = (i % 4 == 0) ? 2 : 1;
			g.rect(sx, sy, size, size);
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

		// 7 líneas convergentes que delimitan los 6 carriles
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
	}

	private void drawMainMenuOverlay(PGraphics g) {
		// Los controles del menú principal se renderizan mediante ControlP5 (_mainGroup)
	}

	private void drawSaveSelectOverlay(PGraphics g) {
		// Los controles de selección de partida se renderizan mediante ControlP5 (_saveSelectGroup)
	}

	private void drawSongSelectOverlay(PGraphics g) {
		// Todos los paneles, listas y tarjetas de Canciones/Tienda se renderizan con ControlP5 (_songsGroup)
	}

	private void drawSettingsOverlay(PGraphics g) {
		// Los controles de configuración se renderizan mediante ControlP5 (_settingsGroup)
	}

	// =========================================================================
	// 4. Construcción y sincronización de cada vista
	// =========================================================================

	/** Cambia la vista activa del menú, reinicia subestados temporales y sincroniza ControlP5. */
	private void switchView(ViewMode mode) {
		_viewMode = mode;
		_rebindCategory = REBIND_NONE;
		_rebindIndex = -1;
		_confirmDeleteSlot = -1;

		if (mode == ViewMode.SAVE_SELECT) {
			_saveRowIdx = 0;
			_saveFocusCol = SAVE_COL_SLOT;
		} else if (mode == ViewMode.SONG_SELECT) {
			_songTab = _savedSongTab;
			_songOptionsOpen = false;
			_amuletDropdownOpen = false;
			_songFocusCol = SONG_COL_LIST;
			_songRightIdx = RIGHT_FOCUS_DIFF;
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
		syncSongSelectUI();
		syncSettingsUI();
	}

	// --- 4.1 Vista: Menú Principal ---

	/** Construye los botones y el encabezado del Menú Principal. */
	private void buildMainView() {
		PApplet app = Engine.getApp();
		ControlFont fontTitle = makeFont(22.0f, true);
		ControlFont fontBtn = makeFont(9.0f, false);

		_mainGroup = addUIGroup("ui_main_group", 0, 0, BASE_W, BASE_H);
		Button titleBanner = addUIBox("ui_menu_banner", _mainGroup, 85, 18, 310, 50, app.color(14, 10, 26, 200));
		titleBanner.setLabel("Combo 6 Beat");
		titleBanner.setColorLabel(app.color(255, 225, 130));
		if (fontTitle != null) titleBanner.setFont(fontTitle);
		titleBanner.getCaptionLabel().align(PConstants.CENTER, PConstants.CENTER);

		float btnW = 186.0f, btnH = 26.0f, btnX = (BASE_W - btnW) * 0.5f, startY = 104.0f, gap = 12.0f;
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

	/** Sincroniza colores y etiquetas de foco del Menú Principal. */
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

	// --- 4.2 Vista: Selección de Partida (4 ranuras) ---

	/** Construye el panel de selección y borrado de las 4 ranuras de partida. */
	private void buildSaveSelectView() {
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
					_saveFocusCol = SAVE_COL_SLOT;
					launchSaveSlot(slot);
				});

			_saveDeleteBtns[i] = addUIButton("ui_save_del_btn_" + i, _saveSelectGroup, "Borrar", 370, rowY, 72, 30,
				app.color(75, 30, 42), app.color(145, 48, 66), fontSmall, () -> {
					_saveRowIdx = slot;
					_saveFocusCol = SAVE_COL_DELETE;
					deleteSaveSlotAction(slot);
				});
		}

		_saveInfoLbl = addUILabel("ui_save_info_lbl", _saveSelectGroup, "", 38, 198, app.color(175, 200, 235), fontSmall);

		_saveBackBtn = addUIButton("ui_save_back_btn", _saveSelectGroup, "VOLVER AL MENU", 140, 214, 200, 24,
			app.color(85, 36, 48), app.color(130, 52, 70), fontMed, () -> switchView(ViewMode.MAIN));
	}

	/** Sincroniza el resumen de cada ranura de guardado y el estado de confirmación de borrado. */
	private void syncSaveSelectUI() {
		PApplet app = Engine.getApp();
		for (int i = 0; i < _saveSlotBtns.length; i++) {
			GameState.SaveSlotSummary sum = GameState.getSaveSlotSummary(i);
			boolean rowFocused = (_saveRowIdx == i);
			boolean mainFocused = rowFocused && (_saveFocusCol == SAVE_COL_SLOT);
			boolean delFocused = rowFocused && (_saveFocusCol == SAVE_COL_DELETE);

			Button slotBtn = _saveSlotBtns[i];
			if (slotBtn != null) {
				String text = sum.exists
					? ("PARTIDA " + (i + 1) + " — Puntos: " + GameState.getSaveSlotPoints(i) +
					   " | Amuletos: " + sum.amuletCount +
					   " | Canciones: " + sum.battlesWonCount)
					: ("PARTIDA " + (i + 1) + " - [Vacio - Nueva Partida]");
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
				_saveInfoLbl.setText("Ult. jefe: " + lastBoss + "  |  Historial: " + lastEvent);
				_saveInfoLbl.setColorValue(app.color(175, 220, 255));
			} else {
				_saveInfoLbl.setText("");
			}
		}
	}

	/** Carga o inicializa la ranura indicada y abre el Selector de Canciones y Tienda de la partida. */
	private void launchSaveSlot(int slot) {
		_confirmDeleteSlot = -1;
		GameState.launchedFromMenu = false;
		GameState.isDebugSession = false;
		GameState.songSpeedMultiplier = 1.0f;
		if (!GameState.hasSaveFile(slot) || !GameState.loadGame(slot)) {
			GameState.startNewGame(slot);
		}
		_savedSongTab = SONG_TAB_SONGS;
		_songTab = SONG_TAB_SONGS;
		switchView(ViewMode.SONG_SELECT);
	}

	/** Gestiona el borrado en dos pasos (Borrar -> Confirmar) de una ranura de partida. */
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
		_saveFocusCol = SAVE_COL_SLOT;
		syncSaveSelectUI();
		syncMainUI();
	}

	// --- 4.3 Vista: Selector de Canciones y Tienda de Amuletos ---

	/** Construye el Selector de Canciones unificado con la pestaña de Tienda y la lista desplegable de amuletos. */
	private void buildSongSelectView() {
		PApplet app = Engine.getApp();
		ControlFont fontTitle = makeFont(9.5f, true);
		ControlFont fontMed = makeFont(7.5f, false);
		ControlFont fontSmall = makeFont(6.5f, false);

		_songsGroup = addUIGroup("ui_songs_group", 0, 0, BASE_W, BASE_H);
		addUIBox("ui_songs_panel_bg", _songsGroup, 12, 10, BASE_W - 24, BASE_H - 20, app.color(14, 16, 28, 225));
		_songsHeaderLbl = addUILabel("ui_songs_header", _songsGroup, "PARTIDA 1  |  PUNTOS: 0", 22, 16, app.color(255, 225, 130), fontTitle);

		_songTabCustomBtn = addUIButton("ui_song_tab_custom", _songsGroup, "CANCIONES", 220, 14, 116, 18,
			app.color(45, 95, 165), app.color(65, 130, 215), fontSmall, () -> setSongTab(SONG_TAB_SONGS));
		_songTabStoryBtn = addUIButton("ui_song_tab_story", _songsGroup, "TIENDA", 342, 14, 116, 18,
			app.color(28, 34, 52), app.color(55, 95, 165), fontSmall, () -> setSongTab(SONG_TAB_SHOP));

		for (int i = 0; i < _songListBtns.length; i++) {
			final int visibleRow = i;
			_songListBtns[i] = addUIButton("ui_song_slot_" + i, _songsGroup, "", 22, 38 + i * 27, 188, 24,
				app.color(26, 30, 46), app.color(50, 95, 165), fontSmall, () -> selectSongFromVisibleRow(visibleRow));
		}

		_songImportBtn = addUIButton("ui_song_import_btn", _songsGroup, "+ IMPORTAR .OSU", 22, 204, 122, 20,
			app.color(35, 95, 130), app.color(55, 140, 185), fontSmall, this::openCustomOsuFilePicker);
		_songRescanBtn = addUIButton("ui_song_rescan_btn", _songsGroup, "ACTUALIZAR", 148, 204, 62, 20,
			app.color(32, 42, 66), app.color(55, 95, 155), fontSmall, () -> {
				scanSongsDirectory(true);
				refreshCustomSongs(true);
				_songStatusMsg = "Actualizado: " + _allPacks.size() + " canciones (" + _basePacks.size() + " base, " + _customPacks.size() + " custom).";
				syncSongSelectUI();
			});

		_songListFooterLbl = addUILabel("ui_song_footer_lbl", _songsGroup, "", 22, 234, app.color(175, 200, 235), fontSmall);

		_songInfoArea = addUITextarea("ui_song_info_area", _songsGroup, "Sin canciones", 220, 40, 164, 88,
			app.color(235, 240, 255), app.color(20, 24, 38), fontSmall);

		_songCoverBox = addUIBox("ui_song_cover_box", _songsGroup, 390, 40, 68, 56, app.color(20, 24, 38));
		_songBadgeArea = addUITextarea("ui_song_badge_area", _songsGroup, "", 390, 98, 68, 30,
			app.color(140, 225, 255), app.color(20, 24, 38), fontSmall);

		_songDiffBtn = addUIButton("ui_song_diff_btn", _songsGroup, "Dificultad: Normal", 220, 134, 238, 20,
			app.color(42, 62, 108), app.color(65, 110, 195), fontSmall, () -> {
				_songFocusCol = SONG_COL_OPTIONS;
				_songRightIdx = RIGHT_FOCUS_DIFF;
				cycleSelectedDifficulty(1);
			});

		_songChipBtn = addUIButton("ui_song_chip_btn", _songsGroup, "Amuletos: 0/1 [Desplegar]", 220, 160, 116, 20,
			app.color(32, 42, 66), app.color(55, 95, 155), fontSmall, () -> {
				_songFocusCol = SONG_COL_OPTIONS;
				_songRightIdx = RIGHT_FOCUS_M1;
				openAmuletDropdown();
			});

		_songInvincibleBtn = addUIButton("ui_song_invincible_btn", _songsGroup, "Jefe: Invencible", 342, 160, 116, 20,
			app.color(32, 42, 66), app.color(55, 95, 155), fontSmall, () -> {
				_songFocusCol = SONG_COL_OPTIONS;
				_songRightIdx = RIGHT_FOCUS_M2;
				_songInvincibleMode = !_songInvincibleMode;
				syncSongSelectUI();
			});

		_songHealBtn = addUIButton("ui_song_heal_btn", _songsGroup, "Cura: Optimo", 220, 186, 116, 20,
			app.color(32, 42, 66), app.color(55, 95, 155), fontSmall, () -> {
				_songFocusCol = SONG_COL_OPTIONS;
				_songRightIdx = RIGHT_FOCUS_M3;
				GameState.toggleHealStrategy();
				syncSongSelectUI();
			});

		_songSpeedBtn = addUIButton("ui_song_speed_btn", _songsGroup, "Velocidad: 1.00x", 342, 186, 116, 20,
			app.color(32, 42, 66), app.color(55, 95, 155), fontSmall, () -> {
				_songFocusCol = SONG_COL_OPTIONS;
				_songRightIdx = RIGHT_FOCUS_M4;
				_songSpeedIdx = (_songSpeedIdx + 1) % SPEED_OPTIONS.length;
				syncSongSelectUI();
			});

		_songPlayBtn = addUIButton("ui_song_play_btn", _songsGroup, "PLAY", 220, 222, 238, 28,
			app.color(35, 125, 85), app.color(55, 170, 115), fontMed, () -> {
				_songFocusCol = SONG_COL_OPTIONS;
				_songRightIdx = RIGHT_FOCUS_PLAY;
				if (_songTab == SONG_TAB_SHOP) {
					executeShopPrimaryAction();
				} else {
					launchSelectedSong();
				}
			});

		// Tarjeta de presentación completa en ControlP5 para la pestaña TIENDA (cubre todo de y=40 a y=216)
		ControlFont fontShopName = makeFont(13.5f, true);
		ControlFont fontShopPrice = makeFont(9.5f, false);
		ControlFont fontShopDesc = makeFont(8.5f, false);
		_shopCardBg = addUIBox("ui_shop_card_bg", _songsGroup, 220, 40, 238, 176, app.color(20, 24, 40, 238));
		_shopNameArea = addUITextarea("ui_shop_name_area", _songsGroup, "", 228, 48, 222, 42,
			app.color(255, 224, 65), app.color(0, 0), fontShopName);
		_shopPriceArea = addUITextarea("ui_shop_price_area", _songsGroup, "", 228, 94, 222, 22,
			app.color(255, 224, 65), app.color(0, 0), fontShopPrice);
		_shopDividerBox = addUIBox("ui_shop_divider", _songsGroup, 230, 118, 218, 1, app.color(72, 96, 150, 210));
		_shopDescArea = addUITextarea("ui_shop_desc_area", _songsGroup, "", 228, 128, 222, 82,
			app.color(245, 248, 255), app.color(0, 0), fontShopDesc);

		// Controles de la lista desplegable de amuletos (ocultos por defecto)
		_amuletDropHeaderLbl = addUILabel("ui_amulet_drop_hdr", _songsGroup, "AMULETOS EQUIPADOS", 220, 40,
			app.color(255, 225, 130), fontSmall);
		for (int i = 0; i < _amuletDropBtns.length; i++) {
			final int row = i;
			_amuletDropBtns[i] = addUIButton("ui_amulet_drop_btn_" + i, _songsGroup, "", 220, 54 + i * 23, 238, 20,
				app.color(28, 36, 56), app.color(55, 105, 180), fontSmall, () -> clickAmuletDropdownRow(row));
		}
		_amuletDropDescArea = addUITextarea("ui_amulet_drop_desc", _songsGroup, "", 220, 148, 238, 70,
			app.color(225, 238, 255), app.color(20, 24, 38), fontSmall);
		_amuletDropCloseBtn = addUIButton("ui_amulet_drop_close", _songsGroup, "CERRAR LISTA", 220, 224, 238, 24,
			app.color(85, 36, 48), app.color(140, 55, 74), fontSmall, () -> {
				_amuletDropdownOpen = false;
				syncSongSelectUI();
			});
	}

	/** Abre la lista desplegable de amuletos en el panel derecho de la canción. */
	private void openAmuletDropdown() {
		_amuletDropdownOpen = true;
		_amuletDropIdx = 0;
		_amuletDropScrollOffset = 0;
		_amuletLastFocusedItemIdx = 0;
		syncSongSelectUI();
	}

	/** Gestiona el clic sobre una fila visible de la lista desplegable de amuletos. */
	private void clickAmuletDropdownRow(int visibleRow) {
		List<GameState.ItemDef> owned = GameState.getOwnedPowerUps();
		if (owned.isEmpty()) {
			_amuletDropdownOpen = false;
			setSongTab(SONG_TAB_SHOP);
			return;
		}
		int idx = _amuletDropScrollOffset + visibleRow;
		if (idx >= 0 && idx < owned.size()) {
			_amuletDropIdx = idx;
			_amuletLastFocusedItemIdx = idx;
			GameState.ItemDef def = owned.get(idx);
			toggleAmuletWithFeedback(def);
			syncSongSelectUI();
		}
	}

	/** Alterna el equipamiento de un amuleto mostrando aviso si se alcanzó el cupo máximo. */
	private void toggleAmuletWithFeedback(GameState.ItemDef def) {
		if (def == null) return;
		boolean wasEquipped = GameState.isPowerUpEquipped(def.id);
		GameState.toggleEquippedPowerUp(def.id);
		boolean nowEquipped = GameState.isPowerUpEquipped(def.id);
		if (!wasEquipped && !nowEquipped) {
			_songStatusMsg = "Cupo lleno (" + GameState.getMaxEquippedAmulets() + "). Desequipa otro amuleto.";
		} else if (nowEquipped) {
			_songStatusMsg = "Equipado: " + def.name;
		} else {
			_songStatusMsg = "Desequipado: " + def.name;
		}
		GameState.saveGame();
	}

	/** Selecciona una canción o amuleto de tienda al hacer clic en una fila visible de la columna izquierda. */
	private void selectSongFromVisibleRow(int visibleRow) {
		if (_songTab == SONG_TAB_SHOP) {
			List<GameState.ItemDef> shopList = GameState.getShopAmulets();
			int actualIdx = _shopScrollOffset + visibleRow;
			if (actualIdx >= 0 && actualIdx < shopList.size()) {
				_shopSelectedIdx = actualIdx;
				_songFocusCol = SONG_COL_OPTIONS;
				_songRightIdx = RIGHT_FOCUS_PLAY;
				syncSongSelectUI();
			}
		} else {
			int actualIdx = _songScrollOffset + visibleRow;
			if (actualIdx >= 0 && actualIdx < _allPacks.size()) {
				if (_selectedSongIdx != actualIdx) _selectedDiffIdx = 0;
				_selectedSongIdx = actualIdx;
				_amuletDropdownOpen = false;
				_songOptionsOpen = true;
				_songFocusCol = SONG_COL_OPTIONS;
				_songRightIdx = RIGHT_FOCUS_DIFF;
				syncSongSelectUI();
			}
		}
	}

	/** Cambia entre la pestaña de Canciones unificadas (0) y la Tienda de Amuletos (1). */
	private void setSongTab(int tab) {
		_songTab = (tab == SONG_TAB_SHOP) ? SONG_TAB_SHOP : SONG_TAB_SONGS;
		_savedSongTab = _songTab;
		_songOptionsOpen = false;
		_amuletDropdownOpen = false;
		_songFocusCol = SONG_COL_LIST;
		_songStatusMsg = "";
		syncSongSelectUI();
	}

	/** Sincroniza todos los elementos visuales del Selector de Canciones y la Tienda de Amuletos. */
	private void syncSongSelectUI() {
		PApplet app = Engine.getApp();
		boolean isShopTab = (_songTab == SONG_TAB_SHOP);
		ensureSelectionVisible();

		if (_songsHeaderLbl != null) {
			_songsHeaderLbl.setText(
				"PARTIDA " + (GameState.activeSaveSlot + 1) + "  |  PUNTOS: " + GameState.getShopPoints()
			);
		}

		if (_songTabCustomBtn != null) {
			_songTabCustomBtn.setLabel((!isShopTab ? "* " : "") + "CANCIONES");
			_songTabCustomBtn.setColorBackground(!isShopTab ? app.color(45, 95, 165) : app.color(26, 30, 46));
		}
		if (_songTabStoryBtn != null) {
			_songTabStoryBtn.setLabel((isShopTab ? "* " : "") + "TIENDA");
			_songTabStoryBtn.setColorBackground(isShopTab ? app.color(45, 95, 165) : app.color(26, 30, 46));
		}

		if (isShopTab) {
			syncShopTabUI(app);
		} else {
			syncSongsTabUI(app);
		}
	}

	/** Sincroniza la pestaña CANCIONES (lista unificada Base + Custom, metadatos, opciones y desplegable de amuletos). */
	private void syncSongsTabUI(PApplet app) {
		hideShopCardControls();
		if (_songImportBtn != null) _songImportBtn.show();
		if (_songRescanBtn != null) _songRescanBtn.show();

		for (int i = 0; i < _songListBtns.length; i++) {
			Button btn = _songListBtns[i];
			if (btn == null) continue;
			int idx = _songScrollOffset + i;
			if (idx < _allPacks.size()) {
				SongPackLoader.SongPack pack = _allPacks.get(idx);
				boolean sel = (idx == _selectedSongIdx);
				boolean focused = sel && (_songFocusCol == SONG_COL_LIST);
				String diffSuffix = (pack.difficulties.size() > 1) ? (" (" + pack.difficulties.size() + " dif.)") : "";
				String label = pack.title + diffSuffix;
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

		if (_songListFooterLbl != null) {
			if (!_songStatusMsg.isEmpty()) {
				_songListFooterLbl.setText(_songStatusMsg);
			} else if (_allPacks.isEmpty()) {
				_songListFooterLbl.setText("0 canciones | TAB: Tienda");
			} else {
				_songListFooterLbl.setText(
					"Cancion " + (_selectedSongIdx + 1) + "/" + _allPacks.size() + " | TAB: Tienda"
				);
			}
		}

		SongPackLoader.SongPack activePack = getActivePack();
		SongPackLoader.ChartVariant activeVar = getActiveChartVariant();

		if (_amuletDropdownOpen) {
			if (_songInfoArea != null) _songInfoArea.hide();
			if (_songCoverBox != null) _songCoverBox.hide();
			if (_songBadgeArea != null) _songBadgeArea.hide();
			if (_songDiffBtn != null) _songDiffBtn.hide();
			if (_songChipBtn != null) _songChipBtn.hide();
			if (_songInvincibleBtn != null) _songInvincibleBtn.hide();
			if (_songHealBtn != null) _songHealBtn.hide();
			if (_songSpeedBtn != null) _songSpeedBtn.hide();
			if (_songPlayBtn != null) _songPlayBtn.hide();
			syncAmuletDropdownUI(app);
		} else {
			hideAmuletDropdownControls();
			syncSongInfoAndCover(activeVar);
			syncSongOptionsPanel(app, activePack, activeVar);
		}
	}

	/** Sincroniza la lista desplegable de amuletos con nombre y descripción debajo. */
	private void syncAmuletDropdownUI(PApplet app) {
		List<GameState.ItemDef> owned = GameState.getOwnedPowerUps();
		List<String> eq = GameState.getEquippedPowerUps();
		int maxEq = GameState.getMaxEquippedAmulets();

		if (_amuletDropHeaderLbl != null) {
			_amuletDropHeaderLbl.setText("AMULETOS  |  Equipados: " + eq.size() + "/" + maxEq);
			_amuletDropHeaderLbl.show();
		}

		if (owned.isEmpty()) {
			_amuletDropIdx = 0;
			_amuletDropScrollOffset = 0;
			if (_amuletDropBtns[0] != null) {
				_amuletDropBtns[0].setLabel("  Sin amuletos — Compra en TIENDA");
				_amuletDropBtns[0].setColorBackground(app.color(36, 44, 68));
				_amuletDropBtns[0].show();
			}
			for (int i = 1; i < _amuletDropBtns.length; i++) {
				if (_amuletDropBtns[i] != null) _amuletDropBtns[i].hide();
			}
			if (_amuletDropDescArea != null) {
				_amuletDropDescArea.setText(
					"Sin amuletos en inventario.\n" +
					"Pulsa TAB o abre la pestana TIENDA para comprar amuletos con tus puntos."
				);
				_amuletDropDescArea.show();
			}
			if (_amuletDropCloseBtn != null) {
				_amuletDropCloseBtn.setLabel("> CERRAR LISTA");
				_amuletDropCloseBtn.setColorBackground(app.color(145, 58, 78));
				_amuletDropCloseBtn.show();
			}
			return;
		}

		int totalFocusable = owned.size() + 1; // 0..owned.size()-1 amuletos, owned.size() = CERRAR LISTA
		if (_amuletDropIdx < 0 || _amuletDropIdx >= totalFocusable) {
			_amuletDropIdx = 0;
		}
		if (_amuletDropIdx < owned.size()) {
			_amuletLastFocusedItemIdx = _amuletDropIdx;
			if (_amuletDropIdx < _amuletDropScrollOffset) {
				_amuletDropScrollOffset = _amuletDropIdx;
			} else if (_amuletDropIdx >= _amuletDropScrollOffset + AMULET_DROP_VISIBLE_ROWS) {
				_amuletDropScrollOffset = _amuletDropIdx - AMULET_DROP_VISIBLE_ROWS + 1;
			}
		}
		_amuletDropScrollOffset = Math.max(0, Math.min(_amuletDropScrollOffset, Math.max(0, owned.size() - AMULET_DROP_VISIBLE_ROWS)));

		for (int i = 0; i < _amuletDropBtns.length; i++) {
			Button btn = _amuletDropBtns[i];
			if (btn == null) continue;
			int idx = _amuletDropScrollOffset + i;
			if (idx < owned.size()) {
				GameState.ItemDef def = owned.get(idx);
				boolean isEq = GameState.isPowerUpEquipped(def.id);
				boolean focused = (_amuletDropIdx == idx);
				String label = (focused ? "> " : "  ") + (isEq ? "[E] " : "[ ] ") + def.name;
				btn.setLabel(label);
				btn.setColorBackground(
					focused ? app.color(55, 110, 195) : (isEq ? app.color(34, 72, 105) : app.color(26, 32, 48))
				);
				btn.show();
			} else {
				btn.hide();
			}
		}

		int descIdx = (_amuletDropIdx < owned.size()) ? _amuletDropIdx : Math.min(_amuletLastFocusedItemIdx, owned.size() - 1);
		GameState.ItemDef focusedDef = owned.get(Math.max(0, descIdx));
		if (_amuletDropDescArea != null) {
			_amuletDropDescArea.setText(focusedDef.name + "\n" + focusedDef.description);
			_amuletDropDescArea.show();
		}

		if (_amuletDropCloseBtn != null) {
			boolean closeFocused = (_amuletDropIdx == owned.size());
			_amuletDropCloseBtn.setLabel((closeFocused ? "> " : "") + "CERRAR LISTA");
			_amuletDropCloseBtn.setColorBackground(closeFocused ? app.color(145, 58, 78) : app.color(85, 36, 48));
			_amuletDropCloseBtn.show();
		}
	}

	private void hideShopCardControls() {
		if (_shopCardBg != null) _shopCardBg.hide();
		if (_shopNameArea != null) _shopNameArea.hide();
		if (_shopPriceArea != null) _shopPriceArea.hide();
		if (_shopDividerBox != null) _shopDividerBox.hide();
		if (_shopDescArea != null) _shopDescArea.hide();
	}

	private void hideAmuletDropdownControls() {
		if (_amuletDropHeaderLbl != null) _amuletDropHeaderLbl.hide();
		for (Button btn : _amuletDropBtns) {
			if (btn != null) btn.hide();
		}
		if (_amuletDropDescArea != null) _amuletDropDescArea.hide();
		if (_amuletDropCloseBtn != null) _amuletDropCloseBtn.hide();
	}

	/** Sincroniza la pestaña TIENDA (catálogo de amuletos, detalle del amuleto y botón de compra/equipar). */
	private void syncShopTabUI(PApplet app) {
		hideAmuletDropdownControls();
		if (_songImportBtn != null) _songImportBtn.hide();
		if (_songRescanBtn != null) _songRescanBtn.hide();
		if (_songCoverBox != null) _songCoverBox.hide();
		if (_songBadgeArea != null) _songBadgeArea.hide();
		if (_songDiffBtn != null) _songDiffBtn.hide();
		if (_songChipBtn != null) _songChipBtn.hide();
		if (_songInvincibleBtn != null) _songInvincibleBtn.hide();
		if (_songHealBtn != null) _songHealBtn.hide();
		if (_songSpeedBtn != null) _songSpeedBtn.hide();

		List<GameState.ItemDef> shopList = GameState.getShopAmulets();
		if (_shopSelectedIdx >= shopList.size()) {
			_shopSelectedIdx = Math.max(0, shopList.size() - 1);
		}

		for (int i = 0; i < _songListBtns.length; i++) {
			Button btn = _songListBtns[i];
			if (btn == null) continue;
			int idx = _shopScrollOffset + i;
			if (idx < shopList.size()) {
				GameState.ItemDef def = shopList.get(idx);
				boolean owned = GameState.hasItem(def.id);
				boolean equipped = GameState.isPowerUpEquipped(def.id);
				boolean sel = (idx == _shopSelectedIdx);
				boolean focused = sel && (_songFocusCol == SONG_COL_LIST);

				String itemLabel;
				if (owned && equipped) {
					itemLabel = "[EQUIP] " + def.name;
				} else if (owned) {
					itemLabel = "[COMPRADO] " + def.name;
				} else {
					itemLabel = def.name + " (" + def.shopPrice + " pts)";
				}
				if (itemLabel.length() > 27) itemLabel = itemLabel.substring(0, 24) + "...";

				btn.setLabel((focused ? "> " : (sel ? "* " : "  ")) + itemLabel);
				btn.setColorBackground(
					focused ? app.color(55, 110, 195) : (sel ? app.color(36, 62, 108) : app.color(26, 30, 46))
				);
				btn.show();
			} else {
				btn.hide();
			}
		}

		if (_songListFooterLbl != null) {
			if (!_songStatusMsg.isEmpty()) {
				_songListFooterLbl.setText(_songStatusMsg);
			} else if (shopList.isEmpty()) {
				_songListFooterLbl.setText("Tienda vacia | TAB: Canciones");
			} else {
				_songListFooterLbl.setText(
					"Amuleto " + (_shopSelectedIdx + 1) + "/" + shopList.size() + " | TAB: Canciones"
				);
			}
		}

		GameState.ItemDef selectedDef = (!shopList.isEmpty() && _shopSelectedIdx >= 0 && _shopSelectedIdx < shopList.size())
			? shopList.get(_shopSelectedIdx)
			: null;

		if (_songInfoArea != null) {
			_songInfoArea.hide();
		}

		if (selectedDef == null) {
			if (_shopCardBg != null) _shopCardBg.show();
			if (_shopNameArea != null) {
				_shopNameArea.setColorValue(app.color(200, 210, 230)).setText("Sin amuletos").show();
			}
			if (_shopPriceArea != null) _shopPriceArea.hide();
			if (_shopDividerBox != null) _shopDividerBox.hide();
			if (_shopDescArea != null) {
				_shopDescArea.setColorValue(app.color(200, 210, 230)).setText("No hay amuletos disponibles.").show();
			}
		} else {
			boolean owned = GameState.hasItem(selectedDef.id);
			boolean canAfford = GameState.getShopPoints() >= selectedDef.shopPrice;
			int nameCol = owned
				? app.color(80, 235, 135)
				: (canAfford ? app.color(255, 224, 65) : app.color(255, 85, 95));

			if (_shopCardBg != null) _shopCardBg.show();
			if (_shopNameArea != null) {
				_shopNameArea.setColorValue(nameCol).setText(selectedDef.name).show();
			}
			if (_shopPriceArea != null) {
				_shopPriceArea.setColorValue(app.color(255, 224, 65)).setText(selectedDef.shopPrice + " pts.").show();
			}
			if (_shopDividerBox != null) _shopDividerBox.show();
			if (_shopDescArea != null) {
				_shopDescArea.setColorValue(app.color(245, 248, 255)).setText(selectedDef.description).show();
			}
		}

		if (_songPlayBtn != null) {
			if (selectedDef == null) {
				_songPlayBtn.hide();
			} else {
				boolean owned = GameState.hasItem(selectedDef.id);
				boolean equipped = GameState.isPowerUpEquipped(selectedDef.id);
				boolean f = (_songFocusCol == SONG_COL_OPTIONS);
				String actionLabel;
				if (!owned) {
					actionLabel = "COMPRAR (" + selectedDef.shopPrice + " PTS)";
					_songPlayBtn.setColorBackground(f ? app.color(205, 145, 38) : app.color(148, 102, 26));
				} else {
					actionLabel = equipped ? "DESEQUIPAR AMULETO" : "EQUIPAR AMULETO";
					_songPlayBtn.setColorBackground(f ? app.color(55, 185, 120) : app.color(35, 130, 85));
				}
				_songPlayBtn.setLabel((f ? "> " : "") + actionLabel);
				_songPlayBtn.show();
			}
		}
	}

	/** Ejecuta la acción de comprar o equipar/desequipar el amuleto seleccionado en la pestaña TIENDA. */
	private void executeShopPrimaryAction() {
		List<GameState.ItemDef> shopList = GameState.getShopAmulets();
		if (shopList.isEmpty() || _shopSelectedIdx < 0 || _shopSelectedIdx >= shopList.size()) return;

		GameState.ItemDef def = shopList.get(_shopSelectedIdx);
		if (!GameState.hasItem(def.id)) {
			if (GameState.buyShopAmulet(def.id)) {
				_songStatusMsg = "Comprado: " + def.name;
				if (GameState.getEquippedPowerUps().isEmpty()) {
					GameState.toggleEquippedPowerUp(def.id);
					GameState.saveGame();
				}
			} else {
				_songStatusMsg = "Puntos insuficientes (" + GameState.getShopPoints() + "/" + def.shopPrice + ")";
			}
		} else {
			toggleAmuletWithFeedback(def);
		}
		syncSongSelectUI();
	}

	/** Actualiza el cuadro de metadatos, dificultad calculada, récord y miniatura de portada. */
	private void syncSongInfoAndCover(SongPackLoader.ChartVariant activeVar) {
		float speedMult = SPEED_OPTIONS[_songSpeedIdx];
		if (_songInfoArea != null) {
			_songInfoArea.setSize(Math.max(1, (int) scaleW(164)), Math.max(1, (int) scaleH(88)));
			if (activeVar == null) {
				_songInfoArea.setText(
					"No hay canciones registradas.\nUsa '+ IMPORTAR .OSU' o agrega carpetas en resources/songs/"
				);
			} else {
				float effStars = SongPackLoader.computeDifficultyRating(activeVar.noteCount, activeVar.bpm, activeVar.durationSec, speedMult);
				float effNps = SongPackLoader.computeNotesPerSecond(activeVar.noteCount, activeVar.durationSec, speedMult);
				String tier = SongPackLoader.getDifficultyTierLabel(effStars);
				String diffLine = String.format("Nivel: %.1f* (%s) | %.1f n/s", effStars, tier, effNps);

				if (activeVar.customDbSummary != null) {
					CustomSongRepository.CustomSongSummary c = activeVar.customDbSummary;
					_songInfoArea.setText(
						"Cancion: " + c.titulo + "\n" +
						"Artista: " + (c.artista.isEmpty() ? "-" : c.artista) + "\n" +
						diffLine + "\n" +
						"Notas: " + c.cantidadNotas + " | Jugadas: " + c.vecesJugada + "\n" +
						"Record: " + c.puntajeMasAlto + " pts (" + c.comboMasAlto + "x, " + String.format("%.1f%%", c.precisionMasAlta) + ")"
					);
				} else {
					String sp = activeVar.bossSpecies.isEmpty() ? "" : " (" + activeVar.bossSpecies + ")";
					_songInfoArea.setText(
						"Cancion: " + activeVar.title + "\n" +
						"Artista: " + (activeVar.artist.isEmpty() ? "-" : activeVar.artist) + "\n" +
						diffLine + "\n" +
						"Notas: " + activeVar.noteCount + " | Vel: " + String.format("%.2f", activeVar.scrollSpeed) + "\n" +
						"Jefe: " + activeVar.bossName + sp
					);
				}
			}
			_songInfoArea.show();
		}

		refreshSelectedCover(activeVar, speedMult);
	}

	/** Sincroniza los botones de dificultad, amuletos, modificadores y PLAY del panel derecho. */
	private void syncSongOptionsPanel(
		PApplet app,
		SongPackLoader.SongPack activePack,
		SongPackLoader.ChartVariant activeVar
	) {
		boolean showOpts = _songOptionsOpen && activeVar != null && activePack != null;
		boolean rightActive = showOpts && (_songFocusCol == SONG_COL_OPTIONS);
		float speedMult = SPEED_OPTIONS[_songSpeedIdx];

		if (_songDiffBtn != null) {
			if (showOpts) {
				boolean f = rightActive && (_songRightIdx == RIGHT_FOCUS_DIFF);
				float effStars = SongPackLoader.computeDifficultyRating(activeVar.noteCount, activeVar.bpm, activeVar.durationSec, speedMult);
				String starTag = String.format(" [%.1f*]", effStars);
				String diffLabel;
				if (activePack.difficulties.size() > 1) {
					diffLabel = "Dificultad: < " + activeVar.difficulty + " >" + starTag + " (" + (_selectedDiffIdx + 1) + "/" + activePack.difficulties.size() + ")";
				} else {
					diffLabel = "Dificultad: " + activeVar.difficulty + starTag;
				}
				_songDiffBtn.setLabel((f ? "> " : "") + diffLabel);
				_songDiffBtn.setColorBackground(f ? app.color(65, 118, 210) : app.color(42, 62, 108));
				_songDiffBtn.show();
			} else {
				_songDiffBtn.hide();
			}
		}
		if (_songChipBtn != null) {
			if (showOpts) {
				List<String> eq = GameState.getEquippedPowerUps();
				int maxEq = GameState.getMaxEquippedAmulets();
				boolean f = rightActive && (_songRightIdx == RIGHT_FOCUS_M1);
				_songChipBtn.setLabel((f ? "> " : "") + "Amuletos: " + eq.size() + "/" + maxEq + " [Desplegar]");
				_songChipBtn.setColorBackground(f ? app.color(55, 110, 195) : app.color(32, 42, 66));
				_songChipBtn.show();
			} else {
				_songChipBtn.hide();
			}
		}
		if (_songInvincibleBtn != null) {
			if (showOpts) {
				boolean f = rightActive && (_songRightIdx == RIGHT_FOCUS_M2);
				_songInvincibleBtn.setLabel((f ? "> " : "") + "Jefe: " + (_songInvincibleMode ? "Invencible" : "Normal"));
				_songInvincibleBtn.setColorBackground(f ? app.color(55, 110, 195) : app.color(32, 42, 66));
				_songInvincibleBtn.show();
			} else {
				_songInvincibleBtn.hide();
			}
		}
		if (_songHealBtn != null) {
			if (showOpts) {
				boolean f = rightActive && (_songRightIdx == RIGHT_FOCUS_M3);
				_songHealBtn.setLabel((f ? "> " : "") + "Cura: " + GameState.healStrategy.label);
				_songHealBtn.setColorBackground(f ? app.color(55, 110, 195) : app.color(32, 42, 66));
				_songHealBtn.show();
			} else {
				_songHealBtn.hide();
			}
		}
		if (_songSpeedBtn != null) {
			if (showOpts) {
				boolean f = rightActive && (_songRightIdx == RIGHT_FOCUS_M4);
				_songSpeedBtn.setLabel((f ? "> " : "") + "Velocidad: " + String.format("%.2fx", speedMult));
				_songSpeedBtn.setColorBackground(f ? app.color(55, 110, 195) : app.color(32, 42, 66));
				_songSpeedBtn.show();
			} else {
				_songSpeedBtn.hide();
			}
		}
		if (_songPlayBtn != null) {
			if (showOpts) {
				boolean f = rightActive && (_songRightIdx == RIGHT_FOCUS_PLAY);
				_songPlayBtn.setLabel((f ? "> " : "") + "PLAY");
				_songPlayBtn.setColorBackground(f ? app.color(55, 185, 120) : app.color(35, 130, 85));
				_songPlayBtn.show();
			} else {
				_songPlayBtn.hide();
			}
		}
	}

	/** Aplica los modificadores elegidos, guarda la partida e inicia la batalla rítmica de la canción seleccionada. */
	private void launchSelectedSong() {
		SongPackLoader.ChartVariant variant = getActiveChartVariant();
		if (variant == null) return;

		GameState.songSpeedMultiplier = SPEED_OPTIONS[_songSpeedIdx];
		GameState.bossInvincibleMode = _songInvincibleMode;
		GameState.launchedFromMenu = true;
		_savedSongTab = _songTab;
		GameState.saveGame();

		if (variant.customDbSummary != null) {
			CustomSongRepository.CustomSongSummary fresh = CustomSongRepository.getById(variant.customDbSummary.idCancion);
			Engine.changeScene(new ROTNScene(fresh != null ? fresh : variant.customDbSummary));
		} else {
			Engine.changeScene(new ROTNScene(variant));
		}
	}

	// --- 4.4 Vista: Configuración (Audio y Controles 6K) ---

	/** Construye el panel de Configuración con controles de volumen BGM/SFX y rebinding de los 6 carriles. */
	private void buildSettingsView() {
		PApplet app = Engine.getApp();
		ControlFont fontTitle = makeFont(11.0f, true);
		ControlFont fontSmall = makeFont(6.5f, false);

		_settingsGroup = addUIGroup("ui_settings_group", 0, 0, BASE_W, BASE_H);
		addUIBox("ui_settings_bg", _settingsGroup, 12, 10, BASE_W - 24, BASE_H - 20, app.color(14, 16, 28, 228));
		addUILabel("ui_settings_header", _settingsGroup, "Configuracion", 22, 16, app.color(255, 225, 130), fontTitle);

		// Columna Izquierda: Audio y Modo de Controles
		_setBgmLbl = addUILabel("ui_set_bgm_lbl", _settingsGroup, "BGM: 50%", 22, 38, app.color(220, 235, 255), fontSmall);
		addUIButton("ui_set_bgm_minus", _settingsGroup, "-", 22, 50, 22, 16, app.color(35, 45, 70), app.color(55, 85, 140), fontSmall,
			() -> { _settingsFocusIdx = SET_FOCUS_BGM; adjustBgmVolume(-0.1f); });
		addUIBox("ui_set_bgm_bar_bg", _settingsGroup, 48, 52, 146, 12, app.color(26, 30, 42));
		_setBgmBarFill = addUIBox("ui_set_bgm_bar_fill", _settingsGroup, 48, 52, 73, 12, app.color(80, 195, 255));
		addUIButton("ui_set_bgm_plus", _settingsGroup, "+", 198, 50, 22, 16, app.color(35, 45, 70), app.color(55, 85, 140), fontSmall,
			() -> { _settingsFocusIdx = SET_FOCUS_BGM; adjustBgmVolume(0.1f); });

		_setSfxLbl = addUILabel("ui_set_sfx_lbl", _settingsGroup, "SFX: 50%", 22, 74, app.color(220, 235, 255), fontSmall);
		addUIButton("ui_set_sfx_minus", _settingsGroup, "-", 22, 86, 22, 16, app.color(35, 45, 70), app.color(55, 85, 140), fontSmall,
			() -> { _settingsFocusIdx = SET_FOCUS_SFX; adjustSfxVolume(-0.1f); });
		addUIBox("ui_set_sfx_bar_bg", _settingsGroup, 48, 88, 146, 12, app.color(26, 30, 42));
		_setSfxBarFill = addUIBox("ui_set_sfx_bar_fill", _settingsGroup, 48, 88, 73, 12, app.color(95, 235, 165));
		addUIButton("ui_set_sfx_plus", _settingsGroup, "+", 198, 86, 22, 16, app.color(35, 45, 70), app.color(55, 85, 140), fontSmall,
			() -> { _settingsFocusIdx = SET_FOCUS_SFX; adjustSfxVolume(0.1f); });

		_setMuteBtn = addUIButton("ui_set_mute_btn", _settingsGroup, "MUTE GENERAL: OFF", 22, 114, 198, 22,
			app.color(32, 42, 66), app.color(55, 95, 155), fontSmall, () -> { _settingsFocusIdx = SET_FOCUS_MUTE; toggleMuteSetting(); });

		_setAltModeBtn = addUIButton("ui_set_alt_mode_btn", _settingsGroup, "CONTROLES: PRINCIPALES", 22, 148, 198, 24,
			app.color(32, 42, 66), app.color(55, 95, 155), fontSmall, () -> { _settingsFocusIdx = SET_FOCUS_ALT_MODE; toggleAltControlsMode(); });
		_setResetBtn = addUIButton("ui_set_reset_btn", _settingsGroup, "RESTAURAR CONTROLES", 22, 184, 198, 22,
			app.color(65, 52, 32), app.color(110, 88, 52), fontSmall, () -> { _settingsFocusIdx = SET_FOCUS_RESET; resetAllControlsSetting(); });
		_setBackBtn = addUIButton("ui_set_back_btn", _settingsGroup, "GUARDAR Y VOLVER", 22, 220, 198, 24,
			app.color(35, 115, 78), app.color(55, 160, 110), fontSmall, () -> {
				_settingsFocusIdx = SET_FOCUS_BACK;
				_rebindCategory = REBIND_NONE;
				Config.saveConfig(".config");
				switchView(ViewMode.MAIN);
			});

		// Columna Derecha: Rebinding de Teclas (únicamente Carriles Ritmo 6K: C1..C6)
		addUILabel("ui_set_rhythm_hdr", _settingsGroup, "CARRILES RITMO 6K", 236, 38, app.color(140, 215, 255), fontSmall);
		for (int i = 0; i < 6; i++) {
			final int lane = i;
			int col = i / 3; // 0: C1..C3, 1: C4..C6
			int row = i % 3;
			_setRhythmBtns[i] = addUIButton("ui_set_rhythm_" + i, _settingsGroup,
				"C" + (i + 1) + ": " + Config.getKeyLabel(Config.RhythmButtons[i]),
				236 + col * 114, 52 + row * 25, 108, 21,
				app.color(28, 34, 52), app.color(50, 95, 165), fontSmall, () -> {
					_settingsFocusIdx = SET_FOCUS_RHYTHM_START + lane;
					_rebindCategory = REBIND_RHYTHM;
					_rebindIndex = lane;
					syncSettingsUI();
				});
		}
	}

	/** Sincroniza las barras de volumen y las etiquetas de teclas asignadas en Configuración. */
	private void syncSettingsUI() {
		PApplet app = Engine.getApp();

		if (_setBgmLbl != null) {
			boolean f = (_settingsFocusIdx == SET_FOCUS_BGM);
			_setBgmLbl.setText((f ? "> " : "") + "BGM: " + Math.round(Config.BGMVolume * 100.0f) + "%" + (f ? "  [< / >]" : ""));
			_setBgmLbl.setColorValue(f ? app.color(255, 225, 110) : app.color(220, 235, 255));
		}
		if (_setBgmBarFill != null) {
			int w = Math.max(1, Math.round(scaleW(146) * Config.BGMVolume));
			_setBgmBarFill.setSize(w, Math.max(1, (int) scaleH(12)));
			_setBgmBarFill.setColorBackground((_settingsFocusIdx == SET_FOCUS_BGM) ? app.color(130, 225, 255) : app.color(80, 195, 255));
		}

		if (_setSfxLbl != null) {
			boolean f = (_settingsFocusIdx == SET_FOCUS_SFX);
			_setSfxLbl.setText((f ? "> " : "") + "SFX: " + Math.round(Config.SFXVolume * 100.0f) + "%" + (f ? "  [< / >]" : ""));
			_setSfxLbl.setColorValue(f ? app.color(255, 225, 110) : app.color(220, 235, 255));
		}
		if (_setSfxBarFill != null) {
			int w = Math.max(1, Math.round(scaleW(146) * Config.SFXVolume));
			_setSfxBarFill.setSize(w, Math.max(1, (int) scaleH(12)));
			_setSfxBarFill.setColorBackground((_settingsFocusIdx == SET_FOCUS_SFX) ? app.color(145, 255, 195) : app.color(95, 235, 165));
		}

		if (_setMuteBtn != null) {
			boolean f = (_settingsFocusIdx == SET_FOCUS_MUTE);
			_setMuteBtn.setLabel((f ? "> " : "") + "MUTE GENERAL: " + (Config.isMuted ? "ON" : "OFF"));
			_setMuteBtn.setColorBackground(
				f ? (Config.isMuted ? app.color(190, 65, 80) : app.color(55, 110, 195))
				  : (Config.isMuted ? app.color(145, 45, 58) : app.color(32, 42, 66))
			);
		}

		if (_setAltModeBtn != null) {
			boolean f = (_settingsFocusIdx == SET_FOCUS_ALT_MODE);
			_setAltModeBtn.setLabel((f ? "> " : "") + "CONTROLES: " + (_editingAltControls ? "ALTERNATIVOS" : "PRINCIPALES"));
			_setAltModeBtn.setColorBackground(
				f ? app.color(55, 110, 195)
				  : (_editingAltControls ? app.color(85, 60, 130) : app.color(32, 42, 66))
			);
		}

		if (_setResetBtn != null) {
			boolean f = (_settingsFocusIdx == SET_FOCUS_RESET);
			_setResetBtn.setLabel((f ? "> " : "") + "RESTAURAR CONTROLES");
			_setResetBtn.setColorBackground(f ? app.color(130, 102, 55) : app.color(65, 52, 32));
		}

		if (_setBackBtn != null) {
			boolean f = (_settingsFocusIdx == SET_FOCUS_BACK);
			_setBackBtn.setLabel((f ? "> " : "") + "GUARDAR Y VOLVER");
			_setBackBtn.setColorBackground(f ? app.color(55, 165, 110) : app.color(35, 115, 78));
		}

		int[] rhythmArr = _editingAltControls ? Config.RhythmAltButtons : Config.RhythmButtons;
		for (int i = 0; i < _setRhythmBtns.length; i++) {
			Button b = _setRhythmBtns[i];
			if (b == null) continue;
			boolean listening = (_rebindCategory == REBIND_RHYTHM && _rebindIndex == i);
			boolean f = (_settingsFocusIdx == SET_FOCUS_RHYTHM_START + i);
			b.setLabel((f ? "> " : "") + "C" + (i + 1) + ": " + (listening ? "[ ? ]" : Config.getKeyLabel(rhythmArr[i])));
			b.setColorBackground(
				listening ? app.color(175, 115, 25) : (f ? app.color(55, 110, 195) : app.color(28, 34, 52))
			);
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
		Audio.playSFX(ResourcesManager.loadSFX("sfx/Normal-Hitnormal.wav"));
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
		_rebindCategory = REBIND_NONE;
		_rebindIndex = -1;
		_editingAltControls = !_editingAltControls;
		syncSettingsUI();
	}

	private void resetAllControlsSetting() {
		_rebindCategory = REBIND_NONE;
		_rebindIndex = -1;
		Config.resetDefaultControls();
		Config.saveConfig(".config");
		syncSettingsUI();
	}

	// =========================================================================
	// 5. Navegación por teclado y acciones de foco
	// =========================================================================

	@Override
	public void keyEvent(KeyEvent e) {
		if (_viewMode == ViewMode.SETTINGS && _rebindCategory != REBIND_NONE && e.getAction() == KeyEvent.PRESS) {
			int code = e.getKeyCode();
			if (code == 27) { // ESC cancela la captura sin salir del menú en este frame
				_rebindCategory = REBIND_NONE;
				_rebindIndex = -1;
				_ignoreNavOnce = true;
				syncSettingsUI();
				return;
			}
			int normalized = KBInput.normalizeKey(code);
			if (normalized > 0 && normalized < 256) {
				int[] rhythmArr = _editingAltControls ? Config.RhythmAltButtons : Config.RhythmButtons;
				if (_rebindCategory == REBIND_RHYTHM && _rebindIndex >= 0 && _rebindIndex < rhythmArr.length) {
					rhythmArr[_rebindIndex] = normalized;
				}
				Config.saveConfig(".config");
				_rebindCategory = REBIND_NONE;
				_rebindIndex = -1;
				_ignoreNavOnce = true;
				syncSettingsUI();
			}
		}
	}

	/** Lee las entradas de navegación UI y las despacha a la vista activa. */
	private void handleKeyNavigation() {
		boolean up = Config.wasUIKeyJustPressed(Config.UP);
		boolean down = Config.wasUIKeyJustPressed(Config.DOWN);
		boolean left = Config.wasUIKeyJustPressed(Config.LEFT);
		boolean right = Config.wasUIKeyJustPressed(Config.RIGHT);
		boolean accept = Config.wasUIKeyJustPressed(Config.INTERACT) || Config.wasRPGKeyJustPressed(Config.INTERACT);
		boolean back = Config.wasUIKeyJustPressed(Config.CANCEL);

		switch (_viewMode) {
			case MAIN -> handleMainNavigation(up, down, accept);
			case SAVE_SELECT -> handleSaveSelectNavigation(up, down, left, right, accept, back);
			case SONG_SELECT -> handleSongSelectNavigation(up, down, left, right, accept, back);
			case SETTINGS -> handleSettingsNavigation(up, down, left, right, accept, back);
		}
	}

	private void handleMainNavigation(boolean up, boolean down, boolean accept) {
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

	private void activateMainButton(int idx) {
		switch (idx) {
			case MAIN_OPT_PLAY -> switchView(ViewMode.SAVE_SELECT);
			case MAIN_OPT_SETTINGS -> switchView(ViewMode.SETTINGS);
			case MAIN_OPT_EXIT -> Engine.getApp().exit();
		}
	}

	private void handleSaveSelectNavigation(
		boolean up,
		boolean down,
		boolean left,
		boolean right,
		boolean accept,
		boolean back
	) {
		if (back) {
			if (_confirmDeleteSlot >= 0) {
				_confirmDeleteSlot = -1;
				syncSaveSelectUI();
			} else {
				switchView(ViewMode.MAIN);
			}
			return;
		}

		int rows = GameState.SAVE_SLOT_COUNT + 1; // 0..3 ranuras + 4 botón volver
		if (up) {
			_confirmDeleteSlot = -1;
			_saveRowIdx = (_saveRowIdx - 1 + rows) % rows;
			if (_saveRowIdx >= GameState.SAVE_SLOT_COUNT || !GameState.hasSaveFile(_saveRowIdx)) {
				_saveFocusCol = SAVE_COL_SLOT;
			}
			syncSaveSelectUI();
		} else if (down) {
			_confirmDeleteSlot = -1;
			_saveRowIdx = (_saveRowIdx + 1) % rows;
			if (_saveRowIdx >= GameState.SAVE_SLOT_COUNT || !GameState.hasSaveFile(_saveRowIdx)) {
				_saveFocusCol = SAVE_COL_SLOT;
			}
			syncSaveSelectUI();
		} else if (left && _saveRowIdx < GameState.SAVE_SLOT_COUNT && _saveFocusCol != SAVE_COL_SLOT) {
			_confirmDeleteSlot = -1;
			_saveFocusCol = SAVE_COL_SLOT;
			syncSaveSelectUI();
		} else if (right && _saveRowIdx < GameState.SAVE_SLOT_COUNT && GameState.hasSaveFile(_saveRowIdx) && _saveFocusCol != SAVE_COL_DELETE) {
			_confirmDeleteSlot = -1;
			_saveFocusCol = SAVE_COL_DELETE;
			syncSaveSelectUI();
		} else if (accept) {
			if (_saveRowIdx >= GameState.SAVE_SLOT_COUNT) {
				switchView(ViewMode.MAIN);
			} else if (_saveFocusCol == SAVE_COL_DELETE) {
				deleteSaveSlotAction(_saveRowIdx);
			} else {
				launchSaveSlot(_saveRowIdx);
			}
		}
	}

	private void handleSongSelectNavigation(
		boolean up,
		boolean down,
		boolean left,
		boolean right,
		boolean accept,
		boolean back
	) {
		if (_pendingCustomRefresh) {
			_pendingCustomRefresh = false;
			refreshCustomSongs(false);
			syncSongSelectUI();
		}
		if (KBInput.wasKeyJustPressed(9)) { // TAB alterna entre CANCIONES y TIENDA
			setSongTab(_songTab == SONG_TAB_SONGS ? SONG_TAB_SHOP : SONG_TAB_SONGS);
			return;
		}

		if (_songTab == SONG_TAB_SHOP) {
			handleShopTabNavigation(up, down, left, right, accept, back);
			return;
		}

		if (_amuletDropdownOpen) {
			handleAmuletDropdownNavigation(up, down, left, accept, back);
			return;
		}

		if (back) {
			if (_songOptionsOpen) {
				_songOptionsOpen = false;
				_songFocusCol = SONG_COL_LIST;
				syncSongSelectUI();
			} else {
				GameState.saveGame();
				switchView(ViewMode.SAVE_SELECT);
			}
			return;
		}

		if (!_songOptionsOpen || _songFocusCol == SONG_COL_LIST) {
			int listSize = _allPacks.size();
			if (up && listSize > 0) {
				_selectedSongIdx = (_selectedSongIdx - 1 + listSize) % listSize;
				_selectedDiffIdx = 0;
				syncSongSelectUI();
			} else if (down && listSize > 0) {
				_selectedSongIdx = (_selectedSongIdx + 1) % listSize;
				_selectedDiffIdx = 0;
				syncSongSelectUI();
			} else if ((accept || right) && listSize > 0) {
				_songOptionsOpen = true;
				_songFocusCol = SONG_COL_OPTIONS;
				_songRightIdx = RIGHT_FOCUS_DIFF;
				syncSongSelectUI();
			}
		} else {
			handleSongOptionsNavigation(up, down, left, right, accept);
		}
	}

	/** Navegación dentro de la lista desplegable de amuletos en la pestaña CANCIONES. */
	private void handleAmuletDropdownNavigation(
		boolean up,
		boolean down,
		boolean left,
		boolean accept,
		boolean back
	) {
		if (back || left) {
			_amuletDropdownOpen = false;
			syncSongSelectUI();
			return;
		}
		List<GameState.ItemDef> owned = GameState.getOwnedPowerUps();
		if (owned.isEmpty()) {
			if (accept) {
				_amuletDropdownOpen = false;
				syncSongSelectUI();
			}
			return;
		}

		int total = owned.size() + 1; // 0..owned.size()-1 amuletos + botón CERRAR LISTA
		if (up) {
			_amuletDropIdx = (_amuletDropIdx - 1 + total) % total;
			syncSongSelectUI();
		} else if (down) {
			_amuletDropIdx = (_amuletDropIdx + 1) % total;
			syncSongSelectUI();
		} else if (accept) {
			if (_amuletDropIdx == owned.size()) {
				_amuletDropdownOpen = false;
				syncSongSelectUI();
			} else if (_amuletDropIdx >= 0 && _amuletDropIdx < owned.size()) {
				toggleAmuletWithFeedback(owned.get(_amuletDropIdx));
				syncSongSelectUI();
			}
		}
	}

	/** Navegación dentro de la pestaña TIENDA. */
	private void handleShopTabNavigation(
		boolean up,
		boolean down,
		boolean left,
		boolean right,
		boolean accept,
		boolean back
	) {
		if (back) {
			if (_songFocusCol == SONG_COL_OPTIONS) {
				_songFocusCol = SONG_COL_LIST;
				syncSongSelectUI();
			} else {
				GameState.saveGame();
				switchView(ViewMode.SAVE_SELECT);
			}
			return;
		}

		List<GameState.ItemDef> shopList = GameState.getShopAmulets();
		int listSize = shopList.size();
		if (listSize == 0) return;

		if (up) {
			_shopSelectedIdx = (_shopSelectedIdx - 1 + listSize) % listSize;
			syncSongSelectUI();
		} else if (down) {
			_shopSelectedIdx = (_shopSelectedIdx + 1) % listSize;
			syncSongSelectUI();
		} else if (right && _songFocusCol == SONG_COL_LIST) {
			_songFocusCol = SONG_COL_OPTIONS;
			_songRightIdx = RIGHT_FOCUS_PLAY;
			syncSongSelectUI();
		} else if (left && _songFocusCol == SONG_COL_OPTIONS) {
			_songFocusCol = SONG_COL_LIST;
			syncSongSelectUI();
		} else if (accept) {
			executeShopPrimaryAction();
		}
	}

	/** Navegación 2D sobre el panel derecho de modificadores y ejecución de la canción. */
	private void handleSongOptionsNavigation(
		boolean up,
		boolean down,
		boolean left,
		boolean right,
		boolean accept
	) {
		if (up) {
			_songRightIdx = switch (_songRightIdx) {
				case RIGHT_FOCUS_DIFF -> RIGHT_FOCUS_PLAY;
				case RIGHT_FOCUS_M1, RIGHT_FOCUS_M2 -> RIGHT_FOCUS_DIFF;
				case RIGHT_FOCUS_M3 -> RIGHT_FOCUS_M1;
				case RIGHT_FOCUS_M4 -> RIGHT_FOCUS_M2;
				default -> RIGHT_FOCUS_M3;
			};
			syncSongSelectUI();
		} else if (down) {
			_songRightIdx = switch (_songRightIdx) {
				case RIGHT_FOCUS_DIFF -> RIGHT_FOCUS_M1;
				case RIGHT_FOCUS_M1 -> RIGHT_FOCUS_M3;
				case RIGHT_FOCUS_M2 -> RIGHT_FOCUS_M4;
				case RIGHT_FOCUS_M3, RIGHT_FOCUS_M4 -> RIGHT_FOCUS_PLAY;
				default -> RIGHT_FOCUS_DIFF;
			};
			syncSongSelectUI();
		} else if (left) {
			switch (_songRightIdx) {
				case RIGHT_FOCUS_DIFF -> cycleSelectedDifficulty(-1);
				case RIGHT_FOCUS_M2 -> { _songRightIdx = RIGHT_FOCUS_M1; syncSongSelectUI(); }
				case RIGHT_FOCUS_M4 -> { _songRightIdx = RIGHT_FOCUS_M3; syncSongSelectUI(); }
				default -> {
					_songOptionsOpen = false;
					_songFocusCol = SONG_COL_LIST;
					syncSongSelectUI();
				}
			}
		} else if (right) {
			switch (_songRightIdx) {
				case RIGHT_FOCUS_DIFF -> cycleSelectedDifficulty(1);
				case RIGHT_FOCUS_M1 -> { _songRightIdx = RIGHT_FOCUS_M2; syncSongSelectUI(); }
				case RIGHT_FOCUS_M3 -> { _songRightIdx = RIGHT_FOCUS_M4; syncSongSelectUI(); }
			}
		} else if (accept) {
			activateSongRightOption(_songRightIdx);
		}
	}

	private void activateSongRightOption(int rightIdx) {
		switch (rightIdx) {
			case RIGHT_FOCUS_DIFF -> cycleSelectedDifficulty(1);
			case RIGHT_FOCUS_M1 -> openAmuletDropdown();
			case RIGHT_FOCUS_M2 -> {
				_songInvincibleMode = !_songInvincibleMode;
				syncSongSelectUI();
			}
			case RIGHT_FOCUS_M3 -> {
				GameState.toggleHealStrategy();
				syncSongSelectUI();
			}
			case RIGHT_FOCUS_M4 -> {
				_songSpeedIdx = (_songSpeedIdx + 1) % SPEED_OPTIONS.length;
				syncSongSelectUI();
			}
			case RIGHT_FOCUS_PLAY -> launchSelectedSong();
		}
	}

	private void handleSettingsNavigation(
		boolean up,
		boolean down,
		boolean left,
		boolean right,
		boolean accept,
		boolean back
	) {
		if (_rebindCategory != REBIND_NONE) return;
		if (back) {
			_savedSettingsFocusIdx = 0;
			Config.saveConfig(".config");
			switchView(ViewMode.MAIN);
			return;
		}

		// Columna izquierda (0..5): Audio y acciones generales
		if (_settingsFocusIdx < SET_LEFT_COL_COUNT) {
			if (up) {
				_settingsFocusIdx = (_settingsFocusIdx - 1 + SET_LEFT_COL_COUNT) % SET_LEFT_COL_COUNT;
				syncSettingsUI();
			} else if (down) {
				_settingsFocusIdx = (_settingsFocusIdx + 1) % SET_LEFT_COL_COUNT;
				syncSettingsUI();
			} else {
				switch (_settingsFocusIdx) {
					case SET_FOCUS_BGM -> {
						if (left) adjustBgmVolume(-0.1f);
						else if (right || accept) adjustBgmVolume(0.1f);
					}
					case SET_FOCUS_SFX -> {
						if (left) adjustSfxVolume(-0.1f);
						else if (right || accept) adjustSfxVolume(0.1f);
					}
					case SET_FOCUS_MUTE -> {
						if (right) { _settingsFocusIdx = 7; syncSettingsUI(); }
						else if (accept) toggleMuteSetting();
					}
					case SET_FOCUS_ALT_MODE -> {
						if (right) { _settingsFocusIdx = 8; syncSettingsUI(); }
						else if (accept) toggleAltControlsMode();
					}
					case SET_FOCUS_RESET -> {
						if (right) { _settingsFocusIdx = 9; syncSettingsUI(); }
						else if (accept) resetAllControlsSetting();
					}
					case SET_FOCUS_BACK -> {
						if (right) { _settingsFocusIdx = 9; syncSettingsUI(); }
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

		// Columna derecha: Carriles 6K (7..12)
		if (accept) {
			if (_settingsFocusIdx >= SET_FOCUS_RHYTHM_START && _settingsFocusIdx <= SET_FOCUS_RHYTHM_END) {
				_rebindCategory = REBIND_RHYTHM;
				_rebindIndex = _settingsFocusIdx - SET_FOCUS_RHYTHM_START;
				syncSettingsUI();
			}
			return;
		}

		int row = 0, col = 0;
		for (int r = 0; r < SETTINGS_RIGHT_GRID.length; r++) {
			for (int c = 0; c < 2; c++) {
				if (SETTINGS_RIGHT_GRID[r][c] == _settingsFocusIdx) {
					row = r;
					col = c;
				}
			}
		}

		if (left) {
			if (col == 1) {
				_settingsFocusIdx = SETTINGS_RIGHT_GRID[row][0];
			} else {
				_settingsFocusIdx = switch (row) {
					case 0 -> SET_FOCUS_MUTE;
					case 1 -> SET_FOCUS_ALT_MODE;
					default -> SET_FOCUS_RESET;
				};
			}
			syncSettingsUI();
		} else if (right) {
			if (col == 0) {
				_settingsFocusIdx = SETTINGS_RIGHT_GRID[row][1];
				syncSettingsUI();
			}
		} else if (up) {
			int nextRow = (row - 1 + SETTINGS_RIGHT_GRID.length) % SETTINGS_RIGHT_GRID.length;
			_settingsFocusIdx = SETTINGS_RIGHT_GRID[nextRow][col];
			syncSettingsUI();
		} else if (down) {
			int nextRow = (row + 1) % SETTINGS_RIGHT_GRID.length;
			_settingsFocusIdx = SETTINGS_RIGHT_GRID[nextRow][col];
			syncSettingsUI();
		}
	}

	// =========================================================================
	// 6. Helpers de catálogo de canciones, portadas e importación de .osu
	// =========================================================================

	/** Inicializa las posiciones pseudoaleatorias de las estrellas del fondo. */
	private void initBackgroundStars() {
		for (int i = 0; i < _starX.length; i++) {
			_starX[i] = ((i * 73 + 19) % (int) (BASE_W - 12)) + 6;
			_starY[i] = ((i * 37 + 11) % 98) + 6;
			_starPhase[i] = i * 0.75f;
		}
	}

	/** Sincroniza el multiplicador de velocidad con el estado global de GameState. */
	private void syncSpeedSelectionFromState() {
		_songSpeedIdx = 1;
		for (int i = 0; i < SPEED_OPTIONS.length; i++) {
			if (Math.abs(SPEED_OPTIONS[i] - GameState.songSpeedMultiplier) < 0.02f) {
				_songSpeedIdx = i;
				break;
			}
		}
	}

	/** Escanea el catálogo de canciones base agrupadas por paquete y dificultades. */
	private void scanSongsDirectory(boolean forceRefresh) {
		_basePacks.clear();
		_basePacks.addAll(SongPackLoader.loadBasePacks(forceRefresh));
		rebuildAllPacks();
	}

	/** Recarga el catálogo de canciones custom desde SQLite y la carpeta de usuario. */
	private void refreshCustomSongs(boolean syncFolder) {
		_customPacks.clear();
		_customPacks.addAll(SongPackLoader.loadCustomPacks(syncFolder));
		rebuildAllPacks();
	}

	/** Reconstruye la lista unificada de canciones (Base + Custom). */
	private void rebuildAllPacks() {
		_allPacks.clear();
		_allPacks.addAll(_basePacks);
		_allPacks.addAll(_customPacks);
		if (_selectedSongIdx >= _allPacks.size()) {
			_selectedSongIdx = Math.max(0, _allPacks.size() - 1);
		}
		_selectedDiffIdx = clampDifficultyIdx(_allPacks, _selectedSongIdx, _selectedDiffIdx);
		ensureSelectionVisible();
	}

	private int clampDifficultyIdx(List<SongPackLoader.SongPack> packs, int packIdx, int diffIdx) {
		if (packs.isEmpty() || packIdx < 0 || packIdx >= packs.size()) return 0;
		int n = packs.get(packIdx).difficulties.size();
		if (n <= 0) return 0;
		return Math.max(0, Math.min(diffIdx, n - 1));
	}

	private SongPackLoader.SongPack getActivePack() {
		if (_allPacks.isEmpty() || _selectedSongIdx < 0 || _selectedSongIdx >= _allPacks.size()) return null;
		return _allPacks.get(_selectedSongIdx);
	}

	private SongPackLoader.ChartVariant getActiveChartVariant() {
		SongPackLoader.SongPack pack = getActivePack();
		if (pack == null || pack.difficulties.isEmpty()) return null;
		int dIdx = Math.max(0, Math.min(_selectedDiffIdx, pack.difficulties.size() - 1));
		return pack.difficulties.get(dIdx);
	}

	private void cycleSelectedDifficulty(int delta) {
		SongPackLoader.SongPack pack = getActivePack();
		if (pack == null || pack.difficulties.size() <= 1) return;
		int n = pack.difficulties.size();
		_selectedDiffIdx = (_selectedDiffIdx + delta + n) % n;
		syncSongSelectUI();
	}

	private void ensureSelectionVisible() {
		int cap = _songListBtns.length;
		if (_songTab == SONG_TAB_SHOP) {
			List<GameState.ItemDef> shopList = GameState.getShopAmulets();
			if (_shopSelectedIdx < _shopScrollOffset) _shopScrollOffset = _shopSelectedIdx;
			else if (_shopSelectedIdx >= _shopScrollOffset + cap) _shopScrollOffset = _shopSelectedIdx - cap + 1;
			_shopScrollOffset = Math.max(0, Math.min(_shopScrollOffset, Math.max(0, shopList.size() - cap)));
		} else {
			if (_selectedSongIdx < _songScrollOffset) _songScrollOffset = _selectedSongIdx;
			else if (_selectedSongIdx >= _songScrollOffset + cap) _songScrollOffset = _selectedSongIdx - cap + 1;
			_songScrollOffset = Math.max(0, Math.min(_songScrollOffset, Math.max(0, _allPacks.size() - cap)));
		}
	}

	/** Actualiza la miniatura de portada y el badge de BPM/Dificultad de la canción activa. */
	private void refreshSelectedCover(SongPackLoader.ChartVariant activeVar, float speedMult) {
		if (_songCoverBox == null || _songBadgeArea == null) return;
		if (activeVar == null) {
			PImage fallback = getScaledCoverImage("");
			_songCoverBox.setSize(fallback.width, fallback.height).setImage(fallback).show();
			_songBadgeArea.setText("BPM: -").show();
		} else {
			PImage cover = getScaledCoverImage(activeVar.coverRelPath);
			float effBpm = activeVar.bpm * Math.max(0.5f, speedMult);
			float effStars = SongPackLoader.computeDifficultyRating(activeVar.noteCount, activeVar.bpm, activeVar.durationSec, speedMult);
			_songCoverBox.setSize(cover.width, cover.height).setImage(cover).show();
			_songBadgeArea.setText(
				"BPM: " + String.format("%.0f", effBpm) + "\n" +
				"Dif: " + String.format("%.1f*", effStars)
			).show();
		}
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

	/** Abre en segundo plano un selector de archivos nativo para importar un beatmap .osu externo. */
	private void openCustomOsuFilePicker() {
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
}
