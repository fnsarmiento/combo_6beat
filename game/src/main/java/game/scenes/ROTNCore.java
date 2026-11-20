package game.scenes;

import engine.KBInput;
import game.Config;
import game.core.GameState;
import game.core.notes.NotePacker;

/**
 * Núcleo lógico del combate rítmico de 6 carriles (inspirado en Rift of the NecroDancer).
 * Gestiona las ventanas de juicio, las notas simples y mantenidas (Hold Notes),
 * la salud del jugador y del jefe, la curación progresiva y los bloqueos temporales de carril.
 */
public class ROTNCore {

	// =========================================================================
	// 1. Constantes de juicio, curación y bloqueo de carriles
	// =========================================================================

	private static final int LANE_COUNT = 6;

	// Límites inferiores de seguridad para las ventanas de acierto (en milisegundos)
	private static final float MIN_PERFECT_WINDOW_MS = 8.0f;
	private static final float MIN_GOOD_WINDOW_MS = 15.0f;
	private static final float MIN_SAFE_WINDOW_MS = 25.0f;
	private static final float CAPTURE_WINDOW_MARGIN_MS = 25.0f;

	// Modificadores de ventana por amuletos/power-ups equipados
	private static final float POWERUP_REACTION_PLUS_MS = 10.0f;
	private static final float POWERUP_REACTION_MINUS_MS = -7.5f;

	// Puntuación base y multiplicadores de daño al jefe según precisión
	private static final int SCORE_PERFECT_BASE = 300;
	private static final int SCORE_PERFECT_COMBO_MULT = 10;
	private static final int SCORE_GOOD_BASE = 200;
	private static final int SCORE_GOOD_COMBO_MULT = 5;
	private static final int SCORE_SAFE_BASE = 100;
	private static final float BOSS_DMG_PERFECT_MULT = 1.5f;
	private static final float BOSS_DMG_SAFE_MULT = 0.6f;
	private static final float BOSS_FLASH_DURATION_SEC = 0.14f;

	// Curación progresiva por racha corta y barra de autocuración
	private static final int CONSECUTIVE_HITS_FOR_HEAL = 2;
	private static final float BASE_DAMAGE_RECOVERY_RATIO = 0.50f;
	private static final float AMULET_DAMAGE_RECOVERY_RATIO = 0.75f;
	private static final float AUTO_HEAL_PULSE_INTERVAL_SEC = 0.9f;
	private static final int DEFAULT_AUTO_HEAL_AMOUNT = 4;

	// Escudo de invencibilidad de emergencia
	private static final float INVINCIBILITY_SHIELD_DURATION_SEC = 4.0f;
	private static final int INVINCIBILITY_SHIELD_MIN_HP = 15;

	// Bloqueo temporal de carriles por ataques del jefe
	private static final int[] LANE_LOCK_CYCLE_ORDER = { 1, 4, 0, 3, 5, 2 };
	private static final float LOCK_WARNING_LEAD_SEC = 1.4f;
	private static final int LOCK_WARNING_LEAD_MS = 1400;
	private static final long LOCK_END_OF_SONG_MARGIN_MS = 1500L;
	private static final int LOCKED_LANE_PRESS_DAMAGE = 2;

	// Margen posterior a la última nota para declarar victoria por supervivencia
	private static final long VICTORY_OUTRO_GRACE_MS = 2000L;

	/**
	 * Categorías de juicio al golpear o soltar una nota.
	 * Cada categoría define su ventana base en milisegundos alrededor del tiempo exacto.
	 */
	public enum HitRating {
		PERFECT(45.0f, 5, "PERFECT +5"),
		GOOD(85.0f, 3, "GOOD +3"),
		SAFE(125.0f, 1, "SAFE +1"),
		MISS(170.0f, -2, "MISS -2");

		public final float baseWindowMs;
		public final int healPointsDelta;
		public final String displayLabel;

		HitRating(float baseWindowMs, int healPointsDelta, String displayLabel) {
			this.baseWindowMs = baseWindowMs;
			this.healPointsDelta = healPointsDelta;
			this.displayLabel = displayLabel;
		}
	}

