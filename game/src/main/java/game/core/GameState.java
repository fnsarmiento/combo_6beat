package game.core;

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
 * Administrador central del estado persistente del juego (State Manager).
 * <p>
 * Centraliza el catalogo e inventario de items, chips Power-Up, amuletos desbloqueables,
 * estrategias de curacion automatica, banderas de progreso narrativo/combate, coordenadas
 * de retorno al Hub y sincronizacion con la base de datos SQLite (3FN).
 */
public class GameState {

	// =========================================================================
	// IDENTIFICADORES Y DEFINICIONES DE ITEMS, AMULETOS Y ESTRATEGIAS
	// =========================================================================

	/** Modos de campana soportados al iniciar una nueva partida. */
	public enum CampaignMode {
		CLASSIC,
		EXPLORATION
	}

	/** Categorias funcionales de objetos dentro del catalogo maestro. */
	public enum ItemCategory {
		HEAL,
		KEY,
		POWERUP,
		AMULET,
		MISC
	}

	/** Estrategia de seleccion automatica de consumibles curativos durante el combate ritmico. */
	public enum HealStrategy {
		OPTIMAL("Optimo"),
		MINIMAL("Minimo");

		public final String label;

		HealStrategy(String label) {
			this.label = label;
		}
	}

	/** Definicion inmutable de un objeto, chip Power-Up, llave o amuleto del catalogo. */
	public static class ItemDef {
		public final String id;
		public final String name;
		public final ItemCategory category;
		public final int healAmount;
		public final int shopPrice;
		public final String description;

		public ItemDef(
			String id,
			String name,
			ItemCategory category,
			int healAmount,
			String description
		) {
			this(id, name, category, healAmount, 0, description);
		}

		public ItemDef(
			String id,
			String name,
			ItemCategory category,
			int healAmount,
			int shopPrice,
			String description
		) {
			this.id = id;
			this.name = name;
			this.category = category;
			this.healAmount = healAmount;
			this.shopPrice = shopPrice;
			this.description = description;
		}
	}

	/** Resumen de estado de una ranura de guardado SQLite para su visualizacion en menus y HUD. */
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

	// =========================================================================
	// ESTADO DE CAMPANA, ESCENA ACTIVA Y RANURA ACTIVA SQLITE
	// =========================================================================

	/** Cantidad total de ranuras de guardado disponibles en SQLite. */
	public static final int SAVE_SLOT_COUNT = 4;

	/** Indice (0..3) de la ranura de guardado SQLite actualmente seleccionada. */
	public static int activeSaveSlot = 0;

	/** Puntos acumulados en la partida activa para comprar amuletos en la tienda. */
	public static int shopPoints = 0;

	/** Identificador del chip Power-Up primario equipado ("none" si no hay ninguno). */
	public static String equippedPowerUp = "none";

	/** Estrategia activa de curacion automatica durante los combates ritmicos. */
	public static HealStrategy healStrategy = HealStrategy.OPTIMAL;

	/** Ruta relativa de la escena activa y coordenadas de retorno al Hub. */
	public static String currentScenePath = "resources/scenes/hub.xml";
	public static String returnScenePath = "resources/scenes/hub.xml";
	public static float returnPlayerX = 7.0f;
	public static float returnPlayerY = 4.0f;

	/** Coordenadas de aparicion diferida tras cargar partida o cambiar de sala. */
	public static boolean hasPendingSpawn = false;
	public static float pendingSpawnX = 0.0f;
	public static float pendingSpawnY = 0.0f;

	/** Banderas de sesion para distinguir modo historia, selector libre (arcade) y depuracion. */
	public static boolean launchedFromMenu = false;
	public static boolean isDebugSession = false;
	public static float songSpeedMultiplier = 1.0f;

