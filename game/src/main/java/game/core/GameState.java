package game.core;

import engine.Engine;
import engine.ResourcesManager;
import engine.dialogs.DialogManager;
import game.Global;
import game.core.db.DatabaseManager;
import game.core.db.SaveRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Administrador persistente de estado del juego (State Manager):
 * inventario, ítems de curación, llaves, power-ups, flags de diálogo y transiciones de escena.
 */
public class GameState {

	public enum CampaignMode {
		CLASSIC,
		EXPLORATION
	}

	public enum ItemCategory {
		HEAL,
		KEY,
		POWERUP,
		AMULET,
		MISC
	}

	public enum HealStrategy {
		OPTIMAL("Optimo"),
		MINIMAL("Minimo");

		public final String label;

		HealStrategy(String label) {
			this.label = label;
		}
	}

	public static class ItemDef {
		public final String id;
		public final String name;
		public final ItemCategory category;
		public final int healAmount;
		public final String description;

		public ItemDef(
			String id,
			String name,
			ItemCategory category,
			int healAmount,
			String description
		) {
			this.id = id;
			this.name = name;
			this.category = category;
			this.healAmount = healAmount;
			this.description = description;
		}
	}

	private static final Set<String> _flags = new HashSet<>();
	private static final Map<String, ItemDef> _catalog = new LinkedHashMap<>();
	private static final Map<String, Integer> _inventory = new LinkedHashMap<>();
	private static final Set<String> _equippedPowerUps = new LinkedHashSet<>();

	public static final int SAVE_SLOT_COUNT = 4;
	public static int activeSaveSlot = 0;

	public static class SaveSlotSummary {
		public boolean exists = false;
		public String sceneLabel = "Vacio";
		public int itemCount = 0;
		public int amuletCount = 0;
		public int battlesWonCount = 0;
		public float progressPercent = 0.0f;
		public String generalStatus = "EN_PROGRESO";
		public String lastUpdated = "";
	}

	public static String equippedPowerUp = "none";
	public static HealStrategy healStrategy = HealStrategy.OPTIMAL;

	public static String currentScenePath = "resources/scenes/hub.xml";
	public static String returnScenePath = "resources/scenes/hub.xml";
	public static float returnPlayerX = 7.0f;
	public static float returnPlayerY = 4.0f;

	public static boolean hasPendingSpawn = false;
	public static float pendingSpawnX = 0.0f;
	public static float pendingSpawnY = 0.0f;

	public static boolean launchedFromMenu = false;
	public static boolean isDebugSession = false;
	public static boolean bossInvincibleMode = false;
	public static float songSpeedMultiplier = 1.0f;

	private static String _notificationText = "";
	private static float _notificationTimer = 0.0f;
	private static boolean _initialized = false;

	public static void init() {
		if (_initialized) return;
		_initialized = true;

		ensureCatalogLoaded();
		if (!DatabaseManager.isInitialized()) {
			DatabaseManager.init();
		}
		// Inventario inicial mínimo para poder probar curación y mecánicas en combate
		_inventory.put("potion_small", 2);
		bindDialogHooks();
	}

	public static void ensureCatalogLoaded() {
		if (_catalog.isEmpty()) {
			registerDefaults();
		}
	}

	public static Collection<ItemDef> getCatalogSnapshot() {
		ensureCatalogLoaded();
		return new ArrayList<>(_catalog.values());
	}

	public static void registerItemIfAbsent(ItemDef def) {
		if (def != null && def.id != null && !def.id.isEmpty()) {
			_catalog.putIfAbsent(def.id, def);
		}
	}

	public static void reset() {
		_flags.clear();
		_inventory.clear();
		_equippedPowerUps.clear();
		equippedPowerUp = "none";
		healStrategy = HealStrategy.OPTIMAL;
		currentScenePath = "resources/scenes/beach_ruins.xml";
		returnScenePath = "resources/scenes/beach_ruins.xml";
		returnPlayerX = 1344.0f;
		returnPlayerY = 2464.0f;
		hasPendingSpawn = false;
		launchedFromMenu = false;
		isDebugSession = false;
		bossInvincibleMode = false;
		_notificationText = "";
		_notificationTimer = 0.0f;
		_initialized = false;
		init();
	}

