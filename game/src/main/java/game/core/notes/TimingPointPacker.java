package game.core.notes;

public class TimingPointPacker {
	private static final int BITS_TIME = 30;
	private static final int BITS_UNINHERITED = 1;
	//private static final int BITS_VALUE = 32;

	private static final long MASK_TIME = (1L << BITS_TIME) - 1;
	private static final long MASK_UNINHERITED = (1L << BITS_UNINHERITED) - 1;
	private static final long MASK_VALUE = 0xFFFFFFFFL;

	private static final int SHIFT_TIME = 0;
	private static final int SHIFT_UNINHERITED = SHIFT_TIME + BITS_TIME; // 30
	private static final int SHIFT_VALUE = SHIFT_UNINHERITED + BITS_UNINHERITED; // 31

	public static long pack(int timeMs, boolean isUninherited, float beatLength) {
		int valueBits = Float.floatToIntBits(beatLength);

		return (
				((long) timeMs & MASK_TIME) << SHIFT_TIME)
				| (((isUninherited ? 1L : 0L) & MASK_UNINHERITED) << SHIFT_UNINHERITED)
				| (((long) valueBits & MASK_VALUE) << SHIFT_VALUE
		);
	}

	public static int getTime(long packed) {
		return (int) ((packed >>> SHIFT_TIME) & MASK_TIME);
	}

	public static boolean isUninherited(long packed) {
		return ((packed >>> SHIFT_UNINHERITED) & MASK_UNINHERITED) == 1L;
	}

	public static float getBeatLength(long packed) {
		int bits = (int) ((packed >>> SHIFT_VALUE) & MASK_VALUE);
		return Float.intBitsToFloat(bits);
	}

	public static double getBpm(long packed) {
		return 60000.0 / getBeatLength(packed);
	}

	public static double getSvMultiplier(long packed) {
		return -100.0 / getBeatLength(packed);
	}
}