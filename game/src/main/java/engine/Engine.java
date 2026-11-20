package engine;

import controlP5.ControlP5;
import engine.audio.Audio;
import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PGraphics;
import processing.core.PVector;
import processing.event.KeyEvent;
import processing.opengl.PGraphicsOpenGL;

/**
 * Núcleo principal del motor gráfico y de ciclo de vida sobre Processing ({@link PApplet}).
 *
 * <p>Responsabilidades principales:
 * <ul>
 *   <li>Gestionar el framebuffer virtual de baja resolución ({@code _virtualScreen}) y su escalado
 *       con muestreo Nearest-Neighbor a la ventana física para preservar la estética pixel-art.</li>
 *   <li>Coordinar el ciclo por frame registrando {@link #pre()}, {@link #draw()} y
 *       {@link #keyEvent(KeyEvent)} en el {@link PApplet} anfitrión.</li>
 *   <li>Administrar las transiciones seguras entre instancias de {@link Scene} al inicio de cada tick.</li>
 *   <li>Renderizar la capa de interfaz {@link ControlP5} por encima del lienzo virtual escalado.</li>
 * </ul>
 */
public class Engine {

	// =========================================================================
	// Estado Estático del Motor
	// =========================================================================

	private static PApplet _app;
	private static Engine _instance;
	private static ControlP5 _cp5;

	private static Scene _currentScene;
	private static Scene _pendingScene;
	private static int _lastTime;
	private static PGraphics _virtualScreen;
	private static boolean _pixelPerfect = true;
	private static boolean _muteOnUnfocus = true;

	/** Tipografías compartidas para encabezados y textos de HUD en escenas y overlays. */
	public static PFont titleFont;
	public static PFont descriptionFont;

	// =========================================================================
	// Inicialización y Acceso Global
	// =========================================================================

	private Engine() {
		_lastTime = _app.millis();

		_cp5 = new ControlP5(_app);
		_cp5.setAutoDraw(false);

		_virtualScreen = createFrameBuffer(_app.width, _app.height);

		KBInput.init();
		Audio.init(_app);

		_app.registerMethod("pre", this);
		_app.registerMethod("draw", this);
		_app.registerMethod("keyEvent", this);
	}

	/**
	 * Inicializa el singleton del motor enlazándolo al {@link PApplet} principal.
	 */
	public static void init(PApplet app) {
		if (_instance != null) return;

		_app = app;
		setPixelArtOptions(app.g);
		_instance = new Engine();
	}

	public static PApplet getApp() {
		return _app;
	}

	public static ControlP5 getControlP5() {
		return _cp5;
	}

	// =========================================================================
	// Pantalla Virtual, Escalado Pixel-Art y Ventana
	// =========================================================================

	/**
	 * Sincroniza la tasa de cuadros por segundo con la frecuencia de refresco del monitor activo.
	 */
	public static void setFramesToMonitorHZ() {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice device = ge.getDefaultScreenDevice();
		DisplayMode mode = device.getDisplayMode();
		_app.frameRate(mode.getRefreshRate());
	}

	/**
	 * Crea un framebuffer {@code P2D} configurado con muestreo Nearest-Neighbor sin suavizado.
	 */
	public static PGraphics createFrameBuffer(int res_x, int res_y) {
		int targetW = res_x > 0 ? res_x : _app.width;
		int targetH = res_y > 0 ? res_y : _app.height;
		PGraphics pg = _app.createGraphics(targetW, targetH, PApplet.P2D);

		pg.noSmooth();
		setPixelArtOptions(pg);

		pg.beginDraw();
		pg.background(0, 0);
		pg.endDraw();

		return pg;
	}

	/**
	 * Fuerza muestreo de texturas Nearest-Neighbor (modo 2 en OpenGL) para evitar filtrado bilineal.
	 */
	public static void setPixelArtOptions(PGraphics g) {
		if (g == null) {
			throw new IllegalStateException("Engine.setPixelArtOptions(PGraphics g): El parametro de PGraphics no puede ser nulo.");
		}
		g.noSmooth();
		((PGraphicsOpenGL) g).textureSampling(2);
	}

	/**
	 * Redimensiona el framebuffer virtual donde las escenas dibujan su contenido base.
	 */
	public static void resizeVirtualScreen(int res_x, int res_y) {
		if (res_x <= 0) {
			throw new IllegalStateException("Engine.newVirtualScreen(int res_x, int res_y): res_x no puede ser cero o negativa.");
		}
		if (res_y <= 0) {
			throw new IllegalStateException("Engine.newVirtualScreen(int res_x, int res_y): res_y no puede ser cero o negativa.");
		}

		_virtualScreen = createFrameBuffer(res_x, res_y);
	}