	/** Estado general del encuentro rítmico. */
	public enum BattleState {
		PLAYING,
		VICTORY,
		DEFEAT
	}

	/** Callback invocado cada vez que se registra un juicio en un carril. */
	public interface OnHitListener {
		void onHit(int lane, HitRating rating, int combo, String detail);
	}

	// =========================================================================
	// 2. Estado interno de notas, carriles, salud y estadísticas
	// =========================================================================

	// Notas empaquetadas (NotePacker) y estado de retención (Hold Notes) por carril
	private long[] notes;
	private boolean[] processedNotes;
	private boolean[] missedNotes;
	private final boolean[] isHoldingLane = new boolean[LANE_COUNT];
	private final int[] holdingNoteIndex = new int[LANE_COUNT];
	private long firstNoteStartTimeMs = Long.MAX_VALUE;
	private long lastNoteEndTimeMs = 0;

	// Estado de la partida y estadísticas de combo/precisión
	private boolean isPaused;
	private BattleState battleState = BattleState.PLAYING;
	private int combo, maxCombo, score;
	private int perfectCount, greatCount, goodCount, missCount;
	private OnHitListener hitListener;

	// Salud del jugador y del jefe
	private int playerMaxHp = 200;
	private int playerHp = 200;
	private int lastDamageTaken = 0;
	private int bossMaxHp = 1500;
	private int bossHp = 1500;
	private boolean bossInvincible = false;
	private int bossAttackDamage = 12;
	private int bossHitDamage = 6;
	private float bossFlashTimer = 0.0f;

	// Curación progresiva, modificadores de ventana y escudo de invencibilidad
	private float windowOffsetMs = 0.0f;
	private int healPoints = 0;
	private boolean healBarActive = false;
	private float healActiveDuration = 6.0f;
	private float healBarTimer = 0.0f;
	private float healPulseTimer = 0.0f;
	private boolean hasInvincibilityShield = false;
	private float invincibilityTimer = 0.0f;

	// Estado del ataque de bloqueo temporal de carril: intervalo [lockStartMs, lockEndMs]
	private int laneLockIntervalMs = 10000;
	private int laneLockDurationMs = 2800;
	private int nextLockTriggerMs = 10000;
	private int warningLane = -1;
	private float lockWarningTimer = 0.0f;
	private int lockedLane = -1;
	private float lockedLaneTimer = 0.0f;
	private int lockStartMs = -1;
	private int lockEndMs = -1;
	private int lockCycleCounter = 0;

	// =========================================================================
	// 3. Inicialización y configuración de dificultad/canción
	// =========================================================================

	public ROTNCore(long[] notes) {
		setNotes(notes);
		applyPowerUpModifiers();
	}

	/**
	 * Configura los parámetros de combate del encuentro (salud, daño, curación y frecuencia de bloqueo).
	 */
	public void configureCombat(
		int playerMaxHp,
		int bossMaxHp,
		int bossAttackDamage,
		int bossHitDamage,
		int healThreshold,
		float healActiveDuration,
		int laneLockIntervalMs,
		int laneLockDurationMs
	) {
		this.playerMaxHp = Math.max(10, playerMaxHp);
		this.playerHp = this.playerMaxHp;
		this.bossMaxHp = Math.max(10, bossMaxHp);
		this.bossHp = this.bossMaxHp;
		this.bossAttackDamage = Math.max(1, bossAttackDamage);
		this.bossHitDamage = Math.max(1, bossHitDamage);
		this.healActiveDuration = Math.max(1.0f, healActiveDuration);
		this.laneLockIntervalMs = laneLockIntervalMs;
		this.laneLockDurationMs = Math.max(500, laneLockDurationMs);
		this.nextLockTriggerMs = (laneLockIntervalMs > 0) ? laneLockIntervalMs : Integer.MAX_VALUE;
		applyPowerUpModifiers();
	}

