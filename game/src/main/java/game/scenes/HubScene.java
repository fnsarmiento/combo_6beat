package game.scenes;

import static game.Global.BASE_H;
import static game.Global.BASE_W;

import engine.Engine;
import engine.ResourcesManager;
import engine.Scene;
import engine.audio.Audio;
import engine.draw.Sprite2D;
import game.Config;
import game.Global;
import game.core.GameState;
import java.util.ArrayList;
import java.util.List;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics;

/**
 * Hub central interactivo por salas al ritmo (inspiracion Crypt of the NecroDancer).
 * <p>
 * Presenta una sala compacta en grilla que pulsa al BPM del lobby, con portales directos
 * a los combates ritmicos de historia (6 carriles), selector arcade (.osu + SQLite),
 * NPC guia con dialogos narrativos, altar de chips/curacion y cristal de guardado SQLite.
 */
public class HubScene extends Scene {

	// =========================================================================
	// CONSTANTES DEL MAPA/RITMO Y TIPOS DE TILE / ESTACIONES
	// =========================================================================

	/** Dimensiones de la grilla compacta centrada sobre el lienzo virtual 480x270. */
	private static final int GRID_COLS = 15;
	private static final int GRID_ROWS = 8;
	private static final int TILE_SIZE = 28;
	private static final float BOARD_X = (BASE_W - GRID_COLS * TILE_SIZE) * 0.5f; // 30.0f
	private static final float BOARD_Y = 22.0f;

	/** Ritmo visual del lobby (120 BPM = 0.5 segundos por pulso). */
	private static final float LOBBY_BPM = 120.0f;
	private static final float BEAT_INTERVAL = 60.0f / LOBBY_BPM;

	/** Duracion de la interpolacion del salto (hop) entre casillas y enfriamiento de paso. */
	private static final float HOP_DURATION = 0.13f;
	private static final float MOVE_COOLDOWN_SECONDS = 0.14f;

	/** Direcciones cardinales de orientacion del sprite de la protagonista. */
	private static final int FACING_DOWN = 0;
	private static final int FACING_UP = 1;
	private static final int FACING_LEFT = 2;
	private static final int FACING_RIGHT = 3;

	/** Identificadores de chips Power-Up ciclables en el Altar de Chips. */
	private static final String[] CHIP_IDS = {
		"none",
		"powerup_reaction_plus",
		"powerup_reaction_minus",
		"powerup_invincibility"
	};

	/** Categorias de casillas interactivas (estaciones) dispuestas en el Hub. */
	private enum StationType {
		SONG_PORTAL,
		ARCADE_PORTAL,
		NPC_DIALOG,
		CHIP_ALTAR,
		SAVE_ALTAR,
		MENU_EXIT
	}

	/** Definicion inmutable de una estacion o portal ubicado en una coordenada de la grilla. */
	private static class HubStation {
		final int col;
		final int row;
		final StationType type;
		final String shortLabel;
		final String title;
		final String subtitle;
		final String targetPath;
		final String defeatFlag;
		final int accentR;
		final int accentG;
		final int accentB;

		HubStation(
			int col,
			int row,
			StationType type,
			String shortLabel,
			String title,
			String subtitle,
			String targetPath,
			String defeatFlag,
			int r,
			int g,
			int b
		) {
			this.col = col;
			this.row = row;
			this.type = type;
			this.shortLabel = shortLabel;
			this.title = title;
			this.subtitle = subtitle;
			this.targetPath = targetPath;
			this.defeatFlag = defeatFlag;
			this.accentR = r;
			this.accentG = g;
			this.accentB = b;
		}
	}

	// =========================================================================
	// ESTADO DE LA ESCENA Y ENTIDADES INTERACTIVAS
	// =========================================================================