	private static void registerDefaults() {
		_catalog.clear();
		registerItem(new ItemDef(
			"potion_small",
			"Pocion Pequena",
			ItemCategory.HEAL,
			25,
			"Restaura 25 HP"
		));
		registerItem(new ItemDef(
			"potion_medium",
			"Pocion Mediana",
			ItemCategory.HEAL,
			50,
			"Restaura 50 HP"
		));
		registerItem(new ItemDef(
			"potion_large",
			"Pocion Grande",
			ItemCategory.HEAL,
			90,
			"Restaura 90 HP"
		));
		registerItem(new ItemDef(
			"key_exit",
			"Llave Virtual",
			ItemCategory.KEY,
			0,
			"Abre la puerta de salida de la realidad virtual"
		));
		registerItem(new ItemDef(
			"powerup_reaction_plus",
			"Chip +10ms",
			ItemCategory.POWERUP,
			0,
			"+10ms ventana de reaccion, +25 pts coste curacion"
		));
		registerItem(new ItemDef(
			"powerup_reaction_minus",
			"Chip -7.5ms",
			ItemCategory.POWERUP,
			0,
			"-7.5ms ventana de reaccion, -20 pts coste curacion"
		));
		registerItem(new ItemDef(
			"powerup_invincibility",
			"Escudo Unico",
			ItemCategory.POWERUP,
			0,
			"Invencibilidad de un solo uso por pista"
		));
		registerItem(new ItemDef(
			"amulet_healing",
			"Amuleto de Curacion",
			ItemCategory.AMULET,
			0,
			"Aumenta la curacion por notas consecutivas del 50% al 75% del dano recibido"
		));
		registerItem(new ItemDef(
			"amulet_medallion_swamp",
			"Medallon del Pantano",
			ItemCategory.AMULET,
			0,
			"Eco del tercer escondite de La Gata en el Pantano"
		));
		registerItem(new ItemDef(
			"amulet_medallion_ruins",
			"Medallon del Ocaso",
			ItemCategory.AMULET,
			0,
			"Eco del tercer escondite de La Gata en Playas y Ruinas"
		));
		registerItem(new ItemDef(
			"key_copper",
			"Llave de Cobre",
			ItemCategory.KEY,
			0,
			"Abre la puerta de la Sala de Control (Ala Este)"
		));
		registerItem(new ItemDef(
			"key_studio",
			"Llave del Estudio",
			ItemCategory.KEY,
			0,
			"Abre el acceso al Archivo Sur"
		));
		registerItem(new ItemDef(
			"key_crest_a",
			"Sello de Bruma",
			ItemCategory.KEY,
			0,
			"Primer sello de la Puerta del Nucleo"
		));
		registerItem(new ItemDef(
			"key_crest_b",
			"Sello de Marea",
			ItemCategory.KEY,
			0,
			"Segundo sello de la Puerta del Nucleo"
		));
		registerItem(new ItemDef(
			"key_crest_c",
			"Sello de Eco",
			ItemCategory.KEY,
			0,
			"Tercer sello de la Puerta del Nucleo"
		));
	}

	/** Indica si el jugador posee un amuleto de curacion activo en su inventario. */
	public static boolean hasHealingAmulet() {
		return getItemCount("amulet_healing") > 0;
	}

	public static void registerItem(ItemDef def) {
		if (def != null && def.id != null && !def.id.isEmpty()) {
			_catalog.put(def.id, def);
		}
	}

	public static ItemDef getOrRegisterItemDef(String itemId) {
		if (itemId == null || itemId.isEmpty()) return null;
		ItemDef existing = _catalog.get(itemId);
		if (existing != null) return existing;

		ItemCategory cat = ItemCategory.MISC;
		int healAmt = 0;
		String desc = "Objeto obtenido";

		if (itemId.startsWith("heal_") || itemId.startsWith("potion_")) {
			cat = ItemCategory.HEAL;
			healAmt = 30;
			desc = "Restaura 30 HP";
		} else if (itemId.startsWith("key_")) {
			cat = ItemCategory.KEY;
			desc = "Llave especial";
		} else if (itemId.startsWith("powerup_")) {
			cat = ItemCategory.POWERUP;
			desc = "Mejora para combate";
		} else if (itemId.startsWith("amulet_")) {
			cat = ItemCategory.AMULET;
			desc = "Amuleto equipable";
		}

		ItemDef custom = new ItemDef(itemId, itemId, cat, healAmt, desc);
		_catalog.put(itemId, custom);
		return custom;
	}

	public static String getItemName(String itemId) {
		ItemDef def = getOrRegisterItemDef(itemId);
		return (def != null) ? def.name : itemId;
	}