	/**
	 * Evalúa los amuletos equipados en {@link GameState} para ajustar las ventanas de juicio
	 * y habilitar el escudo de invencibilidad si corresponde.
	 */
	public void applyPowerUpModifiers() {
		windowOffsetMs = 0.0f;
		hasInvincibilityShield = false;
		invincibilityTimer = 0.0f;

		if (GameState.isPowerUpEquipped("powerup_reaction_plus")) {
			windowOffsetMs += POWERUP_REACTION_PLUS_MS;
		}
		if (GameState.isPowerUpEquipped("powerup_reaction_minus")) {
			windowOffsetMs += POWERUP_REACTION_MINUS_MS;
		}
		if (GameState.isPowerUpEquipped("powerup_invincibility")) {
			hasInvincibilityShield = true;
		}
	}

	/**
	 * Carga el arreglo de notas empaquetadas del beatmap, calcula los límites temporales
	 * de la canción y reinicia todas las estadísticas del combate.
	 */
	public void setNotes(long[] notes) {
		this.notes = notes;
		int count = (notes != null) ? notes.length : 0;
		this.processedNotes = new boolean[count];
		this.missedNotes = new boolean[count];
		this.firstNoteStartTimeMs = Long.MAX_VALUE;
		this.lastNoteEndTimeMs = 0;

		if (notes != null) {
			for (long packed : notes) {
				long start = NotePacker.getStartTime(packed);
				long end = start + NotePacker.getDuration(packed);
				if (start < firstNoteStartTimeMs) firstNoteStartTimeMs = start;
				if (end > lastNoteEndTimeMs) lastNoteEndTimeMs = end;
			}
		}

		for (int i = 0; i < LANE_COUNT; i++) {
			holdingNoteIndex[i] = -1;
			isHoldingLane[i] = false;
		}
		resetStats();
	}

	/**
	 * Restablece contadores de combo, puntaje, vida, curación y bloqueos de carril al estado inicial.
	 */
	public void resetStats() {
		combo = maxCombo = score = 0;
		perfectCount = greatCount = goodCount = missCount = 0;
		isPaused = false;
		battleState = BattleState.PLAYING;
		playerHp = playerMaxHp;
		lastDamageTaken = 0;
		bossHp = bossMaxHp;
		healPoints = 0;
		healBarActive = false;
		healBarTimer = 0.0f;
		healPulseTimer = 0.0f;
		bossFlashTimer = 0.0f;
		warningLane = -1;
		lockWarningTimer = 0.0f;
		lockedLane = -1;
		lockedLaneTimer = 0.0f;
		lockStartMs = -1;
		lockEndMs = -1;
		lockCycleCounter = 0;
		nextLockTriggerMs = (laneLockIntervalMs > 0) ? laneLockIntervalMs : Integer.MAX_VALUE;
		applyPowerUpModifiers();
	}

	// =========================================================================
	// 4. Bucle principal de actualización (update), procesamiento de notas
	//    expiradas/holds activos y bloqueos de carril ([lockStartMs, lockEndMs])
	// =========================================================================

	/**
	 * Avanza el estado del combate rítmico para el instante {@code songTimeMs} de la canción.
	 */
	public void update(int songTimeMs, float dt) {
		if (battleState != BattleState.PLAYING) return;

		if (
			KBInput.wasKeyJustPressed(Config.UIButtons[Config.CANCEL]) ||
			KBInput.wasKeyJustPressed(Config.UIAltButtons[Config.CANCEL])
		) {
			togglePause();
		}

		if (isPaused || notes == null) return;

		if (bossFlashTimer > 0.0f) bossFlashTimer = Math.max(0.0f, bossFlashTimer - dt);
		if (invincibilityTimer > 0.0f) invincibilityTimer = Math.max(0.0f, invincibilityTimer - dt);

		updateHealBar(dt);
		updateLaneLockAttack(songTimeMs, dt);
		processInputAndNotes(songTimeMs);
		checkVictoryOrDefeat(songTimeMs);
	}

	public void update(int songTimeMs) {
		update(songTimeMs, 1.0f / 60.0f);
	}