	/** Posicion logica actual del jugador en la grilla y estado de interpolacion del salto. */
	private int _playerCol = 7;
	private int _playerRow = 4;
	private float _drawX;
	private float _drawY;
	private float _hopFromX;
	private float _hopFromY;
	private float _hopToX;
	private float _hopToY;
	private float _hopTimer = 1.0f;
	private int _facing = FACING_DOWN;

	/** Spritesheets de la protagonista y entidades visuales del Hub. */
	private Sprite2D _sprDown;
	private Sprite2D _sprUp;
	private Sprite2D _sprLeft;
	private Sprite2D _sprRight;
	private Sprite2D _npcCat;
	private Sprite2D _bossBear;

	/** Lista de estaciones interactivas activas en el tablero. */
	private final List<HubStation> _stations = new ArrayList<>();

	/** Reloj de pulso ritmico y estado de notificaciones temporales (toast) en barra inferior. */
	private float _time = 0.0f;
	private float _beatPhase = 0.0f;
	private int _beatCount = 0;
	private float _moveCooldown = 0.0f;
	private String _toastMsg = "";
	private float _toastTimer = 0.0f;

	public HubScene() {
		super();
	}

	/** Inicializa los spritesheets del jugador y de los personajes presentes en la sala. */
	private void initSprites() {
		_sprDown = createCharSprite("sprites/player/The Female Adventurer - Free/Idle/Idle_Down.png");
		_sprUp = createCharSprite("sprites/player/The Female Adventurer - Free/Idle/Idle_Up.png");
		_sprLeft = createCharSprite("sprites/player/The Female Adventurer - Free/Idle/Idle_Left_Down.png");
		_sprRight = createCharSprite("sprites/player/The Female Adventurer - Free/Idle/Idle_Right_Down.png");

		_npcCat = new Sprite2D("sprites/npcs/cat.png", 2, 3, 5);
		_npcCat.centerPosition = true;
		_npcCat.scale.set(1.2f, 1.2f);
		_npcCat.delay = 0.18f;

		_bossBear = new Sprite2D("sprites/bosses/bear.png", 3, 3, 5);
		_bossBear.centerPosition = true;
		_bossBear.scale.set(0.65f, 0.65f);
		_bossBear.delay = 0.11f;
	}

	private Sprite2D createCharSprite(String path) {
		Sprite2D s = new Sprite2D(path, 1, 8, 5);
		s.centerPosition = true;
		s.scale.set(1.1f, 1.1f);
		s.delay = 0.11f;
		return s;
	}

	/** Registra todas las estaciones interactivas (portales de jefe, arcade, NPC, altares y salida). */
	private void initStations() {
		_stations.clear();

		// Fila superior: portales de combate ritmico (apuntan a charts en resources/songs/songs.xml)
		_stations.add(new HubStation(
			3, 1, StationType.SONG_PORTAL,
			"Z1", "ZONA 1: BLOODY DARKNESS [BEGINNER]", "Combate Ritmico 6K — Kael",
			"bloody_darkness_beginner", "BOSS_SWAMP_1_DEFEATED",
			80, 220, 145
		));
		_stations.add(new HubStation(
			6, 1, StationType.SONG_PORTAL,
			"Z2", "ZONA 2: BLOODY DARKNESS [EASY]", "Combate Ritmico 6K — Kael & Vesper",
			"bloody_darkness_easy", "BOSS_BEACH_1_DEFEATED",
			95, 195, 255
		));
		_stations.add(new HubStation(
			8, 1, StationType.SONG_PORTAL,
			"Z3", "ZONA 3: SWIFT SWING [BASIC]", "Combate Ritmico 6K — Nash",
			"swift_swing_basic", "BOSS_NASH_DEFEATED",
			255, 185, 70
		));
		_stations.add(new HubStation(
			11, 1, StationType.SONG_PORTAL,
			"Z4", "ZONA 4: TAKEMIKADUCHI [NORMAL]", "Combate Ritmico 6K — Grail",
			"takemikaduchi_normal", "BOSS_BEACH_3_DEFEATED",
			240, 85, 125
		));

		// Estacion izquierda: Selector Arcade / Canciones Custom (.osu + SQLite)
		_stations.add(new HubStation(
			2, 4, StationType.ARCADE_PORTAL,
			"OSU", "PORTAL ARCADE / CUSTOM", "Selector de canciones .osu y catalogo SQLite",
			"", "",
			165, 115, 255
		));

		// NPC Guia (centro-izquierda)
		_stations.add(new HubStation(
			5, 4, StationType.NPC_DIALOG,
			"NPC", "LA GATA (GUIA DEL HUB)", "Hablar — Historia y consejos",
			"__start__", "",
			255, 225, 130
		));

		// Altar de Chips / Power-ups (centro-derecha)
		_stations.add(new HubStation(
			9, 4, StationType.CHIP_ALTAR,
			"CHIP", "ALTAR DE CHIPS & CURA", "Cambiar Power-Up (Z) o Curacion (X) — Guarda en BD",
			"", "",
			75, 225, 235
		));

		// Altar de Guardado SQLite (derecha)
		_stations.add(new HubStation(
			12, 4, StationType.SAVE_ALTAR,
			"SAVE", "CRISTAL DE GUARDADO (SQLITE)", "Guardar partida en ranura activa y ver estado BD",
			"", "",
			110, 245, 155
		));

		// Salida al menu principal (abajo centro)
		_stations.add(new HubStation(
			7, 6, StationType.MENU_EXIT,
			"MENU", "SALIR AL MENU PRINCIPAL", "Volver a la pantalla de titulo",
			"", "",
			215, 95, 105
		));
	}

