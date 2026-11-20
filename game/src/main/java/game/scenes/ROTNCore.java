package game.scenes;

import engine.KBInput;
import game.Config;
import game.core.GameState;
import game.core.notes.NotePacker;

public class ROTNCore {

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

	public enum BattleState {
		PLAYING,
		VICTORY,
		DEFEAT
	}

	public interface OnHitListener {
		void onHit(int lane, HitRating rating, int combo, String detail);
	}

	private long[] notes;
	private boolean[] processedNotes;
	private final boolean[] isHoldingLane = new boolean[6];
	private final int[] holdingNoteIndex = new int[6];

	private boolean isPaused;
	private BattleState battleState = BattleState.PLAYING;

	private int combo, maxCombo, score;
	private int perfectCount, greatCount, goodCount, missCount;

	// Vida del jugador y del jefe
	private int playerMaxHp = 200;
	private int playerHp = 200;
	private int lastDamageTaken = 0;
	private int bossMaxHp = 1500;
	private int bossHp = 1500;
	private boolean bossInvincible = false;
	private int bossAttackDamage = 12;
	private int bossHitDamage = 6;
	private float bossFlashTimer = 0.0f;

	// Sistema de Curación Dinámica y Power-Ups
	private float windowOffsetMs = 0.0f;
	private int baseHealThreshold = 50;
	private int healCostOffset = 0;
	private int healPoints = 0;
	private boolean healBarActive = false;
	private float healActiveDuration = 6.0f;
	private float healBarTimer = 0.0f;
	private float healPulseTimer = 0.0f;

	private boolean hasInvincibilityShield = false;
	private float invincibilityTimer = 0.0f;

	// Inhabilitación temporal de carriles por ataques del jefe
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

	private long firstNoteStartTimeMs = Long.MAX_VALUE;
	private long lastNoteEndTimeMs = 0;
	private OnHitListener hitListener;