	/**
	 * Actualiza la barra de curación periódica cuando se encuentra activa.
	 */
	private void updateHealBar(float dt) {
		if (!healBarActive) return;

		healBarTimer -= dt;
		healPulseTimer -= dt;

		if (healPulseTimer <= 0.0f && playerHp < playerMaxHp) {
			int missing = playerMaxHp - playerHp;
			int itemHeal = GameState.consumeAutoHeal(missing);
			int healed = (itemHeal > 0) ? itemHeal : DEFAULT_AUTO_HEAL_AMOUNT;
			playerHp = Math.min(playerMaxHp, playerHp + healed);
			healPulseTimer = AUTO_HEAL_PULSE_INTERVAL_SEC;
		}

		if (healBarTimer <= 0.0f) {
			healBarActive = false;
			healBarTimer = 0.0f;
		}
	}

	/**
	 * Gestiona el ciclo de bloqueo temporal de carriles en el intervalo {@code [lockStartMs, lockEndMs]}.
	 * <p>
	 * Funcionamiento del bloqueo:
	 * <ol>
	 *   <li><b>Fase de aviso ({@code warningLane}):</b> se programa {@code lockStartMs = songTimeMs + 1400ms}
	 *       y {@code lockEndMs = lockStartMs + laneLockDurationMs}. Durante esos 1.4s previos, la escena
	 *       dibuja un bloque rojo continuo descendiendo por la pista hacia la línea de impacto.</li>
	 *   <li><b>Fase activa ({@code lockedLane}):</b> cuando {@code songTimeMs >= lockStartMs}, el carril queda
	 *       bloqueado hasta {@code lockEndMs}. Si había un Hold activo en ese carril, se libera sin penalizar,
	 *       y cualquier nota que inicie dentro del carril bloqueado se descarta automáticamente.</li>
	 * </ol>
	 */
	private void updateLaneLockAttack(int songTimeMs, float dt) {
		if (laneLockIntervalMs <= 0 || notes == null || notes.length == 0 || songTimeMs < firstNoteStartTimeMs) {
			lockedLane = -1;
			warningLane = -1;
			lockStartMs = -1;
			lockEndMs = -1;
			return;
		}

		// 1. Si hay un carril actualmente bloqueado, verificar si concluyó el intervalo [lockStartMs, lockEndMs]
		if (lockedLane != -1) {
			lockedLaneTimer -= dt;
			if (lockedLaneTimer <= 0.0f || (lockEndMs > 0 && songTimeMs >= lockEndMs)) {
				lockedLane = -1;
				lockedLaneTimer = 0.0f;
				lockStartMs = -1;
				lockEndMs = -1;
				nextLockTriggerMs = songTimeMs + laneLockIntervalMs;
			}
			return;
		}

		// 2. Si hay un carril en fase de advertencia, activar el bloqueo al alcanzar lockStartMs
		if (warningLane != -1) {
			lockWarningTimer -= dt;
			if (lockWarningTimer <= 0.0f || (lockStartMs > 0 && songTimeMs >= lockStartMs)) {
				lockedLane = warningLane;
				warningLane = -1;
				lockWarningTimer = 0.0f;
				lockedLaneTimer = laneLockDurationMs / 1000.0f;
				if (isHoldingLane[lockedLane]) {
					isHoldingLane[lockedLane] = false;
					holdingNoteIndex[lockedLane] = -1;
				}
			}
			return;
		}

		// 3. Iniciar un nuevo aviso de bloqueo eligiendo un carril que no tenga un Hold activo
		if (songTimeMs >= nextLockTriggerMs && songTimeMs < lastNoteEndTimeMs - LOCK_END_OF_SONG_MARGIN_MS) {
			int candidate = LANE_LOCK_CYCLE_ORDER[lockCycleCounter % LANE_LOCK_CYCLE_ORDER.length];
			for (int offset = 0; offset < LANE_COUNT; offset++) {
				int testLane = LANE_LOCK_CYCLE_ORDER[(lockCycleCounter + offset) % LANE_LOCK_CYCLE_ORDER.length];
				if (!isHoldingLane[testLane]) {
					candidate = testLane;
					break;
				}
			}
			lockCycleCounter++;
			warningLane = candidate;
			lockWarningTimer = LOCK_WARNING_LEAD_SEC;
			lockStartMs = songTimeMs + LOCK_WARNING_LEAD_MS;
			lockEndMs = lockStartMs + laneLockDurationMs;
		}
	}

