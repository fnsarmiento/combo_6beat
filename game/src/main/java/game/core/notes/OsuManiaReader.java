package game.core.notes;

import static processing.core.PApplet.constrain;
import static processing.core.PApplet.parseFloat;
import static processing.core.PApplet.parseInt;
import static processing.core.PApplet.split;

import engine.Engine;
import engine.ResourcesManager;
import engine.audio.Audio;
import engine.audio.MusicTrack;
import engine.draw.Sprite2D;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class OsuManiaReader {
	private final String filepath;
	private final long[] notes;
	private final long[] timingPoints;
	private final Sprite2D[] sprites;
	private final int[] spritesTimes;
	private MusicTrack song;

	// Metadatos generales del beatmap .osu
	private String title = "";
	private String artist = "";
	private String creator = "";
	private String difficulty = "6K";
	private String audioFilename = "";
	private String audioAbsPath = "";
	private String coverAbsPath = "";
	private float primaryBpm = 120.0f;

	// Metadatos opcionales de jefe dentro de sección [Combo6Beat] en el .osu
	private boolean hasBossSection = false;
	private String bossName = "";
	private String bossSpecies = "";
	private int bossMaxHp = 1500;
	private String bossSprite = "";
	private int bossRows = 1;
	private int bossCols = 1;
	private int bossAttackDamage = 12;
	private int bossHitDamage = 6;
	private String returnScene = "";
	private String defeatFlag = "";
	private String rewardItem = "";

	public OsuManiaReader(String filepath) {
		this(filepath, true);
	}

	public OsuManiaReader(String filepath, boolean loadMedia) {
		this.filepath = filepath;
		String[] lines = readFileLines(filepath);
		String header = "";

		ArrayList<Long> tempNotes = new ArrayList<>();
		ArrayList<Long> tempTimingPoints = new ArrayList<>();
		ArrayList<Sprite2D> tempSprites = new ArrayList<>();
		ArrayList<Integer> tempSpritesTime = new ArrayList<>();

		if (lines != null) {
			for (String s : lines) {
				String line = s.trim();
				if (line.isEmpty() || line.startsWith("//")) continue;

				if (line.startsWith("[") && line.endsWith("]")) {
					header = line;
					continue;
				}

				switch (header) {
					case "[General]"      -> processGeneral(line, loadMedia);
					case "[Metadata]"     -> processMetadata(line);
					case "[TimingPoints]" -> processTimingPoints(line, tempTimingPoints);
					case "[HitObjects]"   -> processHitObjects(line, tempNotes);
					case "[Events]"       -> processEvents(line, tempSprites, tempSpritesTime, loadMedia);
					case "[Combo6Beat]"   -> processCombo6Beat(line);
				}
			}
		}

		this.notes = tempNotes.stream().mapToLong(Long::longValue).toArray();
		this.timingPoints = tempTimingPoints.stream().mapToLong(Long::longValue).toArray();
		this.sprites = tempSprites.toArray(new Sprite2D[0]);
		this.spritesTimes = tempSpritesTime.stream().mapToInt(Integer::intValue).toArray();
		this.primaryBpm = computePrimaryBpm(this.timingPoints, this.notes);
	}

	private static String[] readFileLines(String path) {
		if (path == null || path.isEmpty()) return null;
		if (Engine.getApp() != null) {
			String[] loaded = Engine.getApp().loadStrings(path);
			if (loaded != null) return loaded;
		}
		try {
			return Files.readAllLines(Path.of(path), StandardCharsets.UTF_8).toArray(new String[0]);
		} catch (Exception e) {
			try {
				return Files.readAllLines(Path.of(path), StandardCharsets.ISO_8859_1).toArray(new String[0]);
			} catch (Exception ignored) {
				return null;
			}
		}
	}

	private void processMetadata(String line) {
		int colonIdx = line.indexOf(':');
		if (colonIdx == -1) return;
		String key = line.substring(0, colonIdx).trim();
		String val = line.substring(colonIdx + 1).trim();
		switch (key) {
			case "Title"   -> title = val;
			case "Artist"  -> artist = val;
			case "Creator" -> creator = val;
			case "Version" -> { if (!val.isEmpty()) difficulty = val; }
		}
	}

	private void processGeneral(String line, boolean loadMedia) {
		int colonIdx = line.indexOf(':');
		if (colonIdx == -1) return;

		String key = line.substring(0, colonIdx).trim();
		String val = line.substring(colonIdx + 1).trim();

		if (key.equals("AudioFilename")) {
			audioFilename = val;
			Path osuPath = Path.of(filepath).toAbsolutePath().normalize();
			Path parentDir = osuPath.getParent();
			if (parentDir != null) {
				File localAudio = parentDir.resolve(val).normalize().toFile();
				if (localAudio.exists()) {
					audioAbsPath = localAudio.getAbsolutePath();
					if (loadMedia) {
						song = Audio.loadBGM(audioAbsPath);
					}
					return;
				}
			}
			String resAbs = ResourcesManager.getResourceAbsPath(val);
			if (new File(resAbs).exists()) {
				audioAbsPath = resAbs;
			}
			if (loadMedia) {
				song = ResourcesManager.loadBGM(val);
			}
		}
	}

	private void processCombo6Beat(String line) {
		int colonIdx = line.indexOf(':');
		if (colonIdx == -1) return;

		hasBossSection = true;
		String key = line.substring(0, colonIdx).trim();
		String val = line.substring(colonIdx + 1).trim();

		switch (key) {
			case "BossName"         -> bossName = val;
			case "BossSpecies"      -> bossSpecies = val;
			case "BossMaxHP"        -> bossMaxHp = Math.max(10, parseInt(val, 400));
			case "BossSprite"       -> bossSprite = val;
			case "BossRows"         -> bossRows = Math.max(1, parseInt(val, 1));
			case "BossCols"         -> bossCols = Math.max(1, parseInt(val, 1));
			case "BossAttackDamage" -> bossAttackDamage = Math.max(1, parseInt(val, 12));
			case "BossHitDamage"    -> bossHitDamage = Math.max(1, parseInt(val, 6));
			case "ReturnScene"      -> returnScene = val;
			case "DefeatFlag"       -> defeatFlag = val;
			case "RewardItem"       -> rewardItem = val;
		}
	}

	private void processTimingPoints(String line, ArrayList<Long> list) {
		String[] data = split(line, ",");
		if (data.length < 2) return;

		int time = (int) parseFloat(data[0]);
		float beatLength = parseFloat(data[1]);
		boolean isUninherited = (data.length >= 7) ? (parseInt(data[6]) == 1) : (beatLength > 0);

		list.add(TimingPointPacker.pack(time, isUninherited, beatLength));
	}

	private void processHitObjects(String line, ArrayList<Long> list) {
		String[] data = split(line, ",");
		if (data.length < 4) return;

		int x = parseInt(data[0]);
		// Fórmula estándar de osu!mania para convertir X (0..512) a carril (0..5)
		byte lane = (byte) constrain((int) (x * 6 / 512.0f), 0, 5);

		int time = parseInt(data[2]);
		int type = parseInt(data[3]);
		int hitSound = (data.length >= 5) ? parseInt(data[4]) : 0;

		boolean isHold = (type & 128) != 0;
		int duration = 0;

		if (isHold && data.length >= 6) {
			String[] holdParams = split(data[5], ":");
			int endTime = parseInt(holdParams[0]);
			duration = Math.max(0, endTime - time);
		}

		byte noteType = NotePacker.NOTE_NORMAL;
		list.add(NotePacker.pack(lane, noteType, time, duration));
	}

	private void processEvents(
		String line,
		ArrayList<Sprite2D> list,
		ArrayList<Integer> tempSpritesTime,
		boolean loadMedia
	) {
		// Evento estándar de fondo/portada en .osu: 0,0,"cover.jpg",0,0
		if (line.startsWith("0,")) {
			String[] parts = split(line, ",");
			if (parts.length >= 3) {
				String bgRel = parts[2].trim().replace("\"", "");
				if (!bgRel.isEmpty() && coverAbsPath.isEmpty()) {
					Path osuPath = Path.of(filepath).toAbsolutePath().normalize();
					Path parentDir = osuPath.getParent();
					if (parentDir != null) {
						File bgFile = parentDir.resolve(bgRel).normalize().toFile();
						if (bgFile.exists() && bgFile.isFile()) {
							coverAbsPath = bgFile.getAbsolutePath();
						}
					}
				}
			}
			return;
		}

		String[] split = split(line, ":");
		if (split.length >= 5 && split[0].equals("0")) {
			int time = parseInt(split[1]);
			String path = split[2].replace("\"", "");
			if (!loadMedia) return;

			Sprite2D spr = new Sprite2D(path, -100000);
			spr.position.x = parseInt(split[3]);
			spr.position.y = parseInt(split[4]);
			spr.visible = false;

			list.add(spr);
			tempSpritesTime.add(time);
		}
	}

	private static float computePrimaryBpm(long[] tps, long[] notes) {
		if (tps == null || tps.length == 0) return 120.0f;

		ArrayList<long[]> uninherited = new ArrayList<>();
		for (long tp : tps) {
			if (!TimingPointPacker.isUninherited(tp)) continue;
			float beatLength = TimingPointPacker.getBeatLength(tp);
			if (beatLength > 0.0f) {
				uninherited.add(new long[] { TimingPointPacker.getTime(tp), Float.floatToIntBits(beatLength) });
			}
		}
		if (uninherited.isEmpty()) return 120.0f;
		if (uninherited.size() == 1) {
			double bpm = 60000.0 / Float.intBitsToFloat((int) uninherited.get(0)[1]);
			return (float) (Math.round(bpm * 100.0) / 100.0);
		}

		long songEndMs = 0L;
		if (notes != null) {
			for (long n : notes) {
				long end = NotePacker.getStartTime(n) + NotePacker.getDuration(n);
				if (end > songEndMs) songEndMs = end;
			}
		}
		long lastTpTime = uninherited.get(uninherited.size() - 1)[0];
		if (songEndMs <= lastTpTime) songEndMs = lastTpTime + 30000L;

		Map<Integer, Long> durationByBpmCents = new LinkedHashMap<>();
		for (int i = 0; i < uninherited.size(); i++) {
			long start = uninherited.get(i)[0];
			long end = (i + 1 < uninherited.size()) ? uninherited.get(i + 1)[0] : songEndMs;
			long span = Math.max(1L, end - start);
			float bl = Float.intBitsToFloat((int) uninherited.get(i)[1]);
			double bpm = 60000.0 / bl;
			int cents = (int) Math.round(bpm * 100.0);
			if (cents > 0) {
				durationByBpmCents.merge(cents, span, Long::sum);
			}
		}

		int bestCents = 12000;
		long bestSpan = -1L;
		for (Map.Entry<Integer, Long> entry : durationByBpmCents.entrySet()) {
			if (entry.getValue() > bestSpan) {
				bestSpan = entry.getValue();
				bestCents = entry.getKey();
			}
		}
		return bestCents / 100.0f;
	}

	public long[] getNotes() { return notes; }
	public long[] getTimingPoints() { return timingPoints; }
	public Sprite2D[] getSprites() { return sprites; }
	public int[] getSpritesTime() { return spritesTimes; }
	public MusicTrack getSong() { return song; }
	public int getNoteCount() { return notes != null ? notes.length : 0; }

	public String getTitle() { return title; }
	public String getArtist() { return artist; }
	public String getCreator() { return creator; }
	public String getDifficulty() { return difficulty; }
	public String getAudioFilename() { return audioFilename; }
	public String getAudioAbsPath() { return audioAbsPath; }
	public String getCoverAbsPath() { return coverAbsPath; }
	public float getPrimaryBpm() { return primaryBpm; }

	public boolean hasBossSection() { return hasBossSection; }
	public String getBossName() { return bossName; }
	public String getBossSpecies() { return bossSpecies; }
	public int getBossMaxHp() { return bossMaxHp; }
	public String getBossSprite() { return bossSprite; }
	public int getBossRows() { return bossRows; }
	public int getBossCols() { return bossCols; }
	public int getBossAttackDamage() { return bossAttackDamage; }
	public int getBossHitDamage() { return bossHitDamage; }
	public String getReturnScene() { return returnScene; }
	public String getDefeatFlag() { return defeatFlag; }
	public String getRewardItem() { return rewardItem; }
}