	/** Colecciones internas de banderas narrativas, catalogo maestro, inventario y equipamiento. */
	private static final Set<String> _flags = new HashSet<>();
	private static final Map<String, ItemDef> _catalog = new LinkedHashMap<>();
	private static final Map<String, Integer> _inventory = new LinkedHashMap<>();
	private static final Set<String> _equippedPowerUps = new LinkedHashSet<>();

	/** Estado interno de notificaciones emergentes y control de inicializacion unica. */
	private static String _notificationText = "";
	private static float _notificationTimer = 0.0f;
	private static boolean _initialized = false;

	/**
	 * Inicializa el catalogo maestro, la conexion SQLite, el inventario base y los hooks de dialogo.
	 */
	public static void init() {
		if (_initialized) return;
		_initialized = true;

		ensureCatalogLoaded();
		if (!DatabaseManager.isInitialized()) {
			DatabaseManager.init();
		}
		// Inventario inicial minimo para disponer de curacion base en los primeros combates
		_inventory.put("potion_small", 2);
	}

	/** Asegura que el catalogo en memoria contenga todas las definiciones base del juego. */
	public static void ensureCatalogLoaded() {
		if (_catalog.isEmpty()) {
			registerDefaults();
		}
	}

	/** Devuelve una copia inmutable de todas las definiciones registradas en el catalogo. */
	public static Collection<ItemDef> getCatalogSnapshot() {
		ensureCatalogLoaded();
		return new ArrayList<>(_catalog.values());
	}

	/** Registra un item en el catalogo solo si su identificador aun no existe. */
	public static void registerItemIfAbsent(ItemDef def) {
		if (def != null && def.id != null && !def.id.isEmpty()) {
			_catalog.putIfAbsent(def.id, def);
		}
	}

	/** Registra o actualiza la definicion de un item en el catalogo en memoria. */
	public static void registerItem(ItemDef def) {
		if (def != null && def.id != null && !def.id.isEmpty()) {
			_catalog.put(def.id, def);
		}
	}

	/**
	 * Obtiene la definicion de un objeto del catalogo o infiere y registra una por defecto
	 * a partir de su prefijo (`heal_`, `potion_`, `key_`, `powerup_`, `amulet_`).
	 */
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

	/** Devuelve el nombre legible de un objeto a partir de su identificador. */
	public static String getItemName(String itemId) {
		ItemDef def = getOrRegisterItemDef(itemId);
		return (def != null) ? def.name : itemId;
	}

	/** Restablece el estado en memoria a los valores predeterminados de una sesion limpia. */
	public static void reset() {
		_flags.clear();
		_inventory.clear();
		_equippedPowerUps.clear();
		shopPoints = 0;
		equippedPowerUp = "none";
		healStrategy = HealStrategy.OPTIMAL;
		currentScenePath = "resources/scenes/beach_ruins.xml";
		returnScenePath = "resources/scenes/beach_ruins.xml";
		returnPlayerX = 1344.0f;
		returnPlayerY = 2464.0f;
		hasPendingSpawn = false;
		launchedFromMenu = false;
		isDebugSession = false;
		_notificationText = "";
		_notificationTimer = 0.0f;
		_initialized = false;
		init();
	}