	/**
	 * Procesa en cada frame las entradas de los 6 carriles, el estado de las Hold Notes activas
	 * y las notas que expiraron sin ser pulsadas.
	 */
	private void processInputAndNotes(int songTimeMs) {
		for (int laneIdx = 0; laneIdx < LANE_COUNT; laneIdx++) {
			boolean justPressed = Config.wasRhythmKeyJustPressed(laneIdx);
			boolean isDown =
				KBInput.isKeyDown(Config.RhythmButtons[laneIdx]) ||
				KBInput.isKeyDown(Config.RhythmAltButtons[laneIdx]);

			// Si el carril está bloqueado, pulsar su tecla inflige una penalización directa de 2 HP
			if (laneIdx == lockedLane) {
				if (justPressed) {
					applyPlayerDamage(LOCKED_LANE_PRESS_DAMAGE);
					if (hitListener != null) {
						hitListener.onHit(laneIdx, HitRating.MISS, combo, "CARRIL BLOQUEADO!");
					}
				}
				if (isHoldingLane[laneIdx]) {
					isHoldingLane[laneIdx] = false;
					holdingNoteIndex[laneIdx] = -1;
				}
				continue;
			}

			if (justPressed) {
				pressKey(laneIdx, songTimeMs);
			}

			if (isHoldingLane[laneIdx]) {
				updateActiveHoldOnLane(laneIdx, songTimeMs, isDown);
			}
		}

		processExpiredNotes(songTimeMs);
	}

	/**
	 * Marca como procesadas las notas que superaron la ventana SAFE sin ser golpeadas.
	 * Las notas que caen en un carril actualmente bloqueado se descartan sin penalizar al jugador.
	 */
	private void processExpiredNotes(int songTimeMs) {
		float safeWin = getSafeWindowMs();
		for (int i = 0; i < notes.length; i++) {
			if (processedNotes[i]) continue;

			long packed = notes[i];
			long startTime = NotePacker.getStartTime(packed);
			int laneIdx = NotePacker.getLane(packed);

			if (laneIdx == lockedLane && songTimeMs >= startTime) {
				processedNotes[i] = true;
				continue;
			}

			if (songTimeMs > startTime + safeWin) {
				processedNotes[i] = true;
				missedNotes[i] = true;
				registerHit(laneIdx, HitRating.MISS);
			}
		}
	}

	/**
	 * Verifica si el combate terminó por derrota (HP jugador <= 0) o por victoria
	 * (HP jefe <= 0 o todas las notas procesadas tras el margen final de la canción).
	 */
	private void checkVictoryOrDefeat(int songTimeMs) {
		if (playerHp <= 0) {
			battleState = BattleState.DEFEAT;
			return;
		}
		if (!bossInvincible && bossHp <= 0) {
			battleState = BattleState.VICTORY;
			return;
		}
		if (notes != null && notes.length > 0 && songTimeMs > lastNoteEndTimeMs + VICTORY_OUTRO_GRACE_MS) {
			boolean allDone = true;
			for (boolean p : processedNotes) {
				if (!p) {
					allDone = false;
					break;
				}
			}
			if (allDone) {
				battleState = BattleState.VICTORY;
			}
		}
	}

	// =========================================================================
	// 5. Procesamiento de entrada por carril (pressKey, releaseKey), cálculo de
	//    ventanas de acierto y penalizaciones/curación dinámica
	// =========================================================================