	public static void bindDialogHooks() {
		DialogManager.setOnDefineFlag(flag -> {
			defineFlag(flag);
			autoSaveIfStory();
		});
		DialogManager.setOnCheckFlag(GameState::hasFlag);
		DialogManager.setOnGiveItemRequest((itemId, count) -> {
			addItem(itemId, count);
			autoSaveIfStory();
		});
	}

	// Flags

	public static void defineFlag(String flag) {
		if (flag != null && !flag.isEmpty()) {
			_flags.add(flag);
		}
	}

	public static void clearFlag(String flag) {
		if (flag != null && !flag.isEmpty()) {
			_flags.remove(flag);
			DialogManager.clearLocalFlag(flag);
		}
	}

	public static boolean hasFlag(String flag) {
		if (flag == null || flag.isEmpty()) return false;
		if (flag.startsWith("HAS_ITEM:")) {
			return hasItem(flag.substring("HAS_ITEM:".length()));
		}
		return _flags.contains(flag);
	}

	// Inventario

	public static void addItem(String itemId, int count) {
		if (itemId == null || itemId.isEmpty() || count <= 0) return;
		ItemDef def = getOrRegisterItemDef(itemId);
		int prev = _inventory.getOrDefault(itemId, 0);
		_inventory.put(itemId, prev + count);
		String label = (def != null) ? def.name : itemId;
		showNotification("Obtenido: " + label + " x" + count);
	}

	public static int getItemCount(String itemId) {
		if (itemId == null || itemId.isEmpty()) return 0;
		return _inventory.getOrDefault(itemId, 0);
	}

	public static boolean hasItem(String itemId) {
		return getItemCount(itemId) > 0;
	}

	public static boolean consumeItem(String itemId, int count) {
		if (count <= 0) return true;
		int cur = getItemCount(itemId);
		if (cur < count) return false;
		int next = cur - count;
		if (next == 0) {
			_inventory.remove(itemId);
		} else {
			_inventory.put(itemId, next);
		}
		return true;
	}