	/** Restaura la coordenada de grilla previa del jugador o lo ubica en el centro de la sala. */
	private void restorePlayerGridPosition() {
		int savedCol = Math.round(GameState.returnPlayerX);
		int savedRow = Math.round(GameState.returnPlayerY);
		if (savedCol >= 1 && savedCol <= GRID_COLS - 2 && savedRow >= 1 && savedRow <= GRID_ROWS - 2) {
			_playerCol = savedCol;
			_playerRow = savedRow;
		} else {
			_playerCol = 7;
			_playerRow = 4;
		}
		_drawX = tileCenterX(_playerCol);
		_drawY = tileCenterY(_playerRow);
		_hopFromX = _drawX;
		_hopFromY = _drawY;
		_hopToX = _drawX;
		_hopToY = _drawY;
		_hopTimer = 1.0f;
	}

	/** Procesa las banderas de retorno tras finalizar una batalla ritmica (victoria o derrota). */
	private void processPostBattleFeedback() {
		if (GameState.hasFlag("LAST_BATTLE_WON")) {
			GameState.clearFlag("LAST_BATTLE_WON");
			GameState.autoSaveIfStory();
			showToast("VICTORIA REGISTRADA EN SQLITE (RANURA " + (GameState.activeSaveSlot + 1) + ")");
		} else if (GameState.hasFlag("LAST_BATTLE_LOST")) {
			GameState.clearFlag("LAST_BATTLE_LOST");
			showToast("DERROTA — AJUSTA TU CHIP O VUELVE A INTENTAR");
		}
	}

	// =========================================================================
	// CICLO DE VIDA (SETUP, UPDATE, DRAW)
	// =========================================================================

	@Override
	public void setup() {
		Engine.ensureVirtualScreenSize((int) BASE_W, (int) BASE_H);
		GameState.init();
		GameState.currentScenePath = "resources/scenes/hub.xml";
		GameState.returnScenePath = "resources/scenes/hub.xml";
		GameState.launchedFromMenu = false;

		super.setup();
		Audio.ensurePersistentBGM(ResourcesManager.getAbsPathFromProject(Global.MENU_HUB_BGM));

		initSprites();
		initStations();
		restorePlayerGridPosition();
		processPostBattleFeedback();
	}

