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

public class Main extends PApplet {

	public static void main(String[] args) {
		for (String arg : args) {
			if (arg.equals("--debug") || arg.equals("-d")) {
				Global.DEBUG_MODE = true;
			}
		}
		PApplet.main("game.Main");
	}

	public void settings() {
		Config.preloadWindowSize(".config");
		size(displayWidth, displayHeight, PConstants.P2D);
		noSmooth();
	}

	public void setup() {
		surface.setTitle("Combo 6Beat");
		enableBorderlessFullscreen();

		Engine.init(this);
		Engine.resizeVirtualScreen((int) BASE_W, (int) BASE_H);
		Engine.setFramesToMonitorHZ();

		Config.loadConfig(".config");
		Config.windowWidth = displayWidth;
		Config.windowHeight = displayHeight;
		Config.applyAudioConfig();
		Config.applyVideoConfig();

		setFonts();

		Config.syncDialogKeys();

		DatabaseManager.init();
		CustomSongRepository.syncCustomSongsFolder();
		GameState.init();

		Engine.changeScene(new InitScene());
	}

	public void draw() {}

	private void enableBorderlessFullscreen() {
		surface.setResizable(false);
		Object nativeWin = surface.getNative();
		if (nativeWin != null) {
			try {
				nativeWin.getClass().getMethod("setUndecorated", boolean.class).invoke(nativeWin, true);
			} catch (Exception ignored) {}
			try {
				nativeWin.getClass().getMethod("setPosition", int.class, int.class).invoke(nativeWin, 0, 0);
			} catch (Exception ignored) {}
			try {
				nativeWin.getClass().getMethod("setSize", int.class, int.class).invoke(nativeWin, displayWidth, displayHeight);
			} catch (Exception ignored) {}
		}
		surface.setLocation(0, 0);
		surface.setSize(displayWidth, displayHeight);
	}

	public void keyPressed() {
		if (key == ESC) {
			key = 0;
		}
	}

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
