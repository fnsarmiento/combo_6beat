package game.core.notes;

public class NotePacker {
	public static final byte NOTE_NORMAL        = 0;
	public static final byte NOTE_RANGED_ORIGIN = 1; // Ataque a distancia (caballero/pajaro) con indicador de origen
	public static final byte NOTE_SHIFT_UP      = 2; // Cambio de trayectoria hacia arriba
	public static final byte NOTE_SHIFT_DOWN    = 3; // Cambio de trayectoria hacia abajo

	private static final int BITS_LANE      = 3;
	private static final int BITS_NOTE_TYPE = 2;
	private static final int BITS_TIME      = 29;
	private static final int BITS_DURATION  = 30;

	private static final long MASK_LANE      = (1L << BITS_LANE) - 1;
	private static final long MASK_NOTE_TYPE = (1L << BITS_NOTE_TYPE) - 1;
	private static final long MASK_TIME      = (1L << BITS_TIME) - 1;
	private static final long MASK_DURATION  = (1L << BITS_DURATION) - 1;

	private static final int SHIFT_LANE      = 0;
	private static final int SHIFT_NOTE_TYPE = BITS_LANE;
	private static final int SHIFT_TIME      = SHIFT_NOTE_TYPE + BITS_NOTE_TYPE;
	private static final int SHIFT_DURATION  = SHIFT_TIME + BITS_TIME;

	public static long pack(byte lane, boolean isPressed, int startTime, int duration) {
		return pack(lane, isPressed ? NOTE_RANGED_ORIGIN : NOTE_NORMAL, startTime, duration);
	}

	public static long pack(byte lane, byte noteType, int startTime, int duration) {
		long packed = 0L;

		packed |= ((long) lane & MASK_LANE) << SHIFT_LANE;
		packed |= ((long) noteType & MASK_NOTE_TYPE) << SHIFT_NOTE_TYPE;
		packed |= ((long) startTime & MASK_TIME) << SHIFT_TIME;
		packed |= ((long) duration & MASK_DURATION) << SHIFT_DURATION;

		return packed;
	}

	public static int getLane(long packed) {
		return (int) ((packed >>> SHIFT_LANE) & MASK_LANE);
	}

	public static byte getNoteType(long packed) {
		return (byte) ((packed >>> SHIFT_NOTE_TYPE) & MASK_NOTE_TYPE);
	}

	public static boolean isPressed(long packed) {
		return getNoteType(packed) != NOTE_NORMAL;
	}

	public static long getStartTime(long packed) {
		return (packed >>> SHIFT_TIME) & MASK_TIME;
	}

	public static long getDuration(long packed) {
		return (packed >>> SHIFT_DURATION) & MASK_DURATION;
	}
}