	@Override
	public void update(float dt) {
		_time += dt;
		_beatCount = (int) (_time / BEAT_INTERVAL);
		_beatPhase = (_time % BEAT_INTERVAL) / BEAT_INTERVAL;

		if (_toastTimer > 0.0f) {
			_toastTimer = Math.max(0.0f, _toastTimer - dt);
		}
		if (_moveCooldown > 0.0f) {
			_moveCooldown = Math.max(0.0f, _moveCooldown - dt);
		}

		updateSpriteAnimations(dt);
		updateHopInterpolation(dt);

		handleGridMovementInput();
		handleTileInteractionInput();
	}

	@Override
	public void draw(PGraphics g) {
		g.pushStyle();
		g.background(10, 9, 18);

		float beatPulse = 1.0f - _beatPhase; // 1.0 en el golpe, decae a 0.0
		float beatGlow = beatPulse * beatPulse;

		drawBoard(g, beatGlow);
		drawStations(g, beatGlow);
		drawPlayer(g, beatGlow);
		drawTopHud(g);
		drawBottomBar(g, beatGlow);

		g.popStyle();
	}

	private void updateSpriteAnimations(float dt) {
		if (_sprDown != null) _sprDown.updateDraw(dt);
		if (_sprUp != null) _sprUp.updateDraw(dt);
		if (_sprLeft != null) _sprLeft.updateDraw(dt);
		if (_sprRight != null) _sprRight.updateDraw(dt);
		if (_npcCat != null) _npcCat.updateDraw(dt);
		if (_bossBear != null) _bossBear.updateDraw(dt);
	}

	private void updateHopInterpolation(float dt) {
		if (_hopTimer < 1.0f) {
			_hopTimer = Math.min(1.0f, _hopTimer + dt / HOP_DURATION);
			float ease = 1.0f - (1.0f - _hopTimer) * (1.0f - _hopTimer);
			_drawX = PApplet.lerp(_hopFromX, _hopToX, ease);
			_drawY = PApplet.lerp(_hopFromY, _hopToY, ease);
		} else {
			_drawX = tileCenterX(_playerCol);
			_drawY = tileCenterY(_playerRow);
		}
	}

	// =========================================================================
	// MOVIMIENTO POR CASILLAS E INTERACCION CON PORTALES / NPC / ALTARES
	// =========================================================================

	/** Procesa las teclas direccionales para saltar entre casillas de la grilla. */
	private void handleGridMovementInput() {
		if (_moveCooldown > 0.0f || _hopTimer < 0.85f) return;

		int dx = 0;
		int dy = 0;
		if (Config.isRPGKeyDown(Config.UP) || Config.wasUIKeyJustPressed(Config.UP)) {
			dy = -1;
			_facing = FACING_UP;
		} else if (Config.isRPGKeyDown(Config.DOWN) || Config.wasUIKeyJustPressed(Config.DOWN)) {
			dy = 1;
			_facing = FACING_DOWN;
		} else if (Config.isRPGKeyDown(Config.LEFT) || Config.wasUIKeyJustPressed(Config.LEFT)) {
			dx = -1;
			_facing = FACING_LEFT;
		} else if (Config.isRPGKeyDown(Config.RIGHT) || Config.wasUIKeyJustPressed(Config.RIGHT)) {
			dx = 1;
			_facing = FACING_RIGHT;
		}

		if (dx != 0 || dy != 0) {
			tryHop(dx, dy);
		}
	}

	/** Procesa las teclas de confirmacion o cancelacion sobre la casilla actual. */
	private void handleTileInteractionInput() {
		if (Config.wasUIKeyJustPressed(Config.INTERACT) || Config.wasRPGKeyJustPressed(Config.INTERACT)) {
			interactCurrentTile();
		} else if (Config.wasUIKeyJustPressed(Config.CANCEL)) {
			HubStation current = getStationAt(_playerCol, _playerRow);
			if (current != null && current.type == StationType.CHIP_ALTAR) {
				toggleHealStrategy();
			} else {
				exitToMainMenu();
			}
		}
	}