	/**
	 * Garantiza que el framebuffer virtual tenga las dimensiones indicadas, recreándolo sólo si cambió.
	 */
	public static void ensureVirtualScreenSize(int res_x, int res_y) {
		if (_virtualScreen == null || _virtualScreen.width != res_x || _virtualScreen.height != res_y) {
			resizeVirtualScreen(res_x, res_y);
		}
	}

	public static PGraphics getVirtualScreen() {
		return _virtualScreen;
	}

	public static PVector getVirtualScreenSize() {
		return new PVector(_virtualScreen.width, _virtualScreen.height);
	}

	public static void setPixelPerfect(boolean enabled) {
		_pixelPerfect = enabled;
		if (_app != null && _app.g != null) {
			setPixelArtOptions(_app.g);
		}
		if (_virtualScreen != null) {
			setPixelArtOptions(_virtualScreen);
		}
	}

	public static boolean isPixelPerfect() {
		return _pixelPerfect;
	}

	/** Activa o desactiva el silencio automático cuando la ventana pierde el foco. */
	public static void setMuteOnUnfocus(boolean enabled) {
		_muteOnUnfocus = enabled;
		if (!_muteOnUnfocus) {
			Audio.setFocusMute(false);
		}
	}

	/**
	 * Cambia la resolución física de la ventana en tiempo de ejecución y reconfigura {@link ControlP5}.
	 */
	public static void setWindowResolution(int width, int height) {
		if (_app == null || width <= 0 || height <= 0) return;

		var surface = _app.getSurface();
		surface.setResizable(true);
		Object nativeWin = surface.getNative();
		if (nativeWin != null) {
			try {
				nativeWin.getClass().getMethod("setResizable", boolean.class).invoke(nativeWin, true);
			} catch (Exception ignored) {}
		}

		surface.setSize(width, height);
		if (nativeWin != null) {
			try {
				nativeWin.getClass().getMethod("setSize", int.class, int.class).invoke(nativeWin, width, height);
			} catch (Exception ignored) {}
		}

		if (_app.g != null) {
			setPixelArtOptions(_app.g);
		}
		if (_cp5 != null) {
			_cp5.setGraphics(_app, 0, 0);
		}
	}

	// =========================================================================
	// Ciclo de Actualización, Renderizado e Input (Hooks de Processing)
	// =========================================================================

	/**
	 * Ejecutado por Processing antes de cada frame: procesa cambios de escena pendientes,
	 * calcula {@code deltaTime}, consolida el snapshot de teclado y actualiza la escena activa.
	 */
	public void pre() {
		if (_pendingScene != null) {
			Scene next = _pendingScene;
			_pendingScene = null;
			if (_currentScene != null) {
				_currentScene.close();
			}
			if (_cp5 != null && _app != null) {
				_cp5.setGraphics(_app, 0, 0);
			}
			_currentScene = next;
			_currentScene.setup();
		}

		int now = _app.millis();
		float deltaTime = (now - _lastTime) / 1000.0f;
		_lastTime = now;

		KBInput.beginFrame();

		Audio.setFocusMute(_muteOnUnfocus && _app != null && !_app.focused);

		if (_currentScene != null) {
			_currentScene.update(deltaTime);
		}
	}

	/**
	 * Dibuja la escena activa sobre {@code _virtualScreen}, lo escala a la ventana física
	 * y finalmente superpone los controles de {@link ControlP5}.
	 */
	public void draw() {
		if (_virtualScreen == null) return;

		_virtualScreen.beginDraw();
		_virtualScreen.background(0);

		if (_currentScene != null) {
			_currentScene.draw(_virtualScreen);
		}

		_virtualScreen.endDraw();

		if (_app.g != null) {
			setPixelArtOptions(_app.g);
		}
		_app.image(_virtualScreen, 0, 0, _app.width, _app.height);
		_cp5.draw();
	}

	/**
	 * Recibe los eventos crudos de teclado desde Processing y los propaga a {@link KBInput} y a la escena.
	 */
	public void keyEvent(KeyEvent e) {
		KBInput.keyEvent(e);

		if (_currentScene != null) {
			_currentScene.keyEvent(e);
		}
	}

	// =========================================================================
	// Gestión de Escenas
	// =========================================================================

	/**
	 * Solicita el cambio a una nueva escena. Si aún no hay escena activa, la inicializa de inmediato;
	 * en caso contrario, difiere la transición al inicio del siguiente {@link #pre()}.
	 */
	public static void changeScene(Scene scene) {
		if (_currentScene == null) {
			_currentScene = scene;
			if (_currentScene != null) {
				_currentScene.setup();
			}
			return;
		}
		_pendingScene = scene;
	}
}