	/** Pobla el catalogo con los consumibles, chips, amuletos y llaves estandar del juego. */
	private static void registerDefaults() {
		_catalog.clear();
		registerItem(new ItemDef(
			"potion_small",
			"Pocion Pequena",
			ItemCategory.HEAL,
			25,
			0,
			"Restaura 25 HP"
		));
		registerItem(new ItemDef(
			"potion_medium",
			"Pocion Mediana",
			ItemCategory.HEAL,
			50,
			0,
			"Restaura 50 HP"
		));
		registerItem(new ItemDef(
			"potion_large",
			"Pocion Grande",
			ItemCategory.HEAL,
			90,
			0,
			"Restaura 90 HP"
		));
		registerItem(new ItemDef(
			"key_exit",
			"Llave Virtual",
			ItemCategory.KEY,
			0,
			0,
			"Abre la puerta de salida de la realidad virtual"
		));
		registerItem(new ItemDef(
			"amulet_dual_slot",
			"Relicario Gemelo",
			ItemCategory.AMULET,
			0,
			1800,
			"Te permite equipar 2 amuletos adicionales al mismo tiempo."
		));
		registerItem(new ItemDef(
			"powerup_reaction_plus",
			"Amuleto de Calma (+10ms)",
			ItemCategory.AMULET,
			0,
			1200,
			"Amplia +10ms las ventanas de acierto PERFECT, GOOD y SAFE."
		));
		registerItem(new ItemDef(
			"powerup_reaction_minus",
			"Amuleto de Filo (-7.5ms)",
			ItemCategory.AMULET,
			0,
			1500,
			"Reduce -7.5ms las ventanas de juicio a cambio de +25% de puntaje y puntos."
		));
		registerItem(new ItemDef(
			"amulet_healing",
			"Amuleto de Curacion",
			ItemCategory.AMULET,
			0,
			2200,
			"Aumenta la curacion cada 2 aciertos del 50% al 75% del dano recibido."
		));
		registerItem(new ItemDef(
			"powerup_invincibility",
			"Amuleto de Escudo Unico",
			ItemCategory.AMULET,
			0,
			2800,
			"Evita un golpe mortal por cancion otorgando 4s de invencibilidad."
		));
		registerItem(new ItemDef(
			"amulet_medallion_swamp",
			"Medallon de Vitalidad",
			ItemCategory.AMULET,
			0,
			3400,
			"Aumenta tu vida maxima de 200 a 260 HP en cada cancion."
		));
		registerItem(new ItemDef(
			"amulet_medallion_ruins",
			"Medallon del Compas",
			ItemCategory.AMULET,
			0,
			4200,
			"El multiplicador sube cada 5 notas GOOD/PERFECT en vez de cada 7."
		));
		registerItem(new ItemDef(
			"amulet_overdrive_32x",
			"Amuleto Overdrive (x32)",
			ItemCategory.AMULET,
			0,
			3800,
			"Eleva el multiplicador maximo a x32, pero requiere 21 notas GOOD/PERFECT para subir cada nivel."
		));
		registerItem(new ItemDef(
			"key_copper",
			"Llave de Cobre",
			ItemCategory.KEY,
			0,
			0,
			"Abre la puerta de la Sala de Control (Ala Este)"
		));
		registerItem(new ItemDef(
			"key_studio",
			"Llave del Estudio",
			ItemCategory.KEY,
			0,
			0,
			"Abre el acceso al Archivo Sur"
		));
		registerItem(new ItemDef(
			"key_crest_a",
			"Sello de Bruma",
			ItemCategory.KEY,
			0,
			0,
			"Primer sello de la Puerta del Nucleo"
		));
		registerItem(new ItemDef(
			"key_crest_b",
			"Sello de Marea",
			ItemCategory.KEY,
			0,
			0,
			"Segundo sello de la Puerta del Nucleo"
		));
		registerItem(new ItemDef(
			"key_crest_c",
			"Sello de Eco",
			ItemCategory.KEY,
			0,
			0,
			"Tercer sello de la Puerta del Nucleo"
		));
	}

	/** Devuelve la lista de amuletos del catalogo disponibles en la tienda (`shopPrice > 0`), ordenada por precio. */
	public static List<ItemDef> getShopAmulets() {
		ensureCatalogLoaded();
		List<ItemDef> list = new ArrayList<>();
		for (ItemDef def : _catalog.values()) {
			if (def != null && def.shopPrice > 0) {
				list.add(def);
			}
		}
		list.sort((a, b) -> {
			int cmp = Integer.compare(a.shopPrice, b.shopPrice);
			return (cmp != 0) ? cmp : a.name.compareToIgnoreCase(b.name);
		});
		return list;
	}

	/** Devuelve la cantidad actual de puntos de tienda acumulados en la partida. */
	public static int getShopPoints() {
		return shopPoints;
	}

