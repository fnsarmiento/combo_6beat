package game;

import engine.Engine;

/**
 * Constantes globales de resolucion virtual, recursos compartidos y banderas de ejecucion
 * de Combo 6Beat.
 */
public class Global {

	// =========================================================================
	// DEPURACION
	// =========================================================================

	/** Habilita superposiciones de diagnostico, atajos de testeo y logs extendidos. */
	public static boolean DEBUG_MODE = true;

	// =========================================================================
	// PANTALLA Y RESOLUCION VIRTUAL PIXEL-ART
	// =========================================================================

	/** Ancho del lienzo virtual interno en pixeles (relacion de aspecto 16:9). */
	public static final float BASE_W = 480.0f;

	/** Alto del lienzo virtual interno en pixeles (relacion de aspecto 16:9). */
	public static final float BASE_H = 270.0f;

	/** Escala una medida horizontal del lienzo virtual `480x270` al ancho real de ventana. */
	public static float resizeWidth(float val) {
		return (val / BASE_W) * Engine.getApp().width;
	}

	/** Escala una medida vertical del lienzo virtual `480x270` al alto real de ventana. */
	public static float resizeHeight(float val) {
		return (val / BASE_H) * Engine.getApp().height;
	}

	// =========================================================================
	// AUDIO Y MANIFIESTOS DE CANCIONES
	// =========================================================================

	/** Musica de fondo persistida sin cortes entre el Menu Principal (`InitScene`) y el Hub (`HubScene`). */
	public static final String MENU_HUB_BGM = "resources/sfx/XeoN - Odysseus.wav";

	/** Ruta relativa al manifiesto XML maestro con todos los packs y charts `.osu` incluidos. */
	public static final String SONGS_MANIFEST = "resources/songs/songs.xml";
}
