package engine.audio;

import java.io.File;
import java.util.Locale;

/**
 * Conversor automático de audio comprimido (.mp3 / .ogg / .flac) a WAV PCM 16-bit 44.1kHz
 * para compatibilidad con javax.sound.sampled.Clip antes de iniciar una canción.
 */
public class AudioConverter {

	/**
	 * Devuelve un archivo .wav listo para reproducir.
	 * Si sourceFile ya es .wav o ya existe un .wav hermano, lo reutiliza al instante.
	 * Si es .mp3/.ogg y falta el .wav, intenta convertirlo automáticamente con las herramientas
	 * disponibles del sistema (ffmpeg, mpg123, gst-launch-1.0, oggdec).
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

	private static boolean runConverter(File outWav, String... cmd) {
		try {
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);
			Process proc = pb.start();
			// Consumir salida para evitar bloqueos de buffer
			try (var is = proc.getInputStream()) {
				is.transferTo(java.io.OutputStream.nullOutputStream());
			}
			int code = proc.waitFor();
			if (code == 0 && outWav.exists() && outWav.length() > 44) {
				return true;
			}
		} catch (Exception ignored) {
			// Herramienta no instalada o no disponible: probar la siguiente
		}
		if (outWav.exists() && outWav.length() <= 44) {
			outWav.delete();
		}
		return false;
	}
}