	/**
	 * Evalúa la pulsación de tecla en un carril buscando la nota no procesada más cercana
	 * dentro de la ventana de captura ({@link #getCaptureWindowMs()}).
	 * <ul>
	 *   <li>Si encuentra una nota dentro de la ventana, evalúa su juicio ({@code PERFECT},
	 *       {@code GOOD}, {@code SAFE} o {@code MISS}); si además tiene duración ({@code duration > 0})
	 *       y no fue {@code MISS}, inicia la retención del Hold en el carril.</li>
	 *   <li>Si no hay ninguna nota cercana (pulsación al vacío una vez iniciada la canción),
	 *       se penaliza con {@code MISS}.</li>
	 * </ul>
	 */
	private void pressKey(int laneIdx, int songTimeMs) {
		int bestIdx = -1;
		long bestDiff = Long.MAX_VALUE;
		float captureWin = getCaptureWindowMs();

		for (int i = 0; i < notes.length; i++) {
			if (processedNotes[i]) continue;

			long packed = notes[i];
			if (NotePacker.getLane(packed) != laneIdx) continue;

			long startTime = NotePacker.getStartTime(packed);
			long diff = Math.abs((long) songTimeMs - startTime);

			if (diff <= captureWin && diff < bestDiff) {
				bestDiff = diff;
				bestIdx = i;
			}
		}

		if (bestIdx != -1) {
			long packed = notes[bestIdx];
			long duration = NotePacker.getDuration(packed);
			HitRating rating = evaluateRating((float) bestDiff);

			processedNotes[bestIdx] = true;
			if (rating == HitRating.MISS) {
				missedNotes[bestIdx] = true;
			}

			if (duration > 0 && rating != HitRating.MISS) {
				isHoldingLane[laneIdx] = true;
				holdingNoteIndex[laneIdx] = bestIdx;
			}
			registerHit(laneIdx, rating);
		} else if (notes != null && notes.length > 0 && songTimeMs >= (firstNoteStartTimeMs - captureWin)) {
			registerHit(laneIdx, HitRating.MISS);
		}
	}

	/**
	 * Actualiza el estado de una Hold Note activa en el carril {@code laneIdx}.
	 * Si el jugador suelta la tecla antes de {@code endTime}, se delega a {@link #releaseKey};
	 * si mantiene presionada la tecla hasta alcanzar o superar {@code endTime}, se otorga {@code PERFECT}.
	 */
	private void updateActiveHoldOnLane(int laneIdx, int songTimeMs, boolean isDown) {
		int noteIdx = holdingNoteIndex[laneIdx];
		long packed = notes[noteIdx];
		long endTime = NotePacker.getStartTime(packed) + NotePacker.getDuration(packed);

		if (!isDown) {
			releaseKey(laneIdx, songTimeMs, endTime);
		} else if (songTimeMs >= endTime) {
			registerHit(laneIdx, HitRating.PERFECT);
			isHoldingLane[laneIdx] = false;
			holdingNoteIndex[laneIdx] = -1;
		}
	}

	/**
	 * Evalúa la liberación de tecla sobre una Hold Note activa comparando {@code songTimeMs}
	 * contra el instante final {@code endTime} de la nota mantenida:
	 * <ul>
	 *   <li>Si se suelta demasiado pronto ({@code songTimeMs < endTime - getSafeWindowMs()}),
	 *       se registra {@code MISS}.</li>
	 *   <li>Si se suelta dentro de la ventana de tolerancia final, se evalúa la diferencia
	 *       {@code |endTime - songTimeMs|} con las ventanas estándar de juicio.</li>
	 * </ul>
	 */
	private void releaseKey(int laneIdx, int songTimeMs, long endTime) {
		int noteIdx = holdingNoteIndex[laneIdx];
		long releaseDiff = Math.abs(endTime - songTimeMs);
		if (songTimeMs < endTime - getSafeWindowMs()) {
			if (missedNotes != null && noteIdx >= 0 && noteIdx < missedNotes.length) {
				missedNotes[noteIdx] = true;
			}
			registerHit(laneIdx, HitRating.MISS);
		} else {
			registerHit(laneIdx, evaluateRating((float) releaseDiff));
		}
		isHoldingLane[laneIdx] = false;
		holdingNoteIndex[laneIdx] = -1;
	}

