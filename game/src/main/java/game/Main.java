package game;

import static game.Global.*;

import engine.Engine;
import engine.dialogs.GameDialog;
import game.core.GameState;
import game.core.db.CustomSongRepository;
import game.core.db.DatabaseManager;
import game.scenes.InitScene;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PFont;

/**
 * Punto de entrada principal de Combo 6Beat sobre Processing (`PApplet`).
 * <p>
 * Inicializa el contexto grafico OpenGL (`P2D`) a pantalla completa, configura el lienzo
 * virtual pixel-perfect `480x270`, sincroniza la base de datos SQLite (3FN) y el repositorio
 * de canciones personalizadas, y lanza la escena inicial (`InitScene`).
 */
public class Main extends PApplet {

	/**
	 * Lanza la aplicacion Processing procesando flags opcionales de linea de comandos (`--debug`, `-d`).
	 */
	public static void main(String[] args) {
		for (String arg : args) {
			if (arg.equals("--debug") || arg.equals("-d")) {
				Global.DEBUG_MODE = true;
			}
		}
		PApplet.main("game.Main");
	}

	/**
	 * Configura el renderizador nativo antes de que Processing construya la ventana del sistema.
	 * <p>
	 * Nota de arquitectura Processing: {@code fullScreen(PConstants.P2D)} y {@code noSmooth()}
	 * deben invocarse obligatoriamente dentro de {@code settings()} (y nunca en {@code setup()})
	 * porque Processing instancia la superficie OpenGL (`P2D`) y bloquea el muestreo de texturas
	 * (nearest-neighbor para pixel art nitido) inmediatamente al salir de este metodo.
	 */
	public void settings() {
		Config.preloadWindowSize(".config");
		fullScreen(PConstants.P2D);
		noSmooth();
	}

	/**
	 * Inicializa el motor (`Engine`), la configuracion de audio/video/controles, las fuentes
	 * monoespaciadas de dialogos, la base de datos SQLite y la escena de titulo.
	 */
	public void setup() {
		surface.setTitle("Combo 6Beat");

		Engine.init(this);
		Engine.resizeVirtualScreen((int) BASE_W, (int) BASE_H);
		Engine.setFramesToMonitorHZ();

		Config.loadConfig(".config");
		Config.windowWidth = width;
		Config.windowHeight = height;
		Config.applyAudioConfig();
		Config.applyVideoConfig();

		setFonts();

		Config.syncDialogKeys();

		DatabaseManager.init();
		CustomSongRepository.syncCustomSongsFolder();
		GameState.init();

		Engine.changeScene(new InitScene());
	}

	/**
	 * Ciclo de dibujo de Processing (delegado internamente al `Engine` mediante hooks de `PApplet`).
	 */
	public void draw() {}

	/**
	 * Intercepta la tecla `ESC` nativa de Processing para evitar que cierre la aplicacion
	 * bruscamente y permitir que las escenas gestionen pausas y retrocesos de menu.
	 */
	public void keyPressed() {
		if (key == ESC) {
			key = 0;
		}
	}

	/**
	 * Configura la tipografia monoespaciada para los cuadros de dialogo (`GameDialog`),
	 * priorizando `0xProto Nerd Font Mono` si esta instalada en el sistema.
	 */
	private void setFonts() {
		String fontName = "Monospaced";
		for (String available : PFont.list()) {
			if ("0xProto Nerd Font Mono".equals(available)) {
				fontName = "0xProto Nerd Font Mono";
				break;
			}
		}

		GameDialog.descriptionFont = createFont(fontName, 48, true);
		GameDialog.descriptionSize = 10;
		GameDialog.titleFont = createFont(fontName, 96, true);
		GameDialog.titleSize = 15;
	}
}
