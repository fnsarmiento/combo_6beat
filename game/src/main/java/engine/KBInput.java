package engine;

import java.util.Arrays;
import processing.event.KeyEvent;

/**
 * Gestor de teclado thread-safe con latching por frame:
 * - Nunca bloquea un PRESS físico real (aunque X11 haya perdido un RELEASE previo).
 * - Filtra auto-repeat del sistema operativo para que mantener una nota larga no dispare
 *   taps fantasma ni corte el hold por el RELEASE sintético de X11.
 * - Toma una foto atómica al inicio de cada frame (beginFrame) para evitar condiciones de carrera.
 */
public class KBInput {
	private static final boolean[] _liveDown = new boolean[256];
	private static final boolean[] _pendingPressed = new boolean[256];
	private static final boolean[] _pendingReleased = new boolean[256];

	private static final boolean[] _frameDown = new boolean[256];
	private static final boolean[] _frameJustPressed = new boolean[256];
	private static final boolean[] _frameJustReleased = new boolean[256];

	public static void init() {}

	public static synchronized void beginFrame() {
		for (int i = 0; i < 256; i++) {
			_frameJustPressed[i] = _pendingPressed[i];
			_frameJustReleased[i] = _pendingReleased[i];
			// Si hubo un tap+release dentro del mismo frame, mantener down=true ese frame
			// para no romper el inicio de una hold note en el mismo tick.
			_frameDown[i] = _liveDown[i] || (_pendingPressed[i] && !_pendingReleased[i]);
		}
		Arrays.fill(_pendingPressed, false);
		Arrays.fill(_pendingReleased, false);
	}

	public static void update() {
		// Mantenido por compatibilidad; el snapshot atómico ocurre en beginFrame().
	}

	public static synchronized void keyEvent(KeyEvent event) {
		if (event == null) return;

		int keyCode = event.getKeyCode();
		if (keyCode < 0 || keyCode >= 256) return;

		boolean autoRepeat = event.isAutoRepeat();

		switch (event.getAction()) {
			case KeyEvent.PRESS -> {
				_liveDown[keyCode] = true;
				if (!autoRepeat) {
					_pendingPressed[keyCode] = true;
				}
			}
			case KeyEvent.RELEASE -> {
				if (!autoRepeat) {
					_liveDown[keyCode] = false;
					_pendingReleased[keyCode] = true;
				}
			}
		}
	}

	public static int normalizeKey(int key) {
		if (key >= 'a' && key <= 'z') {
			key -= 32;
		}
		return (key >= 0 && key < 256) ? key : 0;
	}

	public static boolean isKeyDown(int key) {
		return (key >= 0 && key < 256) && _frameDown[key];
	}

	public static boolean isKeyUp(int key) {
		return (key < 0 || key >= 256) || !_frameDown[key];
	}

	public static boolean wasKeyJustPressed(int key) {
		return (key >= 0 && key < 256) && _frameJustPressed[key];
	}

	public static boolean wasKeyJustReleased(int key) {
		return (key >= 0 && key < 256) && _frameJustReleased[key];
	}
}