	/** Define directamente la cantidad de puntos de tienda (no negativa). */
	public static void setShopPoints(int pts) {
		shopPoints = Math.max(0, pts);
	}

	/** Suma una cantidad positiva de puntos al saldo de la partida. */
	public static void addShopPoints(int delta) {
		if (delta > 0) {
			shopPoints += delta;
		}
	}

	/** Intenta gastar `cost` puntos de tienda; devuelve `true` si habia saldo suficiente. */
	public static boolean spendShopPoints(int cost) {
		if (cost < 0 || shopPoints < cost) return false;
		shopPoints -= cost;
		return true;
	}

	/**
	 * Compra un amuleto de la tienda descontando su precio en puntos, agregandolo al inventario
	 * y persistiendo inmediatamente el estado de la partida.
	 */
	public static boolean buyShopAmulet(String itemId) {
		ensureCatalogLoaded();
		ItemDef def = getOrRegisterItemDef(itemId);
		if (def == null || def.shopPrice <= 0 || hasItem(itemId)) return false;
		if (!spendShopPoints(def.shopPrice)) return false;
		addItem(itemId, 1);
		saveGame();
		return true;
	}

	/**
	 * Calcula la cantidad entera de puntos (`1 .. 10000`) ganados al finalizar una cancion.
	 * <p>
	 * El Full Combo (0 MISS) garantiza la gran mayoria del potencial de puntos de la pista
	 * (entre el 80% y el 100%), mientras que sin Full Combo la recompensa se limita a ~60% maximo.
	 */
	public static int computeSongRewardPoints(
		float difficultyRating,
		int totalNotes,
		int perfectCount,
		int goodCount,
		int safeCount,
		int missCount,
		int maxCombo,
		float accuracyPercent,
		boolean won
	) {
		float normDiff = Math.max(0.10f, Math.min(1.0f, difficultyRating / 11.5f));
		float maxPotential = 650.0f + 9350.0f * (float) Math.pow(normDiff, 1.20);

		if (!won) {
			return Math.max(
				1,
				Math.min(300, Math.round(maxPotential * (Math.max(0.0f, accuracyPercent) / 100.0f) * 0.08f))
			);
		}

		int notesHit = perfectCount + goodCount + safeCount;
		int effTotal = Math.max(1, Math.max(totalNotes, notesHit + missCount));
		boolean fullCombo = (missCount == 0 && notesHit > 0);
		float accRatio = Math.max(0.0f, Math.min(1.0f, accuracyPercent / 100.0f));
		float comboRatio = Math.max(0.0f, Math.min(1.0f, (float) maxCombo / effTotal));
		float perfectRatio = Math.max(0.0f, Math.min(1.0f, (float) perfectCount / effTotal));

		float factor;
		if (fullCombo) {
			factor = 0.55f + 0.25f * accRatio + 0.20f * perfectRatio;
		} else {
			factor = 0.28f * accRatio + 0.22f * comboRatio + 0.10f * perfectRatio;
		}

		if (isPowerUpEquipped("powerup_reaction_minus")) {
			factor *= 1.25f;
		}

		return Math.max(1, Math.min(10000, Math.round(maxPotential * factor)));
	}

	// =========================================================================
	// INVENTARIO DE CHIPS, AMULETOS EQUIPADOS Y CURACION DINAMICA
	// =========================================================================

	/** Agrega `count` unidades de un objeto al inventario y dispara una notificacion en el HUD. */
	public static void addItem(String itemId, int count) {
		if (itemId == null || itemId.isEmpty() || count <= 0) return;
		ItemDef def = getOrRegisterItemDef(itemId);
		int prev = _inventory.getOrDefault(itemId, 0);
		_inventory.put(itemId, prev + count);
		String label = (def != null) ? def.name : itemId;
		showNotification("Obtenido: " + label + " x" + count);
	}

