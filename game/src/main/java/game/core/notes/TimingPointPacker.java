package game.core.notes;

/**
 * Empaquetador de puntos de tiempo ({@code [TimingPoints]} de osu!mania) en un {@code long} de 64 bits.
 *
 * <p>Distribución exacta de bits dentro del {@code long}:
 * <pre>
 *  [63] [62 ........................ 31] [30] [29 ....................... 0]
 *  |0 | | Float.floatToIntBits(bl) (32b)| |un| |        timeMs (30b)        |
 * </pre>
 * <ul>
 *   <li><b>Bits {@code [0..29]} (30 bits, {@code SHIFT_TIME = 0}):</b> Marca temporal {@code timeMs} en milisegundos.</li>
 *   <li><b>Bit {@code [30]} (1 bit, {@code SHIFT_UNINHERITED = 30}):</b> Bandera {@code isUninherited}
 *       ({@code 1} = punto rojo no heredado que fija BPM real; {@code 0} = punto verde heredado que escala SV).</li>
 *   <li><b>Bits {@code [31..62]} (32 bits, {@code SHIFT_VALUE = 31}):</b> Valor IEEE-754 de {@code beatLength}
 *       convertido vía {@link Float#floatToIntBits(float)}.</li>
 * </ul>
 */
public class TimingPointPacker {

	// =========================================================================
	// Tamaños de Campo, Máscaras y Desplazamientos de Bits
	// =========================================================================

	private static final int BITS_TIME = 30;
	private static final int BITS_UNINHERITED = 1;

	private static final long MASK_TIME = (1L << BITS_TIME) - 1;
	private static final long MASK_UNINHERITED = (1L << BITS_UNINHERITED) - 1;
	private static final long MASK_VALUE = 0xFFFFFFFFL;

	private static final int SHIFT_TIME = 0;
	private static final int SHIFT_UNINHERITED = SHIFT_TIME + BITS_TIME; // Bit 30
	private static final int SHIFT_VALUE = SHIFT_UNINHERITED + BITS_UNINHERITED; // Bits 31..62

	// =========================================================================
	// Empaquetado y Extracción de Campos
	// =========================================================================

	public static long pack(int timeMs, boolean isUninherited, float beatLength) {
		int valueBits = Float.floatToIntBits(beatLength);

		return (((long) timeMs & MASK_TIME) << SHIFT_TIME)
			| (((isUninherited ? 1L : 0L) & MASK_UNINHERITED) << SHIFT_UNINHERITED)
			| (((long) valueBits & MASK_VALUE) << SHIFT_VALUE);
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

	/**
	 * Calcula el BPM instantáneo para un punto de tiempo no heredado ({@code 60000.0 / beatLength}).
	 */
	public static double getBpm(long packed) {
		return 60000.0 / getBeatLength(packed);
	}

	/**
	 * Calcula el multiplicador de velocidad de scroll (SV) para un punto heredado ({@code -100.0 / beatLength}).
	 */
	public static double getSvMultiplier(long packed) {
		return -100.0 / getBeatLength(packed);
	}
}
