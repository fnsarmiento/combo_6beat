package engine;

import java.util.Arrays;
import processing.event.KeyEvent;

/**
 * Gestor de teclado thread-safe con latching por frame:
 * - Captura pulsaciones rápidas (press + release dentro del mismo frame) sin perder taps.
 * - Filtra auto-repeat del sistema operativo para evitar disparos duplicados al mantener teclas.
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
		System.arraycopy(_liveDown, 0, _frameDown, 0, 256);
		System.arraycopy(_pendingPressed, 0, _frameJustPressed, 0, 256);
		System.arraycopy(_pendingReleased, 0, _frameJustReleased, 0, 256);
		Arrays.fill(_pendingPressed, false);
		Arrays.fill(_pendingReleased, false);
	}

	public static void update() {
		// Mantenido por compatibilidad; el snapshot atómico ocurre en beginFrame().
	}

	public static synchronized void keyEvent(KeyEvent event) {
		if (event == null || event.isAutoRepeat()) return;

		int keyCode = event.getKeyCode();
		if (keyCode < 0 || keyCode >= 256) return;

		switch (event.getAction()) {
			case KeyEvent.PRESS -> {
				if (!_liveDown[keyCode]) {
					_liveDown[keyCode] = true;
					_pendingPressed[keyCode] = true;
				}
			}
			case KeyEvent.RELEASE -> {
				if (_liveDown[keyCode]) {
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