	/** Devuelve la cantidad disponible de un objeto en el inventario actual. */
	public static int getItemCount(String itemId) {
		if (itemId == null || itemId.isEmpty()) return 0;
		return _inventory.getOrDefault(itemId, 0);
	}

	/** Comprueba si el jugador posee al menos una unidad del objeto indicado. */
	public static boolean hasItem(String itemId) {
		return getItemCount(itemId) > 0;
	}

	/** Consume `count` unidades del objeto indicado si hay stock suficiente. */
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

	/** Indica si el jugador tiene equipado el Amuleto de Curacion. */
	public static boolean hasHealingAmulet() {
		return isPowerUpEquipped("amulet_healing");
	}

	/** Lista todos los chips Power-Up y amuletos actualmente en posesion del jugador. */
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

	/** Devuelve una copia del mapa de inventario (`id_objeto -> cantidad`). */
	public static Map<String, Integer> getInventorySnapshot() {
		return new LinkedHashMap<>(_inventory);
	}

	/** Devuelve el maximo de amuletos simultaneos permitidos (3 con Relicario Gemelo equipado, 1 base). */
	public static int getMaxEquippedAmulets() {
		return _equippedPowerUps.contains("amulet_dual_slot") ? 3 : 1;
	}

	/** Equipa un unico chip Power-Up como primario (o limpia el equipamiento si es `"none"`). */
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

