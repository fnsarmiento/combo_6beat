package game;

import engine.Engine;

public class Global {
	public static boolean DEBUG_MODE = true;

	public static final float BASE_W = 480.0f;
	public static final float BASE_H = 270.0f;

	// Música de fondo compartida entre Menú Principal y Hub (continúa sin cortarse)
	public static final String MENU_HUB_BGM = "resources/sfx/XeoN - Odysseus.wav";
	// Archivo padre con todos los packs y dificultades .osu base
	public static final String SONGS_MANIFEST = "resources/songs/songs.xml";

	public static float resizeWidth(float val) {
		return (val / BASE_W) * Engine.getApp().width;
	}
	
	public static float resizeHeight(float val) {
		return (val / BASE_H) * Engine.getApp().height;
	}
}