	public ROTNCore(long[] notes) {
		setNotes(notes);
		applyPowerUpModifiers();
	}

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
		this.baseHealThreshold = Math.max(15, healThreshold);
		this.healActiveDuration = Math.max(1.0f, healActiveDuration);
		this.laneLockIntervalMs = laneLockIntervalMs;
		this.laneLockDurationMs = Math.max(500, laneLockDurationMs);
		this.nextLockTriggerMs = (laneLockIntervalMs > 0) ? laneLockIntervalMs : Integer.MAX_VALUE;
		applyPowerUpModifiers();
	}

	public void applyPowerUpModifiers() {
		windowOffsetMs = 0.0f;
		healCostOffset = 0;
		hasInvincibilityShield = false;
		invincibilityTimer = 0.0f;

		if (GameState.isPowerUpEquipped("powerup_reaction_plus")) {
			windowOffsetMs += 10.0f;
			healCostOffset += 25;
		}
		if (GameState.isPowerUpEquipped("powerup_reaction_minus")) {
			windowOffsetMs -= 7.5f;
			healCostOffset -= 20;
		}
		if (GameState.isPowerUpEquipped("powerup_invincibility")) {
			hasInvincibilityShield = true;
		}
	}

	public void setNotes(long[] notes) {
		this.notes = notes;
		int count = notes != null ? notes.length : 0;
		this.processedNotes = new boolean[count];
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

		for (int i = 0; i < 6; i++) {
			holdingNoteIndex[i] = -1;
			isHoldingLane[i] = false;
		}
		resetStats();
	}

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
		lockCycleCounter = 0;
		nextLockTriggerMs = (laneLockIntervalMs > 0) ? laneLockIntervalMs : Integer.MAX_VALUE;
		applyPowerUpModifiers();
	}

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

	private void updateHealBar(float dt) {
		if (!healBarActive) return;

		healBarTimer -= dt;
		healPulseTimer -= dt;

		if (healPulseTimer <= 0.0f && playerHp < playerMaxHp) {
			int missing = playerMaxHp - playerHp;
			int itemHeal = GameState.consumeAutoHeal(missing);
			int healed = (itemHeal > 0) ? itemHeal : 4;
			playerHp = Math.min(playerMaxHp, playerHp + healed);
			healPulseTimer = 0.9f;
		}

		if (healBarTimer <= 0.0f) {
			healBarActive = false;
			healBarTimer = 0.0f;
		}
	}

	private void updateLaneLockAttack(int songTimeMs, float dt) {
		if (laneLockIntervalMs <= 0 || notes == null || notes.length == 0 || songTimeMs < firstNoteStartTimeMs) {
			lockedLane = -1;
			warningLane = -1;
			lockStartMs = -1;
			lockEndMs = -1;
			return;
		}

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

		if (songTimeMs >= nextLockTriggerMs && songTimeMs < lastNoteEndTimeMs - 1500L) {
			final int[] order = { 1, 4, 0, 3, 5, 2 };
			int candidate = order[lockCycleCounter % order.length];
			for (int offset = 0; offset < 6; offset++) {
				int testLane = order[(lockCycleCounter + offset) % order.length];
				if (!isHoldingLane[testLane]) {
					candidate = testLane;
					break;
				}
			}
			lockCycleCounter++;
			warningLane = candidate;
			lockWarningTimer = 1.4f;
			lockStartMs = songTimeMs + 1400;
			lockEndMs = lockStartMs + laneLockDurationMs;
		}
	}

	private void processInputAndNotes(int songTimeMs) {
		for (int laneIdx = 0; laneIdx < 6; laneIdx++) {
			boolean justPressed = Config.wasRhythmKeyJustPressed(laneIdx);
			boolean isDown =
				KBInput.isKeyDown(Config.RhythmButtons[laneIdx]) ||
				KBInput.isKeyDown(Config.RhythmAltButtons[laneIdx]);

			if (laneIdx == lockedLane) {
				if (justPressed) {
					applyPlayerDamage(2);
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
				checkNotePress(laneIdx, songTimeMs);
			}

			// Gestión de Hold Notes activas
			if (isHoldingLane[laneIdx]) {
				int noteIdx = holdingNoteIndex[laneIdx];
				long packed = notes[noteIdx];
				long endTime =
					NotePacker.getStartTime(packed) +
					NotePacker.getDuration(packed);

				if (!isDown) {
					long releaseDiff = Math.abs(endTime - songTimeMs);
					if (songTimeMs < endTime - getSafeWindowMs()) {
						registerHit(laneIdx, HitRating.MISS);
					} else {
						registerHit(laneIdx, evaluateRating((float) releaseDiff));
					}
					isHoldingLane[laneIdx] = false;
					holdingNoteIndex[laneIdx] = -1;
				} else if (songTimeMs >= endTime) {
					registerHit(laneIdx, HitRating.PERFECT);
					isHoldingLane[laneIdx] = false;
					holdingNoteIndex[laneIdx] = -1;
				}
			}
		}

		// Comprobar notas que pasaron de largo sin ser presionadas
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
				registerHit(laneIdx, HitRating.MISS);
			}
		}
	}

	private void checkNotePress(int laneIdx, int songTimeMs) {
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

			if (duration > 0 && rating != HitRating.MISS) {
				isHoldingLane[laneIdx] = true;
				holdingNoteIndex[laneIdx] = bestIdx;
			}
			registerHit(laneIdx, rating);
		} else {
			if (notes != null && notes.length > 0 && songTimeMs >= (firstNoteStartTimeMs - captureWin)) {
				registerHit(laneIdx, HitRating.MISS);
			}
		}
	}

	public float getPerfectWindowMs() {
		return Math.max(8.0f, HitRating.PERFECT.baseWindowMs + windowOffsetMs);
	}

	public float getGoodWindowMs() {
		return Math.max(15.0f, HitRating.GOOD.baseWindowMs + windowOffsetMs);
	}

	public float getSafeWindowMs() {
		return Math.max(25.0f, HitRating.SAFE.baseWindowMs + windowOffsetMs);
	}

	public float getCaptureWindowMs() {
		return Math.max(getSafeWindowMs() + 25.0f, HitRating.MISS.baseWindowMs + Math.max(0.0f, windowOffsetMs));
	}

	private HitRating evaluateRating(float diffMs) {
		if (diffMs <= getPerfectWindowMs()) return HitRating.PERFECT;
		if (diffMs <= getGoodWindowMs()) return HitRating.GOOD;
		if (diffMs <= getSafeWindowMs()) return HitRating.SAFE;
		return HitRating.MISS;
	}

	private void registerHit(int laneIdx, HitRating rating) {
		switch (rating) {
			case PERFECT -> {
				perfectCount++;
				combo++;
				score += 300 + combo * 10;
				damageBoss(Math.round(bossHitDamage * 1.5f));
			}
			case GOOD -> {
				greatCount++;
				combo++;
				score += 200 + combo * 5;
				damageBoss(bossHitDamage);
			}
			case SAFE -> {
				goodCount++;
				combo++;
				score += 100;
				damageBoss(Math.max(1, Math.round(bossHitDamage * 0.6f)));
			}
			case MISS -> {
				missCount++;
				combo = 0;
				applyPlayerDamage(bossAttackDamage);
			}
		}

		if (combo > maxCombo) maxCombo = combo;

		// Curacion por 2 notas consecutivas: recupera 50% del ultimo dano recibido (75% con amuleto)
		if (rating == HitRating.MISS) {
			healPoints = 0;
		} else {
			healPoints++;
			if (healPoints >= 2) {
				healPoints = 0;
				if (lastDamageTaken > 0 && playerHp < playerMaxHp) {
					float healPercent = GameState.hasHealingAmulet() ? 0.75f : 0.50f;
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
		bossFlashTimer = 0.14f;
		if (bossInvincible) return;
		bossHp = Math.max(0, bossHp - dmg);
	}

	private void applyPlayerDamage(int dmg) {
		if (dmg <= 0 || invincibilityTimer > 0.0f) return;

		if (playerHp - dmg <= 0 && hasInvincibilityShield) {
			hasInvincibilityShield = false;
			invincibilityTimer = 4.0f;
			playerHp = Math.max(15, playerHp);
			GameState.showNotification("Escudo de Invencibilidad activado!");
			return;
		}

		playerHp = Math.max(0, playerHp - dmg);
		lastDamageTaken = dmg;
	}

	private void checkVictoryOrDefeat(int songTimeMs) {
		if (playerHp <= 0) {
			battleState = BattleState.DEFEAT;
			return;
		}
		if (!bossInvincible && bossHp <= 0) {
			battleState = BattleState.VICTORY;
			return;
		}
		if (notes != null && notes.length > 0 && songTimeMs > lastNoteEndTimeMs + 2000L) {
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

	// Getters

	public long[] getNotes() { return notes; }
	public boolean isNoteProcessed(int index) { return processedNotes[index]; }
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
	public int getEffectiveHealThreshold() { return 2; }
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