	/**
	 * Alterna el estado equipado/desequipado de un amuleto respetando la capacidad maxima:
	 * 1 ranura por defecto, o hasta 3 ranuras (el Relicario Gemelo + 2 amuletos adicionales)
	 * cuando `"amulet_dual_slot"` esta equipado.
	 */
	public static void toggleEquippedPowerUp(String powerUpId) {
		if (powerUpId == null || powerUpId.isEmpty() || "none".equals(powerUpId)) return;
		if (!hasItem(powerUpId)) return;

		if (_equippedPowerUps.contains(powerUpId)) {
			_equippedPowerUps.remove(powerUpId);
			if ("amulet_dual_slot".equals(powerUpId) && _equippedPowerUps.size() > 1) {
				String keep = null;
				for (String id : _equippedPowerUps) {
					keep = id;
					break;
				}
				_equippedPowerUps.clear();
				if (keep != null) {
					_equippedPowerUps.add(keep);
				}
			}
		} else {
			if ("amulet_dual_slot".equals(powerUpId)) {
				_equippedPowerUps.add(powerUpId);
			} else if (_equippedPowerUps.contains("amulet_dual_slot")) {
				if (_equippedPowerUps.size() >= 3) {
					String toRemove = null;
					for (String id : _equippedPowerUps) {
						if (!"amulet_dual_slot".equals(id)) {
							toRemove = id;
						}
					}
					if (toRemove != null) {
						_equippedPowerUps.remove(toRemove);
					}
				}
				_equippedPowerUps.add(powerUpId);
			} else {
				_equippedPowerUps.clear();
				_equippedPowerUps.add(powerUpId);
			}
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

	/** Verifica si el chip Power-Up o amuleto indicado se encuentra equipado y disponible. */
	public static boolean isPowerUpEquipped(String powerUpId) {
		if (powerUpId == null || powerUpId.isEmpty() || "none".equals(powerUpId)) return false;
		if (!hasItem(powerUpId)) return false;
		return _equippedPowerUps.contains(powerUpId) || powerUpId.equals(equippedPowerUp);
	}

	/** Devuelve la lista ordenada de identificadores de chips/amuletos actualmente equipados. */
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

	/** Alterna la estrategia de curacion automatica entre OPTIMAL y MINIMAL. */
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
	 * Selecciona y consume automaticamente un item de curacion del inventario segun la estrategia:
	 * - MINIMAL: el item curativo de menor potencia disponible.
	 * - OPTIMAL: el mayor item que no exceda missingHp (o el menor disponible si todos exceden).
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

	// =========================================================================
	// PROGRESO DE BATALLAS, HISTORIA, FLAGS Y TRANSICIONES
	// =========================================================================

	/** Activa una bandera de evento narrativo o de jefe derrotado. */
	public static void defineFlag(String flag) {
		if (flag != null && !flag.isEmpty()) {
			_flags.add(flag);
		}
	}

	/** Elimina una bandera de estado global. */
	public static void clearFlag(String flag) {
		if (flag != null && !flag.isEmpty()) {
			_flags.remove(flag);
		}
	}

	/**
	 * Evalua si una bandera esta activa. Soporta el prefijo especial `HAS_ITEM:<id>`
	 * para condicionales de dialogo basados en inventario.
	 */
	public static boolean hasFlag(String flag) {
		if (flag == null || flag.isEmpty()) return false;
		if (flag.startsWith("HAS_ITEM:")) {
			return hasItem(flag.substring("HAS_ITEM:".length()));
		}
		return _flags.contains(flag);
	}

	/** Registra la escena y coordenadas de retorno antes de iniciar un combate ritmico. */
	public static void saveReturnLocation(String scenePath, float x, float y) {
		if (scenePath != null && !scenePath.isEmpty()) {
			returnScenePath = scenePath;
		}
		returnPlayerX = x;
		returnPlayerY = y;
	}

	/** Define una posicion de aparicion pendiente para la siguiente carga de escena. */
	public static void setPendingSpawn(float x, float y) {
		hasPendingSpawn = true;
		pendingSpawnX = x;
		pendingSpawnY = y;
	}

	/** Consume y limpia la marca de aparicion pendiente si estaba activa. */
	public static boolean consumePendingSpawn() {
		if (!hasPendingSpawn) return false;
		hasPendingSpawn = false;
		return true;
	}

	/**
	 * Registra en SQLite (`BATALLAS_VENCIDAS`) la victoria sobre un jefe o pista de historia,
	 * disparando los triggers de recompensas y amuletos desbloqueables por puntaje.
	 */
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

	// =========================================================================
	// PERSISTENCIA SQLITE (GUARDADO, CARGA Y REINICIO DE PROGRESO)
	// =========================================================================

	/** Normaliza una ruta de escena a formato relativo dentro del proyecto. */
	public static String toRelativeProjectPath(String path) {
		String rel = DatabaseManager.toRelativeProjectPath(path);
		return rel.isEmpty() ? "resources/scenes/beach_ruins.xml" : rel;
	}

	/** Restringe un indice de ranura al rango valido `0 .. SAVE_SLOT_COUNT - 1`. */
	public static int clampSaveSlot(int slot) {
		return Math.max(0, Math.min(SAVE_SLOT_COUNT - 1, slot));
	}

	/** Establece la ranura de guardado SQLite activa. */
	public static void setActiveSaveSlot(int slot) {
		activeSaveSlot = clampSaveSlot(slot);
	}

	/** Indica si existe una partida guardada en la ranura especificada. */
	public static boolean hasSaveFile(int slot) {
		init();
		return SaveRepository.hasSlot(clampSaveSlot(slot));
	}

	/** Indica si existe una partida guardada en la ranura activa actual. */
	public static boolean hasSaveFile() {
		return hasSaveFile(activeSaveSlot);
	}

	/** Cuenta cuantas ranuras de guardado estan ocupadas en SQLite. */
	public static int countUsedSaveSlots() {
		init();
		return SaveRepository.countUsedSlots();
	}

	/** Elimina los datos de la ranura indicada en SQLite y reinicia el estado si era la activa. */
	public static boolean deleteSaveSlot(int slot) {
		init();
		int s = clampSaveSlot(slot);
		boolean ok = SaveRepository.deleteSlot(s);
		if (s == activeSaveSlot) {
			reset();
		}
		return ok;
	}

	/** Obtiene el resumen estadistico de una ranura de guardado desde SQLite. */
	public static SaveSlotSummary getSaveSlotSummary(int slot) {
		init();
		return SaveRepository.getSlotSummary(clampSaveSlot(slot));
	}

	/** Obtiene los puntos de tienda almacenados en la ranura de guardado indicada en SQLite. */
	public static int getSaveSlotPoints(int slot) {
		init();
		SaveRepository.LoadedSaveData d = SaveRepository.loadState(clampSaveSlot(slot));
		if (d == null || d.flags == null) return 0;
		for (String flag : d.flags) {
			if (flag != null && flag.startsWith("SHOP_POINTS:")) {
				try {
					return Math.max(0, Integer.parseInt(flag.substring("SHOP_POINTS:".length()).trim()));
				} catch (NumberFormatException ignored) {
					return 0;
				}
			}
		}
		return 0;
	}

	/** Ejecuta un autoguardado en SQLite solo si la sesion actual pertenece al modo historia. */
	public static void autoSaveIfStory() {
		if (!isDebugSession && !launchedFromMenu) {
			saveGame();
		}
	}

	/** Guarda el estado actual en la ranura activa de SQLite usando la escena y posicion en curso. */
	public static void saveGame() {
		saveGame(currentScenePath, returnPlayerX, returnPlayerY);
	}

	/** Persiste el estado completo de la partida en la ranura activa de la base de datos SQLite. */
	public static void saveGame(String scenePath, float playerX, float playerY) {
		init();
		String relScene = toRelativeProjectPath(
			(scenePath != null && !scenePath.isEmpty()) ? scenePath : currentScenePath
		);
		currentScenePath = relScene;

		_flags.removeIf(f -> f != null && f.startsWith("SHOP_POINTS:"));
		_flags.add("SHOP_POINTS:" + shopPoints);

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

	/** Carga en memoria la partida almacenada en la ranura activa de SQLite. */
	public static boolean loadGame() {
		return loadGame(activeSaveSlot);
	}

	/** Carga en memoria la partida almacenada en la ranura `slot` desde SQLite. */
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

		shopPoints = 0;
		for (String flag : data.flags) {
			if (flag != null && flag.startsWith("SHOP_POINTS:")) {
				try {
					shopPoints = Math.max(0, Integer.parseInt(flag.substring("SHOP_POINTS:".length()).trim()));
				} catch (NumberFormatException ignored) {}
			}
			defineFlag(flag);
		}
		_flags.removeIf(f -> f != null && f.startsWith("SHOP_POINTS:"));
		_flags.add("SHOP_POINTS:" + shopPoints);

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

	/** Inicia una nueva partida en la ranura activa con el modo de campana clasico (Hub). */
	public static void startNewGame() {
		startNewGame(activeSaveSlot, CampaignMode.CLASSIC);
	}

	/** Inicia una nueva partida en la ranura indicada con el modo de campana clasico (Hub). */
	public static void startNewGame(int slot) {
		startNewGame(slot, CampaignMode.CLASSIC);
	}

	/** Reinicia el progreso e inicializa un nuevo registro de partida en SQLite para la ranura dada. */
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

	// =========================================================================
	// NOTIFICACIONES DE HUD
	// =========================================================================

	/** Muestra un mensaje temporal de notificacion en el HUD (ej. al recibir un objeto). */
	public static void showNotification(String msg) {
		if (msg == null || msg.isEmpty()) return;
		_notificationText = msg;
		_notificationTimer = 3.2f;
	}

	/** Actualiza el temporizador de la notificacion activa del HUD. */
	public static void updateNotification(float dt) {
		if (_notificationTimer > 0.0f) {
			_notificationTimer -= dt;
			if (_notificationTimer <= 0.0f) {
				_notificationTimer = 0.0f;
				_notificationText = "";
			}
		}
	}

	/** Indica si hay una notificacion emergente activa pendiente de mostrarse. */
	public static boolean hasNotification() {
		return _notificationTimer > 0.0f && !_notificationText.isEmpty();
	}

	/** Devuelve el texto de la notificacion activa. */
	public static String getNotification() {
		return _notificationText;
	}
}
