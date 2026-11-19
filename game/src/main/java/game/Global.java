package game;

import engine.Engine;

public class Global {
	public static boolean DEBUG_MODE = true;

	public static final float BASE_W = 480.0f;
	public static final float BASE_H = 270.0f;

	public static float resizeWidth(float val) {
		return (val / BASE_W) * Engine.getApp().width;
	}
	
	public static float resizeHeight(float val) {
		return (val / BASE_H) * Engine.getApp().height;
	}
}
