#!/usr/bin/env python3
"""
Importador unificado de escenas exportadas desde PixelLab Map Editor
(map.json + objects/manifest.json + map-composite.png) hacia un archivo XML de escena
de Combo 6Beat, con precisión de sub-cuadrante Wang (16x16 px) y soporte para ser invocado
directamente desde level_editor ("Importar JSON").
"""

import argparse
import hashlib
import json
import pathlib
import shutil
import zipfile
from PIL import Image
import numpy as np


PRESETS = {
    "beach": {
        "asset_folder": "beach",
        "crop": (76, 183, 58, 162),
        "spawn": (1344.0, 2464.0),
        "ts_names": ["ocean_sand.png", "sand_temple.png", "temple_wall.png"],
        "water_ts_idx": 0,
        "water_tile_idx": 6,
        "land_tile_idx": 12,
        "has_walls": True,
        "dialogs": "dialogs/debug.json",
        "entities_xml": """    <Player id="player" group="Player" speed="92.00" type="Struct">
        <Body id="player_body" group="Player" position-x="1344" position-y="2464" width="12" height="12" layer="1" mask="1" disabled="0" type="Box" />
        <Sprite id="player_sprite" group="Player" path="sprites/player/The Female Adventurer - Free/Idle/Idle_Down.png" position-x="1350" position-y="2470" z-index="150" rows="1" cols="8" scale-x="1.00" scale-y="1.00" rotation="0.00" visible="1" delay="0.10" center-position="1" type="Sprite2D" />
    </Player>
    <NPC id="npc_ruins_guardian" group="NPCs" dialogId="__start__" type="GameObject">
        <Body id="npc_ruins_guardian_body" group="NPCs" position-x="1344" position-y="1984" width="32" height="32" layer="1" mask="0" disabled="0" type="Box" />
        <Area id="npc_ruins_guardian_area" group="NPCs" position-x="1338" position-y="1978" width="44" height="44" layer="0" mask="2" disabled="0" type="Box" />
        <Sprite id="npc_ruins_guardian_sprite" group="NPCs" path="sprites/bosses/bear.png" position-x="1360" position-y="1988" z-index="150" rows="3" cols="3" scale-x="1.00" scale-y="1.00" width="48" height="48" rotation="0.00" visible="1" delay="0.14" center-position="1" type="Sprite2D" />
    </NPC>
    <NPC id="npc_ruins_scribe" group="NPCs" dialogId="npc_gift" type="GameObject">
        <Body id="npc_ruins_scribe_body" group="NPCs" position-x="1248" position-y="2432" width="32" height="32" layer="1" mask="0" disabled="0" type="Box" />
        <Area id="npc_ruins_scribe_area" group="NPCs" position-x="1242" position-y="2426" width="44" height="44" layer="0" mask="2" disabled="0" type="Box" />
        <Sprite id="npc_ruins_scribe_sprite" group="NPCs" path="sprites/npcs/cat.png" position-x="1264" position-y="2448" z-index="150" rows="2" cols="3" scale-x="1.00" scale-y="1.00" width="16" height="16" rotation="0.00" visible="1" delay="0.22" center-position="1" type="Sprite2D" />
    </NPC>
    <Chest id="chest_ruins_1" group="chests" itemId="potion_medium" itemCount="1" requiredItem="" dialogId="" opened="0" type="Struct">
        <Body id="chest_ruins_1_body" group="chests" position-x="1440" position-y="2432" width="28" height="24" layer="1" mask="0" disabled="0" type="Box" />
        <Area id="chest_ruins_1_area" group="chests" position-x="1432" position-y="2424" width="44" height="40" layer="0" mask="2" disabled="0" type="Box" />
    </Chest>
    <SceneChanger id="changer_to_swamp" group="triggers" targetScene="resources/scenes/swamp.xml" spawnX="960.0" spawnY="864.0" triggerOnTouch="0" requiredItem="" requiredFlag="" type="Struct">
        <Area id="changer_to_swamp_area" group="triggers" position-x="1408" position-y="2496" width="36" height="36" layer="0" mask="2" disabled="0" type="Box" />
    </SceneChanger>
    <SceneChanger id="changer_to_a" group="triggers" targetScene="resources/scenes/a.xml" spawnX="180.0" spawnY="190.0" triggerOnTouch="0" requiredItem="" requiredFlag="" type="Struct">
        <Area id="changer_to_a_area" group="triggers" position-x="1152" position-y="2464" width="36" height="36" layer="0" mask="2" disabled="0" type="Box" />
    </SceneChanger>
    <SceneChanger id="changer_exit_sanctum" group="triggers" targetScene="resources/scenes/a.xml" spawnX="180.0" spawnY="190.0" triggerOnTouch="0" requiredItem="key_exit" requiredFlag="BOSS_RABY_DEFEATED" type="Struct">
        <Area id="changer_exit_sanctum_area" group="triggers" position-x="1728" position-y="1312" width="64" height="36" layer="0" mask="2" disabled="0" type="Box" />
    </SceneChanger>""",
    },
    "swamp": {
        "asset_folder": "swamp",
        "crop": (70, 190, 90, 185),
        "spawn": (960.0, 864.0),
        "ts_names": ["swamp_water_turf.png", "swamp_water_mud.png", "swamp_path_turf.png"],
        "water_ts_idx": 1,
        "water_tile_idx": 12,
        "land_tile_idx": 6,
        "has_walls": False,
        "dialogs": "dialogs/debug.json",
        "entities_xml": """    <Player id="player" group="Player" speed="92.00" type="Struct">
        <Body id="player_body" group="Player" position-x="960" position-y="864" width="12" height="12" layer="1" mask="1" disabled="0" type="Box" />
        <Sprite id="player_sprite" group="Player" path="sprites/player/The Female Adventurer - Free/Idle/Idle_Down.png" position-x="966" position-y="870" z-index="150" rows="1" cols="8" scale-x="1.00" scale-y="1.00" rotation="0.00" visible="1" delay="0.10" center-position="1" type="Sprite2D" />
    </Player>
    <NPC id="npc_swamp_guardian" group="NPCs" dialogId="__start__" type="GameObject">
        <Body id="npc_swamp_guardian_body" group="NPCs" position-x="2208" position-y="480" width="32" height="32" layer="1" mask="0" disabled="0" type="Box" />
        <Area id="npc_swamp_guardian_area" group="NPCs" position-x="2202" position-y="474" width="44" height="44" layer="0" mask="2" disabled="0" type="Box" />
        <Sprite id="npc_swamp_guardian_sprite" group="NPCs" path="sprites/bosses/bear.png" position-x="2224" position-y="484" z-index="150" rows="3" cols="3" scale-x="1.00" scale-y="1.00" width="48" height="48" rotation="0.00" visible="1" delay="0.14" center-position="1" type="Sprite2D" />
    </NPC>
    <NPC id="npc_swamp_cat" group="NPCs" dialogId="npc_gift" type="GameObject">
        <Body id="npc_swamp_cat_body" group="NPCs" position-x="1024" position-y="864" width="32" height="32" layer="1" mask="0" disabled="0" type="Box" />
        <Area id="npc_swamp_cat_area" group="NPCs" position-x="1018" position-y="858" width="44" height="44" layer="0" mask="2" disabled="0" type="Box" />
        <Sprite id="npc_swamp_cat_sprite" group="NPCs" path="sprites/npcs/cat.png" position-x="1040" position-y="880" z-index="150" rows="2" cols="3" scale-x="1.00" scale-y="1.00" width="16" height="16" rotation="0.00" visible="1" delay="0.22" center-position="1" type="Sprite2D" />
    </NPC>
    <Chest id="chest_swamp_1" group="chests" itemId="potion_large" itemCount="1" requiredItem="" dialogId="" opened="0" type="Struct">
        <Body id="chest_swamp_1_body" group="chests" position-x="960" position-y="800" width="28" height="24" layer="1" mask="0" disabled="0" type="Box" />
        <Area id="chest_swamp_1_area" group="chests" position-x="952" position-y="792" width="44" height="40" layer="0" mask="2" disabled="0" type="Box" />
    </Chest>
    <SceneChanger id="changer_to_beach" group="triggers" targetScene="resources/scenes/beach_ruins.xml" spawnX="1344.0" spawnY="2464.0" triggerOnTouch="0" requiredItem="" requiredFlag="" type="Struct">
        <Area id="changer_to_beach_area" group="triggers" position-x="896" position-y="864" width="36" height="36" layer="0" mask="2" disabled="0" type="Box" />
    </SceneChanger>
    <SceneChanger id="changer_south_to_beach" group="triggers" targetScene="resources/scenes/beach_ruins.xml" spawnX="1344.0" spawnY="2464.0" triggerOnTouch="0" requiredItem="" requiredFlag="" type="Struct">
        <Area id="changer_south_to_beach_area" group="triggers" position-x="1632" position-y="2560" width="36" height="36" layer="0" mask="2" disabled="0" type="Box" />
    </SceneChanger>""",
    },
}