	public static List<ItemDef> getOwnedPowerUps() {
		List<ItemDef> list = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : _inventory.entrySet()) {
			if (entry.getValue() <= 0) continue;
			ItemDef def = getOrRegisterItemDef(entry.getKey());
			if (def != null && (def.category == ItemCategory.POWERUP || def.category == ItemCategory.AMULET)) {
				list.add(def);
			}
		}
		return list;
	}

	public static Map<String, Integer> getInventorySnapshot() {
		return new LinkedHashMap<>(_inventory);
	}

	// Power-ups, Amuletos y Curación Dinámica

	public static void equipPowerUp(String powerUpId) {
		_equippedPowerUps.clear();
		if (powerUpId == null || powerUpId.isEmpty() || "none".equals(powerUpId)) {
			equippedPowerUp = "none";
			return;
		}
		if (hasItem(powerUpId)) {
			_equippedPowerUps.add(powerUpId);
			equippedPowerUp = powerUpId;
		} else {
			equippedPowerUp = "none";
		}
	}

	public static void toggleEquippedPowerUp(String powerUpId) {
		if (powerUpId == null || powerUpId.isEmpty() || "none".equals(powerUpId)) return;
		if (!hasItem(powerUpId)) return;

		if (_equippedPowerUps.contains(powerUpId)) {
			_equippedPowerUps.remove(powerUpId);
		} else {
			_equippedPowerUps.add(powerUpId);
		}
		syncPrimaryEquipped();
	}

	private static void syncPrimaryEquipped() {
		if (_equippedPowerUps.isEmpty()) {
			equippedPowerUp = "none";
		} else {
			String last = "none";
			for (String id : _equippedPowerUps) {
				last = id;
			}
			equippedPowerUp = last;
		}
	}

	public static boolean isPowerUpEquipped(String powerUpId) {
		if (powerUpId == null || powerUpId.isEmpty() || "none".equals(powerUpId)) return false;
		if (!hasItem(powerUpId)) return false;
		return _equippedPowerUps.contains(powerUpId) || powerUpId.equals(equippedPowerUp);
	}

	public static List<String> getEquippedPowerUps() {
		List<String> list = new ArrayList<>();
		for (String id : _equippedPowerUps) {
			if (hasItem(id)) list.add(id);
		}
		if (!"none".equals(equippedPowerUp) && hasItem(equippedPowerUp) && !list.contains(equippedPowerUp)) {
			list.add(equippedPowerUp);
		}
		return list;
	}

	public static void toggleHealStrategy() {
		healStrategy = (healStrategy == HealStrategy.OPTIMAL)
			? HealStrategy.MINIMAL
			: HealStrategy.OPTIMAL;
	}

	/**
	 * Asegura la disponibilidad de los chips Power-Up al entrar desde el Selector de Canciones.
	 */
	public static void ensureFreeplayPowerUps() {
		String[] chips = { "powerup_reaction_plus", "powerup_reaction_minus", "powerup_invincibility" };
		for (String id : chips) {
			if (!hasItem(id)) {
				_inventory.put(id, 1);
			}
		}
	}

	/**
	 * Selecciona y consume automáticamente un ítem de curación del inventario según la estrategia:
	 * - MINIMAL: el ítem curativo de menor potencia disponible.
	 * - OPTIMAL: el mayor ítem que no exceda missingHp (o el menor disponible si todos exceden).
	 */
	public static int consumeAutoHeal(int missingHp) {
		if (missingHp <= 0) return 0;

		ItemDef chosen = null;
		for (Map.Entry<String, Integer> entry : _inventory.entrySet()) {
			if (entry.getValue() <= 0) continue;
			ItemDef def = getOrRegisterItemDef(entry.getKey());
			if (def == null || def.category != ItemCategory.HEAL || def.healAmount <= 0) continue;

			if (chosen == null) {
				chosen = def;
				continue;
			}

			if (healStrategy == HealStrategy.MINIMAL) {
				if (def.healAmount < chosen.healAmount) {
					chosen = def;
				}
			} else {
				boolean defFits = def.healAmount <= missingHp;
				boolean chosenFits = chosen.healAmount <= missingHp;

				if (defFits && !chosenFits) {
					chosen = def;
				} else if (defFits && chosenFits) {
					if (def.healAmount > chosen.healAmount) {
						chosen = def;
					}
				} else if (!defFits && !chosenFits) {
					if (def.healAmount < chosen.healAmount) {
						chosen = def;
					}
				}
			}
		}

		if (chosen == null) return 0;
		if (consumeItem(chosen.id, 1)) {
			return chosen.healAmount;
		}
		return 0;
	}

	// Transición de escenas y spawn

	public static void saveReturnLocation(String scenePath, float x, float y) {
		if (scenePath != null && !scenePath.isEmpty()) {
			returnScenePath = scenePath;
		}
		returnPlayerX = x;
		returnPlayerY = y;
	}

	public static void setPendingSpawn(float x, float y) {
		hasPendingSpawn = true;
		pendingSpawnX = x;
		pendingSpawnY = y;
	}

	public static boolean consumePendingSpawn() {
		if (!hasPendingSpawn) return false;
		hasPendingSpawn = false;
		return true;
	}

	// Guardado y Carga Persistente (SQLite como única fuente de verdad)

	public static String toRelativeProjectPath(String path) {
		String rel = DatabaseManager.toRelativeProjectPath(path);
		return rel.isEmpty() ? "resources/scenes/beach_ruins.xml" : rel;
	}

	public static int clampSaveSlot(int slot) {
		return Math.max(0, Math.min(SAVE_SLOT_COUNT - 1, slot));
	}

	public static void setActiveSaveSlot(int slot) {
		activeSaveSlot = clampSaveSlot(slot);
	}

	public static boolean hasSaveFile(int slot) {
		init();
		return SaveRepository.hasSlot(clampSaveSlot(slot));
	}

	public static boolean hasSaveFile() {
		return hasSaveFile(activeSaveSlot);
	}

	public static int countUsedSaveSlots() {
		init();
		return SaveRepository.countUsedSlots();
	}

	public static boolean deleteSaveSlot(int slot) {
		init();
		int s = clampSaveSlot(slot);
		boolean ok = SaveRepository.deleteSlot(s);
		if (s == activeSaveSlot) {
			reset();
		}
		return ok;
	}

	public static SaveSlotSummary getSaveSlotSummary(int slot) {
		init();
		return SaveRepository.getSlotSummary(clampSaveSlot(slot));
	}

	public static void autoSaveIfStory() {
		if (!isDebugSession && !launchedFromMenu) {
			saveGame();
		}
	}

	public static void saveGame() {
		saveGame(currentScenePath, returnPlayerX, returnPlayerY);
	}

	public static void saveGame(String scenePath, float playerX, float playerY) {
		init();
		String relScene = toRelativeProjectPath(
			(scenePath != null && !scenePath.isEmpty()) ? scenePath : currentScenePath
		);
		currentScenePath = relScene;

		Set<String> equippedSet = new LinkedHashSet<>(getEquippedPowerUps());
		SaveRepository.saveState(
			activeSaveSlot,
			relScene,
			playerX,
			playerY,
			toRelativeProjectPath(returnScenePath),
			returnPlayerX,
			returnPlayerY,
			healStrategy,
			_flags,
			_inventory,
			equippedSet
		);
	}

	public static boolean loadGame() {
		return loadGame(activeSaveSlot);
	}

	public static boolean loadGame(int slot) {
		init();
		setActiveSaveSlot(slot);
		SaveRepository.LoadedSaveData data = SaveRepository.loadState(activeSaveSlot);
		if (data == null) return false;

		reset();

		currentScenePath = toRelativeProjectPath(data.scenePath);
		returnScenePath = toRelativeProjectPath(data.returnScenePath);
		returnPlayerX = data.returnX;
		returnPlayerY = data.returnY;
		healStrategy = (data.healStrategy != null) ? data.healStrategy : HealStrategy.OPTIMAL;

		for (String flag : data.flags) {
			defineFlag(flag);
		}

		_inventory.clear();
		for (Map.Entry<String, Integer> entry : data.inventory.entrySet()) {
			if (entry.getValue() != null && entry.getValue() > 0) {
				getOrRegisterItemDef(entry.getKey());
				_inventory.put(entry.getKey(), entry.getValue());
			}
		}

		_equippedPowerUps.clear();
		equippedPowerUp = "none";
		for (String eqId : data.equippedPowerUps) {
			if (eqId != null && !eqId.isEmpty() && !"none".equals(eqId) && hasItem(eqId)) {
				_equippedPowerUps.add(eqId);
			}
		}
		syncPrimaryEquipped();
		setPendingSpawn(data.playerX, data.playerY);
		return true;
	}

	public static void startNewGame() {
		startNewGame(activeSaveSlot, CampaignMode.CLASSIC);
	}

	public static void startNewGame(int slot) {
		startNewGame(slot, CampaignMode.CLASSIC);
	}

	public static void startNewGame(int slot, CampaignMode mode) {
		setActiveSaveSlot(slot);
		reset();
		if (mode == CampaignMode.EXPLORATION) {
			currentScenePath = "resources/scenes/re_mansion.xml";
			returnScenePath = "resources/scenes/re_mansion.xml";
			returnPlayerX = 1824.0f;
			returnPlayerY = 1984.0f;
		} else {
			currentScenePath = "resources/scenes/hub.xml";
			returnScenePath = "resources/scenes/hub.xml";
			returnPlayerX = 256.0f;
			returnPlayerY = 224.0f;
		}
		hasPendingSpawn = false;
		SaveRepository.createNewGame(activeSaveSlot, currentScenePath, returnPlayerX, returnPlayerY, _inventory);
	}

	public static void recordBattleVictory(
		String bossId,
		String bossName,
		String species,
		String defeatFlag,
		String battleScenePath,
		String zoneScenePath,
		String rewardItemId,
		int score,
		int maxCombo,
		int perfects,
		int goods,
		int safes,
		int misses
	) {
		if (launchedFromMenu || isDebugSession) return;
		init();
		SaveRepository.recordBattleVictory(
			activeSaveSlot,
			bossId,
			bossName,
			species,
			defeatFlag,
			battleScenePath,
			zoneScenePath,
			rewardItemId,
			score,
			maxCombo,
			perfects,
			goods,
			safes,
			misses
		);
	}

	// Notificaciones HUD

	public static void showNotification(String msg) {
		if (msg == null || msg.isEmpty()) return;
		_notificationText = msg;
		_notificationTimer = 3.2f;
	}

	public static void updateNotification(float dt) {
		if (_notificationTimer > 0.0f) {
			_notificationTimer -= dt;
			if (_notificationTimer <= 0.0f) {
				_notificationTimer = 0.0f;
				_notificationText = "";
			}
		}
	}

	public static boolean hasNotification() {
		return _notificationTimer > 0.0f && !_notificationText.isEmpty();
	}

	public static String getNotification() {
		return _notificationText;
	}
}