	/** Intenta realizar un salto hacia la casilla adyacente `(dx, dy)` dentro del area transitable. */
	private void tryHop(int dx, int dy) {
		int nextCol = _playerCol + dx;
		int nextRow = _playerRow + dy;
		// Borde interior transitable: 1..GRID_COLS-2, 1..GRID_ROWS-2
		if (nextCol < 1 || nextCol >= GRID_COLS - 1 || nextRow < 1 || nextRow >= GRID_ROWS - 1) {
			return;
		}
		_hopFromX = _drawX;
		_hopFromY = _drawY;
		_playerCol = nextCol;
		_playerRow = nextRow;
		_hopToX = tileCenterX(_playerCol);
		_hopToY = tileCenterY(_playerRow);
		_hopTimer = 0.0f;
		_moveCooldown = MOVE_COOLDOWN_SECONDS;
	}

	/** Ejecuta la accion asociada a la estacion ubicada en la casilla actual del jugador. */
	private void interactCurrentTile() {
		HubStation st = getStationAt(_playerCol, _playerRow);
		if (st == null) return;

		switch (st.type) {
			case SONG_PORTAL -> launchSongFromHub(st.targetPath);
			case ARCADE_PORTAL -> openArcadeSongSelect();
			case NPC_DIALOG -> showHubNpcHint();
			case CHIP_ALTAR -> cycleChip();
			case SAVE_ALTAR -> saveProgressAtAltar();
			case MENU_EXIT -> exitToMainMenu();
		}
	}

	private void openArcadeSongSelect() {
		GameState.returnPlayerX = _playerCol;
		GameState.returnPlayerY = _playerRow;
		GameState.autoSaveIfStory();
		Engine.changeScene(new InitScene(InitScene.ViewMode.SONG_SELECT));
	}

	private void saveProgressAtAltar() {
		GameState.returnPlayerX = _playerCol;
		GameState.returnPlayerY = _playerRow;
		GameState.saveGame("resources/scenes/hub.xml", _playerCol, _playerRow);
		GameState.SaveSlotSummary sum = GameState.getSaveSlotSummary(GameState.activeSaveSlot);
		showToast(
			"GUARDADO SQLITE RANURA " + (GameState.activeSaveSlot + 1) +
			" | JEFES: " + countDefeatedHubBosses() + "/4 | ITEMS: " + sum.itemCount
		);
	}

	private void exitToMainMenu() {
		GameState.returnPlayerX = _playerCol;
		GameState.returnPlayerY = _playerRow;
		GameState.autoSaveIfStory();
		Engine.changeScene(new InitScene(InitScene.ViewMode.MAIN));
	}

	/** Cicla el chip Power-Up equipado en el Altar de Chips y persiste el cambio en SQLite. */
	private void cycleChip() {
		int idx = 0;
		for (int i = 0; i < CHIP_IDS.length; i++) {
			if (CHIP_IDS[i].equals(GameState.equippedPowerUp)) {
				idx = i;
				break;
			}
		}
		idx = (idx + 1) % CHIP_IDS.length;
		String nextChip = CHIP_IDS[idx];
		if ("none".equals(nextChip)) {
			GameState.equipPowerUp("none");
			showToast("CHIP: NINGUNO (GUARDADO EN SQLITE)");
		} else {
			if (!GameState.hasItem(nextChip)) {
				GameState.addItem(nextChip, 1);
			}
			GameState.equipPowerUp(nextChip);
			showToast("CHIP EQUIPADO: " + GameState.getItemName(nextChip));
		}
		GameState.autoSaveIfStory();
	}

	/** Alterna la estrategia automatica de curacion entre OPTIMAL y MINIMAL. */
	private void toggleHealStrategy() {
		GameState.healStrategy = (GameState.healStrategy == GameState.HealStrategy.OPTIMAL)
			? GameState.HealStrategy.MINIMAL
			: GameState.HealStrategy.OPTIMAL;
		GameState.autoSaveIfStory();
		showToast("ESTRATEGIA DE CURA: " + GameState.healStrategy.label.toUpperCase());
	}