def greedy_merge_rects(mask: np.ndarray):
    used = np.zeros_like(mask, dtype=bool)
    h, w = mask.shape
    rects = []
    for r in range(h):
        for c in range(w):
            if not mask[r, c] or used[r, c]:
                continue
            rw = 1
            while c + rw < w and mask[r, c + rw] and not used[r, c + rw]:
                rw += 1
            rh = 1
            while r + rh < h and np.all(mask[r + rh, c:c + rw]) and not np.any(used[r + rh, c:c + rw]):
                rh += 1
            used[r:r + rh, c:c + rw] = True
            rects.append((c, r, rw, rh))
    return rects


def is_water_decor_prop(obj_hash: str, bw: float, bh: float, preset_name: str):
    if preset_name != "swamp":
        return False
    return obj_hash.startswith("58dc5e46") or obj_hash.startswith("4cf87155")


def refine_prop_ground_bounds(obj: dict, gx: int, gy: int, gb_w: int, gb_h: int):
    desc = (obj.get("description") or "").lower()
    bbox = obj.get("boundingBox") or {}
    bw = float(bbox.get("width", 0))
    bh = float(bbox.get("height", 0))

    if bw >= 180 and bh >= 180:
        new_w = 40
        new_h = 22
        new_x = gx + (gb_w - new_w) // 2
        new_y = gy + (gb_h - new_h) - 4
        return new_x, new_y, new_w, new_h

    if "palm tree" in desc or (bw >= 100 and bh >= 150):
        new_w = max(14, gb_w - 6)
        new_h = min(18, gb_h)
        new_x = gx + (gb_w - new_w) // 2
        new_y = gy + (gb_h - new_h)
        return new_x, new_y, new_w, new_h

    inset_x = 2 if gb_w > 12 else 0
    inset_y = 2 if gb_h > 10 else 0
    return gx + inset_x, gy + inset_y, max(8, gb_w - inset_x * 2), max(6, gb_h - inset_y)