	/** Ventana en ms para juicio PERFECT (45ms base +/- modificadores de amuletos). */
	public float getPerfectWindowMs() {
		return Math.max(MIN_PERFECT_WINDOW_MS, HitRating.PERFECT.baseWindowMs + windowOffsetMs);
	}

	/** Ventana en ms para juicio GOOD (85ms base +/- modificadores de amuletos). */
	public float getGoodWindowMs() {
		return Math.max(MIN_GOOD_WINDOW_MS, HitRating.GOOD.baseWindowMs + windowOffsetMs);
	}

	/** Ventana en ms para juicio SAFE (125ms base +/- modificadores de amuletos). */
	public float getSafeWindowMs() {
		return Math.max(MIN_SAFE_WINDOW_MS, HitRating.SAFE.baseWindowMs + windowOffsetMs);
	}

	/**
	 * Ventana máxima de captura en ms (170ms base). Una nota pulsada entre {@code SAFE} y
	 * {@code CAPTURE} es consumida como {@code MISS} temprano para evitar dobles fallos.
	 */
	public float getCaptureWindowMs() {
		return Math.max(
			getSafeWindowMs() + CAPTURE_WINDOW_MARGIN_MS,
			HitRating.MISS.baseWindowMs + Math.max(0.0f, windowOffsetMs)
		);
	}

	/** Clasifica una desviación temporal absoluta {@code diffMs} en su {@link HitRating} correspondiente. */
	private HitRating evaluateRating(float diffMs) {
		if (diffMs <= getPerfectWindowMs()) return HitRating.PERFECT;
		if (diffMs <= getGoodWindowMs()) return HitRating.GOOD;
		if (diffMs <= getSafeWindowMs()) return HitRating.SAFE;
		return HitRating.MISS;
	}

	/**
	 * Aplica los efectos de un juicio sobre el combo, el puntaje, el daño al jefe o al jugador
	 * y la curación progresiva:
	 * <ul>
	 *   <li><b>Curación progresiva:</b> encadenar {@code 2} aciertos consecutivos ({@code PERFECT},
	 *       {@code GOOD} o {@code SAFE}) recupera el {@code 50%} del último golpe sufrido
	 *       ({@code 75%} si se posee el amuleto de curación) y limpia {@code lastDamageTaken}.
	 *       Un {@code MISS} reinicia el contador {@code healPoints} a {@code 0}.</li>
	 * </ul>
	 */
	private void registerHit(int laneIdx, HitRating rating) {
		switch (rating) {
			case PERFECT -> {
				perfectCount++;
				combo++;
				score += SCORE_PERFECT_BASE + combo * SCORE_PERFECT_COMBO_MULT;
				damageBoss(Math.round(bossHitDamage * BOSS_DMG_PERFECT_MULT));
			}
			case GOOD -> {
				greatCount++;
				combo++;
				score += SCORE_GOOD_BASE + combo * SCORE_GOOD_COMBO_MULT;
				damageBoss(bossHitDamage);
			}
			case SAFE -> {
				goodCount++;
				combo++;
				score += SCORE_SAFE_BASE;
				damageBoss(Math.max(1, Math.round(bossHitDamage * BOSS_DMG_SAFE_MULT)));
			}
			case MISS -> {
				missCount++;
				combo = 0;
				applyPlayerDamage(bossAttackDamage);
			}
		}

		if (combo > maxCombo) maxCombo = combo;

		// Curación progresiva cada 2 notas acertadas consecutivas tras recibir daño
		if (rating == HitRating.MISS) {
			healPoints = 0;
		} else {
			healPoints++;
			if (healPoints >= CONSECUTIVE_HITS_FOR_HEAL) {
				healPoints = 0;
				if (lastDamageTaken > 0 && playerHp < playerMaxHp) {
					float healPercent = GameState.hasHealingAmulet()
						? AMULET_DAMAGE_RECOVERY_RATIO
						: BASE_DAMAGE_RECOVERY_RATIO;
					int healed = Math.max(1, Math.round(lastDamageTaken * healPercent));
					playerHp = Math.min(playerMaxHp, playerHp + healed);
					lastDamageTaken = 0;
				}
			}
		}

		if (hitListener != null) {
			hitListener.onHit(laneIdx, rating, combo, rating.displayLabel);
		}
	}

