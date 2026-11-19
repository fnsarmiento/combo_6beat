package engine;

import java.awt.GraphicsEnvironment;
import java.awt.GraphicsDevice;
import java.awt.DisplayMode;

import controlP5.ControlP5;
import engine.audio.Audio;
import processing.core.PApplet;
import processing.core.PGraphics;
import processing.core.PVector;
import processing.event.KeyEvent;
import processing.opengl.PGraphicsOpenGL;

public class Engine {
	private static PApplet _app;
	private static Engine _instance;
	private static ControlP5 _cp5;

	private static Scene _currentScene;
	private static Scene _pendingScene;
	private static int _lastTime;
	private static PGraphics _virtualScreen;
	private static boolean _pixelPerfect = true;

	private Engine() {
		_lastTime = _app.millis();

		_cp5 = new ControlP5(_app);
		_cp5.setAutoDraw(false);

		// Framebuffers
		_virtualScreen = createFrameBuffer(_app.width, _app.height);

		// Init services
		KBInput.init(); // keyboard input
		Audio.init(_app); // audio controller

		// Register
		_app.registerMethod("pre", this);
		_app.registerMethod("draw", this);
		_app.registerMethod("keyEvent", this);
	}

	public static void init(PApplet app) {
		if (_instance != null) return;

		_app = app;
		setPixelArtOptions(app.g);
		_instance = new Engine();
	}

	public static PApplet getApp() { return _app; }

	public static ControlP5 getControlP5() { return _cp5; }

	// Framebuffers

	public static void setFramesToMonitorHZ() {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice device = ge.getDefaultScreenDevice();
  		DisplayMode mode = device.getDisplayMode();
		_app.frameRate(mode.getRefreshRate());
	}

	public static PGraphics createFrameBuffer(int res_x, int res_y) {
		PGraphics pg = _app.createGraphics(res_x > 0 ? res_x : _app.width, res_y > 0 ? res_y : _app.height, PApplet.P2D);

		pg.noSmooth();
		setPixelArtOptions(pg);

		pg.beginDraw();
		pg.background(0, 0);
		pg.endDraw();

		return pg;
	}

	public static void setPixelArtOptions(PGraphics g) {
		if (g == null) throw new IllegalStateException("Engine.setPixelArtOptions(PGraphics g): El parametro de PGraphics no puede ser nulo.");
		// Usar muestreo Nearest (2) siempre para evitar antialiasing en pixel art incluso con Pixel-Perfect OFF
		g.noSmooth();
		((PGraphicsOpenGL) g).textureSampling(2);
	}

	public static void resizeVirtualScreen(int res_x, int res_y) {
		if (res_x <= 0)
			throw new IllegalStateException("Engine.newVirtualScreen(int res_x, int res_y): res_x no puede ser cero o negativa.");
		if (res_y <= 0)
			throw new IllegalStateException("Engine.newVirtualScreen(int res_x, int res_y): res_y no puede ser cero o negativa.");

		_virtualScreen = createFrameBuffer(res_x, res_y);
	}

	public static void ensureVirtualScreenSize(int res_x, int res_y) {
		if (_virtualScreen == null || _virtualScreen.width != res_x || _virtualScreen.height != res_y) {
			resizeVirtualScreen(res_x, res_y);
		}
	}

	public static PGraphics getVirtualScreen() { return _virtualScreen; }
	public static PVector getVirtualScreenSize() { return new PVector(_virtualScreen.width, _virtualScreen.height); }

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

	// Update

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

		if (_currentScene != null) {
			_currentScene.update(deltaTime);
		}

		KBInput.update();
	}

	// Draw

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

	// Input

	public void keyEvent(KeyEvent e) {
		KBInput.keyEvent(e);

		if (_currentScene != null){
			_currentScene.keyEvent(e);}
	}

	// Scene

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