	private float tileCenterX(int col) {
		return BOARD_X + col * TILE_SIZE + TILE_SIZE * 0.5f;
	}

	private float tileCenterY(int row) {
		return BOARD_Y + row * TILE_SIZE + TILE_SIZE * 0.5f;
	}

	private HubStation getStationAt(int col, int row) {
		for (HubStation st : _stations) {
			if (st.col == col && st.row == row) return st;
		}
		return null;
	}

	private void showToast(String msg) {
		_toastMsg = msg;
		_toastTimer = 3.2f;
	}

	private int countDefeatedHubBosses() {
		int count = 0;
		for (HubStation st : _stations) {
			if (st.type == StationType.SONG_PORTAL && !st.defeatFlag.isEmpty() && GameState.hasFlag(st.defeatFlag)) {
				count++;
			}
		}
		return count;
	}

	// =========================================================================
	// GUIA DEL NPC Y LANZADORES DE BATALLAS
	// =========================================================================

	/** Muestra un consejo rapido de estado/progreso al interactuar con La Gata en el Hub. */
	private void showHubNpcHint() {
		int defeated = countDefeatedHubBosses();
		if (defeated >= 4) {
			showToast("LA GATA: Miau! Dominaste los 4 portales! Prueba el portal OSU o mejora tus puntajes.");
		} else {
			showToast("LA GATA: Miau! Portales vencidos: " + defeated + "/4. Equipa un chip en CHIP antes de entrar.");
		}
	}

	/** Guarda la posicion actual en el Hub y lanza el combate ritmico de 6 carriles (`ROTNScene`). */
	private void launchSongFromHub(String target) {
		GameState.returnScenePath = "resources/scenes/hub.xml";
		GameState.returnPlayerX = _playerCol;
		GameState.returnPlayerY = _playerRow;
		GameState.launchedFromMenu = false;
		GameState.autoSaveIfStory();
		if (target != null && !target.endsWith(".scene")) {
			Engine.changeScene(ROTNScene.fromManifest(target));
		} else {
			String abs = ResourcesManager.getAbsPathFromProject(target);
			Engine.changeScene(new ROTNScene(abs));
		}
	}

	// =========================================================================
	// RENDERIZADO POR CAPAS (SUELO AL RITMO, PORTALES, NPC/JUGADOR, HUD Y PROMPTS)
	// =========================================================================

	/** Capa 1: Tablero en grilla con muros perimetrales, antorchas y suelo que alterna al pulso del BPM. */
	private void drawBoard(PGraphics g, float beatGlow) {
		g.noStroke();
		for (int r = 0; r < GRID_ROWS; r++) {
			for (int c = 0; c < GRID_COLS; c++) {
				float x = BOARD_X + c * TILE_SIZE;
				float y = BOARD_Y + r * TILE_SIZE;
				boolean isWall = (c == 0 || c == GRID_COLS - 1 || r == 0 || r == GRID_ROWS - 1);

				if (isWall) {
					drawWallTile(g, c, r, x, y, beatGlow);
				} else {
					drawFloorTile(g, c, r, x, y, beatGlow);
				}
			}
		}

		// Borde exterior del tablero
		g.noFill();
		g.stroke(90, 78, 130, 180);
		g.rect(BOARD_X, BOARD_Y, GRID_COLS * TILE_SIZE, GRID_ROWS * TILE_SIZE);
	}

