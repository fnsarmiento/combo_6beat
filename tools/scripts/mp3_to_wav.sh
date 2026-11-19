#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Uso: $0 <ruta_a_carpeta>"
    exit 1
fi

DIR="$1"

if [ ! -d "$DIR" ]; then
    echo "Error: '$DIR' no es un directorio válido."
    exit 1
fi

if ! command -v ffmpeg >/dev/null 2>&1; then
    echo "Error: 'ffmpeg' no está instalado o no está en el PATH."
    exit 1
fi

shopt -s nullglob
converted=0

for mp3 in "$DIR"/*.mp3 "$DIR"/*.MP3; do
    base="${mp3%.*}"
    wav="${base}.wav"
    mp3_name="$(basename "$mp3")"
    wav_name="$(basename "$wav")"

    if [ -f "$wav" ]; then
        echo "Ya existe '$wav_name', se omite '$mp3_name'."
        continue
    fi

    echo "Convirtiendo '$mp3_name' -> '$wav_name'..."
    ffmpeg -y -loglevel error -i "$mp3" -acodec pcm_s16le -ar 44100 "$wav"
    converted=$((converted + 1))

    # Actualizar AudioFilename en archivos .osu de la misma carpeta si apuntaban al .mp3
    for osu in "$DIR"/*.osu; do
        if grep -q "^AudioFilename:[[:space:]]*${mp3_name}[[:space:]]*$" "$osu"; then
            sed -i "s|^AudioFilename:[[:space:]]*${mp3_name}[[:space:]]*$|AudioFilename: ${wav_name}|" "$osu"
            echo "Actualizado AudioFilename en '$(basename "$osu")' -> '$wav_name'."
        fi
    done
done

echo "Listo. Archivos convertidos: $converted."