def is_box_mostly_covered_by_subgrid(gx: int, gy: int, gb_w: int, gb_h: int, sub_mask: np.ndarray, sub_size: int = 16):
    sh, sw = sub_mask.shape
    c0 = max(0, gx // sub_size)
    c1 = min(sw - 1, (gx + gb_w - 1) // sub_size)
    r0 = max(0, gy // sub_size)
    r1 = min(sh - 1, (gy + gb_h - 1) // sub_size)
    if c1 < c0 or r1 < r0:
        return False
    sub = sub_mask[r0:r1 + 1, c0:c1 + 1]
    return bool(np.any(sub))


def detect_preset_and_config(src_dir: pathlib.Path, map_data: dict, comp_img: np.ndarray, preset_arg: str | None, xml_out: pathlib.Path):
    if preset_arg and preset_arg in PRESETS:
        return preset_arg, dict(PRESETS[preset_arg])

    map_name = (map_data.get("name") or "").lower()
    dir_name = src_dir.name.lower()
    if "beach" in map_name or "beach" in dir_name:
        return "beach", dict(PRESETS["beach"])
    if "swap" in map_name or "swamp" in map_name or "swap" in dir_name or "swamp" in dir_name:
        return "swamp", dict(PRESETS["swamp"])

    # Configuración genérica automática para cualquier otro mapa de PixelLab
    H, W, _ = comp_img.shape
    tile_size = int(map_data["mapConfig"].get("tileSize", 32))
    max_cols = (W - 16) // tile_size
    max_rows = (H - 16) // tile_size
    c0, c1, r0, r1 = 0, max_cols, 0, max_rows
    if comp_img.shape[2] >= 4:
        ys, xs = np.where(comp_img[:, :, 3] > 0)
        if len(xs) > 0 and len(ys) > 0:
            c0 = max(0, int((xs.min() - 16) // tile_size) - 1)
            c1 = min(max_cols, int((xs.max() - 16) // tile_size) + 2)
            r0 = max(0, int((ys.min() - 16) // tile_size) - 1)
            r1 = min(max_rows, int((ys.max() - 16) // tile_size) + 2)

    cx = float((c1 - c0) * tile_size // 2)
    cy = float((r1 - r0) * tile_size // 2)
    generic_cfg = {
        "asset_folder": xml_out.stem,
        "crop": (c0, c1, r0, r1),
        "spawn": (cx, cy),
        "ts_names": [],
        "water_ts_idx": 0,
        "water_tile_idx": 6,
        "land_tile_idx": 12,
        "has_walls": False,
        "dialogs": "dialogs/debug.json",
        "entities_xml": f"""    <Player id="player" group="Player" speed="92.00" type="Struct">
        <Body id="player_body" group="Player" position-x="{int(cx)}" position-y="{int(cy)}" width="12" height="12" layer="1" mask="1" disabled="0" type="Box" />
        <Sprite id="player_sprite" group="Player" path="sprites/player/The Female Adventurer - Free/Idle/Idle_Down.png" position-x="{int(cx)+6}" position-y="{int(cy)+6}" z-index="150" rows="1" cols="8" scale-x="1.00" scale-y="1.00" rotation="0.00" visible="1" delay="0.10" center-position="1" type="Sprite2D" />
    </Player>""",
    }
    return "generic", generic_cfg


def main():
    parser = argparse.ArgumentParser(description="Importa una escena JSON de PixelLab a un archivo XML de Combo 6Beat.")
    parser.add_argument("src_path", help="Directorio de export de PixelLab o ruta a map.json")
    parser.add_argument("--preset", choices=["beach", "swamp"], default=None, help="Preset del escenario (opcional)")
    parser.add_argument("--out-dir", default=None, help="Directorio destino de assets de la escena (opcional)")
    parser.add_argument("--xml-out", required=True, help="Archivo XML de escena de salida")
    parser.add_argument("--procshit-out", default=None, help="Archivo .procshit de salida (opcional)")
    args = parser.parse_args()

    raw_src = pathlib.Path(args.src_path).resolve()
    src_dir = raw_src.parent if raw_src.is_file() else raw_src
    xml_out = pathlib.Path(args.xml_out).resolve()
    xml_out.parent.mkdir(parents=True, exist_ok=True)
    resources_root = xml_out.parent.parent.resolve()

    map_json_path = raw_src if (raw_src.is_file() and raw_src.suffix.lower() == ".json") else (src_dir / "map.json")
    manifest_path = src_dir / "objects" / "manifest.json"
    comp_path = src_dir / "map-composite.png"

    map_data = json.loads(map_json_path.read_text(encoding="utf-8"))
    manifest_data = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {"objects": []}
    comp_img = np.array(Image.open(comp_path))
    H, W, _ = comp_img.shape

    preset_name, cfg = detect_preset_and_config(src_dir, map_data, comp_img, args.preset, xml_out)

    if args.out_dir:
        out_dir = pathlib.Path(args.out_dir).resolve()
    else:
        out_dir = (xml_out.parent / cfg["asset_folder"]).resolve()

    tile_size = int(map_data["mapConfig"].get("tileSize", 32))
    sub_size = tile_size // 2
    min_tx = int(map_data["mapConfig"]["boundingBox"]["minX"])
    min_ty = int(map_data["mapConfig"]["boundingBox"]["minY"])
    origin_px_x = -min_tx * tile_size
    origin_px_y = -min_ty * tile_size

    c0, c1, r0, r1 = cfg["crop"]
    map_w, map_h = c1 - c0, r1 - r0

    if H < 16 + r1 * tile_size or W < 16 + c1 * tile_size:
        zip_cand = src_dir.with_suffix(".zip")
        if zip_cand.exists():
            with zipfile.ZipFile(zip_cand) as zf:
                with zf.open("map-composite.png") as zcomp:
                    comp_img = np.array(Image.open(zcomp))
                    H, W, _ = comp_img.shape

    out_tilesets_dir = out_dir / "tilesets"
    out_objects_dir = out_dir / "objects"
    out_tilesets_dir.mkdir(parents=True, exist_ok=True)
    out_objects_dir.mkdir(parents=True, exist_ok=True)

    # 1. Copiar tilesets con nombres limpios
    tiles = []
    ts_arrays_rgb = []
    ts_entries_meta = []

    for ts_idx, ts_info in enumerate(map_data.get("tilesets", [])):
        src_ts_file = src_dir / "tilesets" / ts_info["filename"]
        clean_name = cfg["ts_names"][ts_idx] if ts_idx < len(cfg["ts_names"]) else f"tileset_{ts_idx}.png"
        dst_ts_file = out_tilesets_dir / clean_name
        shutil.copy2(src_ts_file, dst_ts_file)

        im = np.array(Image.open(src_ts_file))
        ts_arrays_rgb.append(im[:, :, :3].astype(float))
        th, tw, _ = im.shape
        cols = tw // tile_size
        rows = th // tile_size
        tcount = cols * rows
        for r in range(rows):
            for c in range(cols):
                tid = r * cols + c
                tiles.append((ts_idx, tid, im[r * tile_size:(r + 1) * tile_size, c * tile_size:(c + 1) * tile_size].astype(np.int16)))

        try:
            rel_to_res = dst_ts_file.relative_to(resources_root).as_posix()
        except ValueError:
            rel_to_res = f"scenes/{out_dir.name}/tilesets/{clean_name}"

        ts_info_copy = dict(ts_info)
        ts_info_copy["filename"] = clean_name
        ts_info_copy["path"] = rel_to_res
        ts_info_copy["tileCount"] = tcount
        ts_entries_meta.append(ts_info_copy)

    # 2. Sub-máscaras 2x2 (16x16 px) de Agua y Muros
    w_ts_idx = min(cfg["water_ts_idx"], max(0, len(ts_arrays_rgb) - 1))
    w_tid = cfg["water_tile_idx"]
    l_tid = cfg["land_tile_idx"]
    water_quad_mask = {}
    if ts_arrays_rgb:
        wts_rgb = ts_arrays_rgb[w_ts_idx]
        wr, wc = w_tid // 4, w_tid % 4
        lr_, lc_ = l_tid // 4, l_tid % 4
        water_ref_rgb = wts_rgb[wr * tile_size:(wr + 1) * tile_size, wc * tile_size:(wc + 1) * tile_size].mean(axis=(0, 1))
        land_ref_rgb = wts_rgb[lr_ * tile_size:(lr_ + 1) * tile_size, lc_ * tile_size:(lc_ + 1) * tile_size].mean(axis=(0, 1))

        for tid in range(min(16, len(wts_rgb) // tile_size * 4)):
            r, c = tid // 4, tid % 4
            tile_rgb = wts_rgb[r * tile_size:(r + 1) * tile_size, c * tile_size:(c + 1) * tile_size]
            qm = np.zeros((2, 2), dtype=bool)
            for sr in range(2):
                for sc in range(2):
                    quad_mean = tile_rgb[sr * sub_size:(sr + 1) * sub_size, sc * sub_size:(sc + 1) * sub_size].mean(axis=(0, 1))
                    qm[sr, sc] = np.linalg.norm(quad_mean - water_ref_rgb) < np.linalg.norm(quad_mean - land_ref_rgb)
            water_quad_mask[tid] = qm

    wall_quad_mask = {}
    if cfg["has_walls"] and len(ts_arrays_rgb) >= 3:
        floor_ref_tile = ts_arrays_rgb[1][3 * tile_size:4 * tile_size, 0 * tile_size:1 * tile_size]
        ts2_rgb = ts_arrays_rgb[2]
        for tid in range(32):
            r, c = tid // 4, tid % 4
            tile_rgb = ts2_rgb[r * tile_size:(r + 1) * tile_size, c * tile_size:(c + 1) * tile_size]
            qm = np.zeros((2, 2), dtype=bool)
            for sr in range(2):
                for sc in range(2):
                    quad = tile_rgb[sr * sub_size:(sr + 1) * sub_size, sc * sub_size:(sc + 1) * sub_size]
                    f_quad = floor_ref_tile[sr * sub_size:(sr + 1) * sub_size, sc * sub_size:(sc + 1) * sub_size]
                    qm[sr, sc] = np.abs(quad - f_quad).mean() > 14.0
            wall_quad_mask[tid] = qm

    # 3. Máscara alfa de objetos y deduplicación de texturas PNG
    obj_alpha_mask = np.zeros((H, W), dtype=bool)
    hash_to_clean_name = {}
    updated_objects = []
    unique_idx = 0

    for obj in manifest_data.get("objects", []):
        src_obj_file = src_dir / "objects" / obj["filename"]
        if not src_obj_file.exists():
            continue

        im_arr = np.array(Image.open(src_obj_file))
        if obj.get("visible", True):
            ox = int(round(obj["boundingBox"]["x"] + origin_px_x))
            oy = int(round(obj["boundingBox"]["y"] + origin_px_y))
            oh, ow, _ = im_arr.shape
            y0, y1 = max(0, oy), min(H, oy + oh)
            x0, x1 = max(0, ox), min(W, ox + ow)
            if y1 > y0 and x1 > x0:
                sub = im_arr[y0 - oy:y1 - oy, x0 - ox:x1 - ox, 3] > 0
                pad_sub = np.pad(sub, 1, mode="edge")
                my0, my1 = max(0, y0 - 1), min(H, y1 + 1)
                mx0, mx1 = max(0, x0 - 1), min(W, x1 + 1)
                obj_alpha_mask[my0:my1, mx0:mx1] |= pad_sub[:my1 - my0, :mx1 - mx0]

        digest = hashlib.sha256(src_obj_file.read_bytes()).hexdigest()[:12]
        if digest not in hash_to_clean_name:
            unique_idx += 1
            clean_obj_name = f"prop_{unique_idx:02d}_{digest[:6]}.png"
            shutil.copy2(src_obj_file, out_objects_dir / clean_obj_name)
            hash_to_clean_name[digest] = clean_obj_name

        clean_obj_name = hash_to_clean_name[digest]
        try:
            rel_obj_to_res = (out_objects_dir / clean_obj_name).relative_to(resources_root).as_posix()
        except ValueError:
            rel_obj_to_res = f"scenes/{out_dir.name}/objects/{clean_obj_name}"

        obj_copy = dict(obj)
        obj_copy["filename"] = clean_obj_name
        obj_copy["path"] = rel_obj_to_res
        if is_water_decor_prop(digest, obj["boundingBox"]["width"], obj["boundingBox"]["height"], preset_name):
            obj_copy["noCollision"] = True
        updated_objects.append(obj_copy)

    # 4. Extraer grilla recortada
    packed_cells = []
    q_wall = np.zeros((map_h * 2, map_w * 2), dtype=bool)
    q_water = np.zeros((map_h * 2, map_w * 2), dtype=bool)

    for r in range(r0, r1):
        y = 16 + r * tile_size
        for c in range(c0, c1):
            x = 16 + c * tile_size
            cell = comp_img[y:y + tile_size, x:x + tile_size]
            if np.all(cell[:, :, 3] == 0):
                packed_cells.append(-1)
                continue
            cell16 = cell.astype(np.int16)
            unocc = ~obj_alpha_mask[y:y + tile_size, x:x + tile_size]
            if np.any(unocc) and tiles:
                _, best_ts, best_tid = min(
                    (np.abs(cell16[unocc] - t[unocc]).mean(), ts_idx, tid)
                    for ts_idx, tid, t in tiles
                )
            else:
                best_ts, best_tid = 0, 12

            packed = (best_ts << 16) | (best_tid & 0xFFFF)
            packed_cells.append(packed)

            lr, lc = r - r0, c - c0
            if cfg["has_walls"] and best_ts == 2 and best_tid in wall_quad_mask:
                q_wall[lr * 2:(lr + 1) * 2, lc * 2:(lc + 1) * 2] = wall_quad_mask[best_tid]
            elif best_ts == w_ts_idx and best_tid in water_quad_mask:
                q_water[lr * 2:(lr + 1) * 2, lc * 2:(lc + 1) * 2] = water_quad_mask[best_tid]

    # 5. Colisiones fusionadas (16x16 px)
    wall_rects = greedy_merge_rects(q_wall) if cfg["has_walls"] else []
    ocean_rects = greedy_merge_rects(q_water)

    xml_boxes = []
    for idx, (qc, qr, qw, qh) in enumerate(wall_rects, 1):
        xml_boxes.append((f"wall_{idx}", qc * sub_size, qr * sub_size, qw * sub_size, qh * sub_size))

    for idx, (qc, qr, qw, qh) in enumerate(ocean_rects, 1):
        xml_boxes.append((f"water_{idx}", qc * sub_size, qr * sub_size, qw * sub_size, qh * sub_size))

    shift_x = origin_px_x - sub_size - c0 * tile_size
    shift_y = origin_px_y - sub_size - r0 * tile_size
    combined_sub_blocked = q_wall | q_water

    xml_sprites = []
    prop_box_count = 0
    for i, obj in enumerate(updated_objects):
        if not obj.get("visible", True):
            continue
        short_id = obj["id"][:8]
        bbox = obj["boundingBox"]
        bx = int(round(bbox["x"] + shift_x))
        by = int(round(bbox["y"] + shift_y))
        bw = int(round(bbox["width"]))
        bh = int(round(bbox["height"]))
        layer = int(obj.get("layer", 1))
        no_col = bool(obj.get("noCollision", False))
        z_idx = -5 if no_col else (10 + layer)
        xml_sprites.append((f"prop_{i+1}_{short_id}", obj["path"], bx, by, bw, bh, z_idx))

        gb = obj.get("groundBounds")
        if not no_col and gb:
            raw_gx = int(round(gb["x"] + shift_x))
            raw_gy = int(round(gb["y"] + shift_y))
            raw_gw = int(round(gb["width"]))
            raw_gh = int(round(gb["height"]))
            if raw_gw > 0 and raw_gh > 0:
                gx, gy, gb_w, gb_h = refine_prop_ground_bounds(obj, raw_gx, raw_gy, raw_gw, raw_gh)
                if not is_box_mostly_covered_by_subgrid(gx, gy, gb_w, gb_h, combined_sub_blocked, sub_size):
                    prop_box_count += 1
                    xml_boxes.append((f"prop_col_{i+1}_{short_id}", gx, gy, gb_w, gb_h))

    # 6. Escribir XML de escena
    spawn_x, spawn_y = cfg["spawn"]
    lines = [
        '<?xml version="1.0" encoding="UTF-8" ?>',
        f'<Map resources="../" dialogs="{cfg["dialogs"]}" spawn-x="{spawn_x:.1f}" spawn-y="{spawn_y:.1f}">',
    ]
    for idx, ts_meta in enumerate(ts_entries_meta):
        lines.append(
            f'    <Tileset id="{idx}" path="{ts_meta["path"]}" tile-width="{tile_size}" tile-height="{tile_size}" tile-count="{ts_meta["tileCount"]}" />'
        )
    lines.append(f'    <Tilemap width="{map_w}" height="{map_h}">')
    for r in range(map_h):
        for c in range(map_w):
            packed = packed_cells[r * map_w + c]
            if packed < 0:
                continue
            ts_i = (packed >> 16) & 0x7FFF
            tid = packed & 0xFFFF
            lines.append(f'        <Cell col="{c}" row="{r}" tileset="{ts_i}" tile="{tid}" />')
    lines.append("    </Tilemap>")
    lines.append("")

    for sid, spath, sx, sy, sw, sh, sz in xml_sprites:
        lines.append(
            f'    <Sprite id="{sid}" group="Props" path="{spath}" position-x="{sx}" position-y="{sy}" width="{sw}" height="{sh}" z-index="{sz}" rows="1" cols="1" scale-x="1.00" scale-y="1.00" rotation="0.00" visible="1" delay="0.10" center-position="0" type="Sprite2D" />'
        )

    for bid, bx, by, bw, bh in xml_boxes:
        lines.append(
            f'    <Box id="{bid}" group="Walls" position-x="{bx}" position-y="{by}" width="{bw}" height="{bh}" layer="1" mask="1" disabled="0" type="Box" />'
        )

    lines.append(cfg["entities_xml"])
    lines.append("</Map>")
    xml_out.write_text("\n".join(lines) + "\n", encoding="utf-8")

    # 7. Escribir .procshit si se solicitó
    if args.procshit_out:
        procshit_out = pathlib.Path(args.procshit_out).resolve()
        proc_lines = [
            "# Combo 6Beat Project File (.procshit)",
            "RESOURCES_PREFIX=../",
            f"ACTIVE_SCENE_XML=source/combo_6beat/resources/scenes/{xml_out.name}",
            f"ACTIVE_DIALOGS_JSON=source/combo_6beat/resources/{cfg['dialogs']}",
            "MAP_ENTRY=source/combo_6beat/resources/scenes/beach_ruins.xml",
            "MAP_ENTRY=source/combo_6beat/resources/scenes/swamp.xml",
            "MAP_ENTRY=source/combo_6beat/resources/scenes/a.xml",
            f"TILESET_PNG={ts_entries_meta[0]['path']}",
            f"TILESET_TILE_WIDTH={tile_size}",
            f"TILESET_TILE_HEIGHT={tile_size}",
            f"TILESET_TILE_COUNT={ts_entries_meta[0]['tileCount']}",
            "TILESET_INACTIVE=",
            f"TILESET_COUNT={len(ts_entries_meta)}",
        ]
        for idx, ts_meta in enumerate(ts_entries_meta):
            proc_lines.append(f"TILESET_ENTRY={idx},{tile_size},{tile_size},{ts_meta['tileCount']},{ts_meta['path']}")
            proc_lines.append(f"TILESET_ENTRY_INACTIVE={idx}")
        proc_lines.append(f"TILEMAP_WIDTH={map_w}")
        proc_lines.append(f"TILEMAP_HEIGHT={map_h}")
        for r in range(map_h):
            for c in range(map_w):
                packed = packed_cells[r * map_w + c]
                if packed < 0:
                    continue
                ts_i = (packed >> 16) & 0x7FFF
                tid = packed & 0xFFFF
                proc_lines.append(f"TILEMAP_CELL={c},{r},{ts_i},{tid}")

        proc_lines.extend([
            "",
            "# Prefabs de Objetos del Proyecto",
            "PREFAB_DEF=0,player",
            "PREFAB_OBJ=0,0,1,-1,2,player,Player,Player",
            "PREFAB_PROP=0,0,1,speed,92.00",
            "PREFAB_PROP=0,0,3,type,Struct",
            "PREFAB_OBJ=0,1,2,1,0,player_body,Body,Player",
            "PREFAB_BOX=0,1,0.0,0.0,12.0,12.0,1,1,0",
            "PREFAB_OBJ=0,2,3,1,1,player_sprite,Sprite,Player",
            "PREFAB_SPRITE=0,2,6.0,6.0,1.00,1.00,0.00,150,1,8,0.10,1,1,sprites/player/The Female Adventurer - Free/Idle/Idle_Down.png",
            "PREFAB_DEF=1,enemy_vampire",
            "PREFAB_OBJ=1,0,1,-1,2,enemy_vampire,Enemy,Enemies",
            "PREFAB_PROP=1,0,3,dialogId,__start__",
            "PREFAB_PROP=1,0,3,dialogFile,dialogs/debug.json",
            "PREFAB_PROP=1,0,2,autoTrigger,0",
            "PREFAB_PROP=1,0,3,type,GameObject",
            "PREFAB_OBJ=1,1,2,1,0,enemy_body,Body,Enemies",
            "PREFAB_BOX=1,1,0.0,0.0,28.0,28.0,1,0,0",
            "PREFAB_OBJ=1,2,3,1,0,enemy_area,Area,Enemies",
            "PREFAB_BOX=1,2,-8.0,-8.0,44.0,44.0,0,2,0",
            "PREFAB_OBJ=1,3,4,1,1,enemy_sprite,Sprite,Enemies",
            "PREFAB_SPRITE=1,3,14.0,6.0,1.00,1.00,0.00,150,3,3,0.14,1,1,sprites/bosses/bear.png",
            "PREFAB_DEF=2,npc_guardian",
            "PREFAB_OBJ=2,0,1,-1,2,npc_guardian,NPC,NPCs",
            "PREFAB_PROP=2,0,3,dialogId,__start__",
            "PREFAB_PROP=2,0,3,dialogFile,dialogs/debug.json",
            "PREFAB_PROP=2,0,2,autoTrigger,0",
            "PREFAB_PROP=2,0,3,type,GameObject",
            "PREFAB_OBJ=2,1,2,1,0,npc_body,Body,NPCs",
            "PREFAB_BOX=2,1,0.0,0.0,32.0,32.0,1,0,0",
            "PREFAB_OBJ=2,2,3,1,0,npc_area,Area,NPCs",
            "PREFAB_BOX=2,2,-6.0,-6.0,44.0,44.0,0,2,0",
            "PREFAB_OBJ=2,3,4,1,1,npc_sprite,Sprite,NPCs",
            "PREFAB_SPRITE=2,3,16.0,16.0,1.00,1.00,0.00,150,2,3,0.22,1,1,sprites/npcs/cat.png",
            "PREFAB_DEF=3,chest_item",
            "PREFAB_OBJ=3,0,1,-1,2,chest_item,Chest,Chests",
            "PREFAB_PROP=3,0,3,itemId,potion_medium",
            "PREFAB_PROP=3,0,0,itemCount,1",
            "PREFAB_PROP=3,0,2,opened,0",
            "PREFAB_PROP=3,0,3,type,GameObject",
            "PREFAB_OBJ=3,1,2,1,0,chest_body,Body,Chests",
            "PREFAB_BOX=3,1,0.0,0.0,28.0,24.0,1,0,0",
            "PREFAB_OBJ=3,2,3,1,0,chest_area,Area,Chests",
            "PREFAB_BOX=3,2,-8.0,-8.0,44.0,40.0,0,2,0",
            "PREFAB_DEF=4,portal_scene",
            "PREFAB_OBJ=4,0,1,-1,2,portal_scene,SceneChanger,SceneChangers",
            "PREFAB_PROP=4,0,3,targetScene,resources/scenes/swamp.xml",
            "PREFAB_PROP=4,0,1,spawnX,960.00",
            "PREFAB_PROP=4,0,1,spawnY,864.00",
            "PREFAB_PROP=4,0,2,triggerOnTouch,0",
            "PREFAB_PROP=4,0,3,type,GameObject",
            "PREFAB_OBJ=4,1,2,1,0,portal_area,Area,SceneChangers",
            "PREFAB_BOX=4,1,0.0,0.0,36.0,36.0,0,2,0",
        ])

        procshit_out.write_text("\n".join(proc_lines) + "\n", encoding="utf-8")

    print(f"Imported {preset_name} scene -> {xml_out}")
    print(f"  Grid:    {map_w}x{map_h} tiles (colOffset={c0}, rowOffset={r0})")
    print(f"  Sprites: {len(xml_sprites)} (unique PNGs={len(hash_to_clean_name)})")
    print(f"  Boxes:   {len(xml_boxes)} (Walls={len(wall_rects)}, Water={len(ocean_rects)}, Props={prop_box_count})")


if __name__ == "__main__":
    main()
