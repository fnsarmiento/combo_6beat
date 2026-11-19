package engine;

import processing.event.KeyEvent;

public class KBInput {
	private static boolean[] _previousKeys = new boolean[256];
	private static boolean[] _currentKeys = new boolean[256];

	public static void init() {}

	public static void update() {
		System.arraycopy(_currentKeys, 0, _previousKeys, 0, 256);
	}

	public static void keyEvent(KeyEvent event) {
		int keyCode = event.getKeyCode();
		if (keyCode < 0 || keyCode >= 256) return;

		switch (event.getAction()) {
			case KeyEvent.PRESS:
				_currentKeys[keyCode] = true;
				break;
			case KeyEvent.RELEASE:
				_currentKeys[keyCode] = false;
				break;
		}
	}

	public static int normalizeKey(int key) {
		if (key >= 'a' && key <= 'z') {
			key -= 32;
		}
		return (key >= 0 && key < 256) ? key : 0;
	}

	public static boolean isKeyDown(int key) { return _currentKeys[key]; }

	public static boolean isKeyUp(int key) { return !_currentKeys[key]; }

	public static boolean wasKeyJustPressed(int key) { return _currentKeys[key] && (!_previousKeys[key]); }

	public static boolean wasKeyJustReleased(int key) { return (!_currentKeys[key]) && _previousKeys[key]; }
}