	private void drawWallTile(PGraphics g, int c, int r, float x, float y, float beatGlow) {
		// Muros de piedra del santuario
		g.fill(26, 22, 38);
		g.rect(x, y, TILE_SIZE, TILE_SIZE);
		g.fill(38, 32, 54);
		g.rect(x + 2, y + 2, TILE_SIZE - 4, TILE_SIZE - 6);

		// Antorchas pixel-art en las 4 esquinas
		if ((c == 0 || c == GRID_COLS - 1) && (r == 0 || r == GRID_ROWS - 1)) {
			float flicker = 0.65f + 0.35f * beatGlow;
			g.fill(255, 145, 45, (int) (130 * flicker));
			g.ellipse(x + TILE_SIZE * 0.5f, y + TILE_SIZE * 0.5f, 16 * flicker, 16 * flicker);
			g.fill(255, 225, 110);
			g.rect(x + TILE_SIZE * 0.5f - 2, y + TILE_SIZE * 0.5f - 3, 4, 6);
		}
	}

	private void drawFloorTile(PGraphics g, int c, int r, float x, float y, float beatGlow) {
		// Piso disco/ritmo estilo Crypt of the NecroDancer alternando al beat
		boolean checker = ((c + r + _beatCount) % 2 == 0);
		int baseR = checker ? 28 : 19;
		int baseG = checker ? 24 : 18;
		int baseB = checker ? 48 : 34;
		int boost = checker ? Math.round(beatGlow * 20) : Math.round(beatGlow * 6);
		g.fill(baseR + boost, baseG + boost / 2, baseB + boost);
		g.rect(x + 1, y + 1, TILE_SIZE - 2, TILE_SIZE - 2);
	}

	/** Capa 2: Portales de canciones, NPC guia, altares y salida al menu. */
	private void drawStations(PGraphics g, float beatGlow) {
		g.textAlign(PConstants.CENTER, PConstants.CENTER);
		g.textSize(7);

		for (HubStation st : _stations) {
			float x = BOARD_X + st.col * TILE_SIZE;
			float y = BOARD_Y + st.row * TILE_SIZE;
			float cx = tileCenterX(st.col);
			float cy = tileCenterY(st.row);
			boolean standing = (_playerCol == st.col && _playerRow == st.row);
			boolean defeated = (!st.defeatFlag.isEmpty() && GameState.hasFlag(st.defeatFlag));

			// Resplandor pulsante de la casilla
			int alpha = standing ? 185 : Math.round(70 + beatGlow * 65);
			g.noStroke();
			g.fill(st.accentR, st.accentG, st.accentB, alpha);
			g.rect(x + 2, y + 2, TILE_SIZE - 4, TILE_SIZE - 4);

			// Marco brillante
			g.noFill();
			g.stroke(st.accentR, st.accentG, st.accentB, standing ? 255 : 190);
			g.rect(x + 2, y + 2, TILE_SIZE - 4, TILE_SIZE - 4);

			drawStationIcon(g, st, cx, cy, defeated, beatGlow);
		}
	}

	private void drawStationIcon(PGraphics g, HubStation st, float cx, float cy, boolean defeated, float beatGlow) {
		if (st.type == StationType.NPC_DIALOG && _npcCat != null) {
			_npcCat.position.set(cx, cy - 2 - beatGlow * 2.0f);
			_npcCat.draw(g);
		} else if (st.type == StationType.SONG_PORTAL) {
			g.noStroke();
			g.fill(12, 10, 24, 210);
			g.ellipse(cx, cy, 16, 16);
			g.fill(st.accentR, st.accentG, st.accentB, 220);
			g.ellipse(cx, cy, 8 + beatGlow * 4, 8 + beatGlow * 4);
			g.fill(255);
			g.text(defeated ? "OK" : st.shortLabel, cx, cy - 0.5f);
		} else {
			g.fill(245, 245, 255);
			g.text(st.shortLabel, cx, cy - 0.5f);
		}
	}

