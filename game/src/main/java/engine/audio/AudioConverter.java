package engine.audio;

import java.io.File;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Conversor automático de audio comprimido ({@code .mp3}, {@code .ogg}, {@code .flac})
 * a WAV PCM 16-bit 44.1 kHz estéreo para garantizar compatibilidad con {@link javax.sound.sampled.Clip}.
 */
public class AudioConverter {

	// =========================================================================
	// Conversión bajo Demanda a WAV PCM
	// =========================================================================

	/**
	 * Devuelve un archivo {@code .wav} listo para reproducir.
	 *
	 * <ul>
	 *   <li>Si {@code sourceFile} ya es {@code .wav} o ya existe un {@code .wav} hermano válido,
	 *       lo reutiliza sin reconvertir.</li>
	 *   <li>Si falta el {@code .wav}, prueba secuencialmente con herramientas del sistema
	 *       ({@code ffmpeg}, {@code mpg123}, {@code gst-launch-1.0}, {@code oggdec}).</li>
	 * </ul>
	 */
	public static File ensureWav(File sourceFile) {
		if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
			return sourceFile;
		}

		String name = sourceFile.getName();
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".wav")) {
			return sourceFile;
		}

		int dotIdx = name.lastIndexOf('.');
		String baseName = (dotIdx > 0) ? name.substring(0, dotIdx) : name;
		File wavFile = new File(sourceFile.getParentFile(), baseName + ".wav");
		if (wavFile.exists() && wavFile.length() > 44) {
			return wavFile;
		}

		String src = sourceFile.getAbsolutePath();
		String dst = wavFile.getAbsolutePath();

		// 1. Intentar ffmpeg (universal en Linux/Windows/macOS)
		if (runConverter(wavFile, "ffmpeg", "-y", "-i", src, "-acodec", "pcm_s16le", "-ar", "44100", "-ac", "2", dst)) {
			return wavFile;
		}

		// 2. Si es MP3, intentar mpg123
		if (lower.endsWith(".mp3")) {
			if (runConverter(wavFile, "mpg123", "-q", "-w", dst, src)) {
				return wavFile;
			}
		}

		// 3. Intentar GStreamer (soporta .ogg, .mp3, .flac, .m4a en Linux)
		if (runConverter(
			wavFile,
			"gst-launch-1.0", "-q",
			"filesrc", "location=" + src,
			"!", "decodebin",
			"!", "audioconvert",
			"!", "audioresample",
			"!", "audio/x-raw,format=S16LE,channels=2,rate=44100",
			"!", "wavenc",
			"!", "filesink", "location=" + dst
		)) {
			return wavFile;
		}

		// 4. Si es OGG, intentar oggdec
		if (lower.endsWith(".ogg")) {
			if (runConverter(wavFile, "oggdec", "-Q", "-o", dst, src)) {
				return wavFile;
			}
		}

		System.err.println("No se pudo convertir automáticamente a WAV: " + src);
		return sourceFile;
	}

	// =========================================================================
	// Ejecución de Procesos Externos de Conversión
	// =========================================================================

	private static boolean runConverter(File outWav, String... cmd) {
		try {
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);
			Process proc = pb.start();
			// Consumir salida estándar/error para evitar bloqueos por llenado de pipe
			try (var is = proc.getInputStream()) {
				is.transferTo(OutputStream.nullOutputStream());
			}
			int code = proc.waitFor();
			if (code == 0 && outWav.exists() && outWav.length() > 44) {
				return true;
			}
		} catch (Exception ignored) {
			// Herramienta no instalada o no disponible: probar la siguiente alternativa
		}
		if (outWav.exists() && outWav.length() <= 44) {
			outWav.delete();
		}
		return false;
	}
}
