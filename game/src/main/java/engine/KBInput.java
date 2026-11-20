package engine;

import java.util.Arrays;
import processing.event.KeyEvent;

/**
 * Gestor de entrada de teclado thread-safe con mecanismo de latching (snapshot atómico) por frame.
 *
 * <p>Funcionamiento del latch en {@link #beginFrame()}:
 * <ul>
 *   <li>Los eventos asíncronos de Processing ({@link #keyEvent(KeyEvent)}) actualizan los buffers
 *       vivos ({@code _liveDown}, {@code _pendingPressed}, {@code _pendingReleased}) filtrando
 *       repeticiones automáticas del sistema operativo ({@code isAutoRepeat()}).</li>
 *   <li>Al inicio de cada frame en {@link Engine#pre()}, {@link #beginFrame()} toma una foto
 *       atómica hacia {@code _frameDown}, {@code _frameJustPressed} y {@code _frameJustReleased},
 *       limpiando las colas pendientes.</li>
 *   <li>Si una tecla fue presionada y aún no liberada en el mismo tick, {@code _frameDown} se
 *       mantiene activo durante ese frame completo para evitar perder notas rápidas o inicios de Hold.</li>
 * </ul>
 */
public class KBInput {

	// =========================================================================
	// Buffers de Eventos Asíncronos y Snapshot del Frame Actual
	// =========================================================================

	private static final boolean[] _liveDown = new boolean[256];
	private static final boolean[] _pendingPressed = new boolean[256];
	private static final boolean[] _pendingReleased = new boolean[256];

	private static final boolean[] _frameDown = new boolean[256];
	private static final boolean[] _frameJustPressed = new boolean[256];
	private static final boolean[] _frameJustReleased = new boolean[256];

	// =========================================================================
	// Ciclo de Vida y Latching por Frame
	// =========================================================================

	public static void init() {}

	/**
	 * Captura de forma atómica el estado del teclado para el frame en curso y limpia
	 * las transiciones acumuladas desde el frame anterior.
	 */
	public static synchronized void beginFrame() {
		for (int i = 0; i < 256; i++) {
			_frameJustPressed[i] = _pendingPressed[i];
			_frameJustReleased[i] = _pendingReleased[i];
			// Si hubo un tap en este frame sin release, asegurar down=true durante el tick actual
			_frameDown[i] = _liveDown[i] || (_pendingPressed[i] && !_pendingReleased[i]);
		}
		Arrays.fill(_pendingPressed, false);
		Arrays.fill(_pendingReleased, false);
	}

	/**
	 * Mantenido por compatibilidad; el snapshot atómico se realiza en {@link #beginFrame()}.
	 */
	public static void update() {}

	/**
	 * Procesa un evento de teclado de Processing ignorando eventos de auto-repeat del SO.
	 */
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

	// =========================================================================
	// Consultas de Estado por Tecla
	// =========================================================================

	/**
	 * Normaliza caracteres ASCII en minúscula ({@code 'a'..'z'}) a su código en mayúscula ({@code 'A'..'Z'}).
	 */
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