	private void damageBoss(int dmg) {
		if (dmg <= 0) return;
		bossFlashTimer = BOSS_FLASH_DURATION_SEC;
		if (bossInvincible) return;
		bossHp = Math.max(0, bossHp - dmg);
	}

	private void applyPlayerDamage(int dmg) {
		if (dmg <= 0 || invincibilityTimer > 0.0f) return;

		if (playerHp - dmg <= 0 && hasInvincibilityShield) {
			hasInvincibilityShield = false;
			invincibilityTimer = INVINCIBILITY_SHIELD_DURATION_SEC;
			playerHp = Math.max(INVINCIBILITY_SHIELD_MIN_HP, playerHp);
			GameState.showNotification("Escudo de Invencibilidad activado!");
			return;
		}

		playerHp = Math.max(0, playerHp - dmg);
		lastDamageTaken = dmg;
	}

	// =========================================================================
	// 6. Getters de estado para la escena visual
	// =========================================================================

	public long[] getNotes() { return notes; }
	public boolean isNoteProcessed(int index) { return processedNotes[index]; }
	public boolean isNoteMissed(int index) { return missedNotes != null && index >= 0 && index < missedNotes.length && missedNotes[index]; }
	public boolean isHoldingLane(int lane) { return isHoldingLane[lane]; }
	public int getHoldingNoteIndex(int lane) { return holdingNoteIndex[lane]; }
	public boolean isPaused() { return isPaused; }
	public void togglePause() { isPaused = !isPaused; }
	public void setOnHitListener(OnHitListener listener) { this.hitListener = listener; }

	public BattleState getBattleState() { return battleState; }
	public int getCombo() { return combo; }
	public int getMaxCombo() { return maxCombo; }
	public int getScore() { return score; }
	public int getPerfectCount() { return perfectCount; }
	public int getGoodCount() { return greatCount; }
	public int getSafeCount() { return goodCount; }
	public int getMissCount() { return missCount; }

	public float getAccuracyPercent() {
		int total = perfectCount + greatCount + goodCount + missCount;
		if (total <= 0) return 0.0f;
		float weighted = (perfectCount * 100.0f) + (greatCount * 75.0f) + (goodCount * 40.0f);
		float pct = Math.max(0.0f, Math.min(100.0f, weighted / total));
		return Math.round(pct * 100.0f) / 100.0f;
	}

	public int getPlayerHp() { return playerHp; }
	public int getPlayerMaxHp() { return playerMaxHp; }
	public int getBossHp() { return bossHp; }
	public int getBossMaxHp() { return bossMaxHp; }
	public boolean isBossInvincible() { return bossInvincible; }
	public void setBossInvincible(boolean bossInvincible) { this.bossInvincible = bossInvincible; }
	public float getBossFlashTimer() { return bossFlashTimer; }

	public int getHealPoints() { return healPoints; }
	public int getEffectiveHealThreshold() { return CONSECUTIVE_HITS_FOR_HEAL; }
	public boolean isHealBarActive() { return healBarActive; }
	public float getHealBarTimer() { return healBarTimer; }
	public float getHealActiveDuration() { return healActiveDuration; }

	public boolean hasInvincibilityShield() { return hasInvincibilityShield; }
	public float getInvincibilityTimer() { return invincibilityTimer; }

	public int getWarningLane() { return warningLane; }
	public int getLockedLane() { return lockedLane; }
	public int getLockVisualLane() { return (lockedLane != -1) ? lockedLane : warningLane; }
	public int getLockStartMs() { return lockStartMs; }
	public int getLockEndMs() { return lockEndMs; }
}