	/** Capa 3: Sombra dinamica y sprite direccional de la protagonista con arco de salto (hop). */
	private void drawPlayer(PGraphics g, float beatGlow) {
		float hopArc = (_hopTimer < 1.0f) ? (float) Math.sin(_hopTimer * Math.PI) * 9.0f : 0.0f;
		float idleBob = (_hopTimer >= 1.0f) ? beatGlow * 1.8f : 0.0f;

		// Sombra en el suelo
		g.noStroke();
		g.fill(0, 0, 0, 125);
		float shadowW = 14.0f - hopArc * 0.4f;
		g.ellipse(_drawX, _drawY + 7.0f, shadowW, 5.0f);

		Sprite2D active = switch (_facing) {
			case FACING_UP -> _sprUp;
			case FACING_LEFT -> _sprLeft;
			case FACING_RIGHT -> _sprRight;
			default -> _sprDown;
		};

		if (active != null) {
			active.position.set(_drawX, _drawY - 4.0f - hopArc - idleBob);
			active.draw(g);
		} else {
			g.fill(255, 230, 120);
			g.rect(_drawX - 5, _drawY - 10 - hopArc, 10, 12);
		}
	}

	/** Capa 4: Barra superior de estado (ranura SQLite activa, jefes derrotados, chip y estrategia de cura). */
	private void drawTopHud(PGraphics g) {
		g.noStroke();
		g.fill(14, 14, 26, 235);
		g.rect(0, 0, BASE_W, 18);
		g.stroke(65, 85, 130, 160);
		g.line(0, 18, BASE_W, 18);

		g.noStroke();
		g.textSize(7);
		g.textAlign(PConstants.LEFT, PConstants.CENTER);
		g.fill(255, 225, 130);
		g.text(
			"HUB RITMICO  |  RANURA " + (GameState.activeSaveSlot + 1) +
			"  |  JEFES: " + countDefeatedHubBosses() + "/4",
			8, 9
		);

		String chipName = "none".equals(GameState.equippedPowerUp)
			? "Ninguno"
			: GameState.getItemName(GameState.equippedPowerUp);
		g.textAlign(PConstants.RIGHT, PConstants.CENTER);
		g.fill(150, 225, 255);
		g.text(
			"CHIP: " + chipName + "  |  CURA: " + GameState.healStrategy.label,
			BASE_W - 8, 9
		);
	}

	/** Capa 5: Barra inferior con metronomo visual convergente y prompts de interaccion/notificaciones. */
	private void drawBottomBar(PGraphics g, float beatGlow) {
		float barY = BASE_H - 22;
		g.noStroke();
		g.fill(12, 12, 24, 235);
		g.rect(0, barY, BASE_W, 22);
		g.stroke(65, 85, 130, 160);
		g.line(0, barY, BASE_W, barY);

		// Indicador de pulso estilo NecroDancer (dos barras convergiendo al corazon central)
		float cx = BASE_W * 0.5f;
		float cy = barY + 11;
		float dist = (1.0f - _beatPhase) * 95.0f;
		g.noStroke();
		g.fill(110, 215, 255, Math.round(90 + beatGlow * 150));
		g.rect(cx - dist - 2, barY + 4, 3, 14);
		g.rect(cx + dist - 1, barY + 4, 3, 14);

		// Corazon/pulso central
		float r = 5.0f + beatGlow * 3.5f;
		g.fill(245, 70, 105, 235);
		g.ellipse(cx, cy, r * 2.0f, r * 1.7f);

		drawBottomPromptText(g, cy);
	}

	private void drawBottomPromptText(PGraphics g, float cy) {
		HubStation current = getStationAt(_playerCol, _playerRow);
		g.textSize(7);
		g.textAlign(PConstants.LEFT, PConstants.CENTER);
		if (_toastTimer > 0.0f && !_toastMsg.isEmpty()) {
			g.fill(130, 255, 175);
			g.text(_toastMsg, 8, cy);
		} else if (current != null) {
			g.fill(255, 235, 150);
			g.text("[Z/ENTER] " + current.title, 8, cy);
		} else {
			g.fill(175, 195, 225);
			g.text("WASD/Flechas: Saltar  |  Z/Enter: Interactuar  |  ESC: Menu", 8, cy);
		}
	}
}
