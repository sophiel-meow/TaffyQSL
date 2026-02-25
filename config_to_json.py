#!/usr/bin/env python3
"""
Convert TrustedQSL config.xml into multiple JSON files for TaffyQSL Android app.

Output files (all written to app/src/main/assets/):
  bands.json
  modes.json
  adifmap.json
  dxcc.json
  subdivisions.json
  satellites.json
  propmodes.json

Run from the project root:
  python3 config_to_json.py
"""

import json
import os
import xml.etree.ElementTree as ET

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
ASSETS_DIR = os.path.join(SCRIPT_DIR, "app", "src", "main", "assets")
CONFIG_XML = os.path.join(SCRIPT_DIR, "original-tqsl", "src", "config.xml")


def parse_zonemap(zonemap):
    """Parse ITU:CQ zonemap string into (cqZones, ituZones) comma-separated strings.

    Format: "ITU:CQ" pairs, comma-separated.
    Example: "44:24"      → cqZone="24",    ituZone="44"
    Example: "44:23,44:24"→ cqZone="23,24", ituZone="44"
    """
    if not zonemap:
        return "", ""
    cq_zones = []
    itu_zones = []
    seen_cq = set()
    seen_itu = set()
    for pair in zonemap.split(","):
        pair = pair.strip()
        if ":" in pair:
            parts = pair.split(":", 1)
            itu = parts[0].strip()
            cq = parts[1].strip()
            if itu not in seen_itu:
                itu_zones.append(itu)
                seen_itu.add(itu)
            if cq not in seen_cq:
                cq_zones.append(cq)
                seen_cq.add(cq)
    return ",".join(cq_zones), ",".join(itu_zones)


def write_json(filename, data):
    path = os.path.join(ASSETS_DIR, filename)
    with open(path, "w", encoding="utf-8") as f:
        # Compact JSON (no indentation) to minimize APK size
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    print(f"  Written: {filename}")


def main():
    print(f"Parsing: {CONFIG_XML}")
    tree = ET.parse(CONFIG_XML)
    root = tree.getroot()

    # ── 1. Bands ──────────────────────────────────────────────────────────────
    print("\n[1/7] Bands")
    bands = []
    bands_elem = root.find("bands")
    if bands_elem is not None:
        for band in bands_elem.findall("band"):
            spectrum = band.get("spectrum", "HF")
            raw_low = float(band.get("low", "0"))
            raw_high = float(band.get("high", "0"))
            name = (band.text or "").strip()
            if not name:
                continue
            # HF values are in kHz → convert to MHz; VHF/UHF already in MHz
            divisor = 1000.0 if spectrum == "HF" else 1.0
            bands.append({
                "name": name,
                "spectrum": spectrum,
                "freqLow": raw_low / divisor,
                "freqHigh": raw_high / divisor,
            })
    bands.sort(key=lambda b: b["freqLow"])
    write_json("bands.json", bands)
    print(f"  {len(bands)} bands")

    # ── 2. Modes ──────────────────────────────────────────────────────────────
    print("\n[2/7] Modes")
    modes = []
    modes_elem = root.find("modes")
    if modes_elem is not None:
        for mode in modes_elem.findall("mode"):
            group = mode.get("group", "")
            name = (mode.text or "").strip()
            if name:
                modes.append({"name": name, "group": group})
    write_json("modes.json", modes)
    print(f"  {len(modes)} modes")

    # ── 3. ADIF Mode Map ──────────────────────────────────────────────────────
    print("\n[3/7] ADIF mode map")
    adif_map = []
    adifmap_elem = root.find("adifmap")
    if adifmap_elem is not None:
        for entry in adifmap_elem.findall("adifmode"):
            adif_mode = entry.get("adif-mode", "")
            adif_submode = entry.get("adif-submode", "")
            tqsl_mode = entry.get("mode", "")
            if adif_mode and tqsl_mode:
                adif_map.append({
                    "adifMode": adif_mode,
                    "adifSubmode": adif_submode,
                    "tqslMode": tqsl_mode,
                })
    write_json("adifmap.json", adif_map)
    print(f"  {len(adif_map)} entries")

    # ── 4. DXCC Entities ──────────────────────────────────────────────────────
    print("\n[4/7] DXCC entities")

    # Build locpages mapping: dxcc_id(int) → primary_field_id(str)
    # Skip internal/UI fields that are not subdivision fields
    SKIP_FIELDS = {"ZERR", "CALL", "GRIDSQUARE", "ITUZ", "CQZ", "IOTA", "WPX", "CONT", "DXCC"}
    dxcc_field_map = {}  # dxcc_id(int) → fieldId(str)
    locpages_elem = root.find("locpages")
    if locpages_elem is not None:
        for page in locpages_elem.findall("page"):
            dep_str = page.get("dependency")
            if dep_str is None:
                continue
            try:
                dep = int(dep_str)
            except ValueError:
                continue
            # Use the first non-skip pageField as the primary subdivision field
            for pf in page.findall("pageField"):
                field_name = (pf.text or "").strip()
                if field_name and field_name not in SKIP_FIELDS:
                    dxcc_field_map[dep] = field_name
                    break

    # Parse DXCC entities
    dxcc_entities = []
    dxcc_elem = root.find("dxcc")
    if dxcc_elem is not None:
        for entity in dxcc_elem.findall("entity"):
            arrl_id_str = entity.get("arrlId")
            if arrl_id_str is None:
                continue
            try:
                arrl_id = int(arrl_id_str)
            except ValueError:
                continue
            name = (entity.text or "").strip()
            deleted = entity.get("deleted") == "1"
            zone_map = entity.get("zonemap", "")
            valid = entity.get("valid", "")
            invalid = entity.get("invalid", "")
            state_field_id = dxcc_field_map.get(arrl_id, "")
            dxcc_entities.append({
                "arrlId": arrl_id,
                "name": name,
                "deleted": deleted,
                "valid": valid,
                "invalid": invalid,
                "zoneMap": zone_map,
                "stateFieldId": state_field_id,
            })
    write_json("dxcc.json", dxcc_entities)
    print(f"  {len(dxcc_entities)} DXCC entities")
    print(f"  {len(dxcc_field_map)} entities with subdivision fields: "
          + str(dxcc_field_map))

    # ── 5. Subdivisions (locfields) ───────────────────────────────────────────
    print("\n[5/7] Subdivisions (locfields)")
    # Skip internal/UI fields only (not subdivision enums).
    SKIP_FIELD_IDS = {
        "CALL", "CQZ", "DXCC", "GRIDSQUARE", "IOTA", "ITUZ",
        "WPX", "ZERR", "CONT",
    }
    subdivisions = []
    locfields_elem = root.find("locfields")
    if locfields_elem is not None:
        for field in locfields_elem.findall("field"):
            field_id = field.get("Id", "")
            if field_id in SKIP_FIELD_IDS:
                continue
            label = field.get("label", "")
            depends_on = field.get("dependsOn") or None

            groups = []

            # Handle <enums [dependency="..."]> children
            for enums in field.findall("enums"):
                dep_raw = enums.get("dependency")
                # Try int first, fall back to string
                dep_value = None
                if dep_raw is not None:
                    try:
                        dep_value = int(dep_raw)
                    except ValueError:
                        dep_value = dep_raw  # e.g. province abbreviation

                values = []
                for enum in enums.findall("enum"):
                    abbrev = enum.get("value", "")
                    zonemap = enum.get("zonemap", "")
                    area = enum.get("area")
                    enum_name = (enum.text or "").strip()
                    cq_zone, itu_zone = parse_zonemap(zonemap)
                    entry = {
                        "abbrev": abbrev,
                        "name": enum_name,
                        "cqZone": cq_zone,
                        "ituZone": itu_zone,
                    }
                    if area is not None:
                        entry["area"] = area
                    values.append(entry)

                if values:
                    groups.append({
                        "dependency": dep_value,
                        "values": values,
                    })

            # Handle direct <enum> children (no grouping)
            direct_values = []
            for enum in field.findall("enum"):
                abbrev = enum.get("value", "")
                zonemap = enum.get("zonemap", "")
                enum_name = (enum.text or "").strip()
                cq_zone, itu_zone = parse_zonemap(zonemap)
                direct_values.append({
                    "abbrev": abbrev,
                    "name": enum_name,
                    "cqZone": cq_zone,
                    "ituZone": itu_zone,
                })
            if direct_values:
                groups.append({
                    "dependency": None,
                    "values": direct_values,
                })

            if groups:
                subdivisions.append({
                    "fieldId": field_id,
                    "label": label,
                    "dependsOn": depends_on,
                    "groups": groups,
                })

    write_json("subdivisions.json", subdivisions)
    total_values = sum(len(g["values"]) for s in subdivisions for g in s["groups"])
    print(f"  {len(subdivisions)} subdivision fields, {total_values} total enum values")
    for s in subdivisions:
        n = sum(len(g["values"]) for g in s["groups"])
        print(f"    {s['fieldId']}: {len(s['groups'])} group(s), {n} values")

    # ── 6. Satellites ─────────────────────────────────────────────────────────
    print("\n[6/7] Satellites")
    satellites = []
    satellites_elem = root.find("satellites")
    if satellites_elem is not None:
        for sat in satellites_elem.findall("satellite"):
            name = sat.get("name", "")
            start = sat.get("startDate", "")
            end = sat.get("endDate", "")
            description = (sat.text or "").strip()
            if name:
                satellites.append({
                    "name": name,
                    "description": description,
                    "startDate": start,
                    "endDate": end,
                })
    write_json("satellites.json", satellites)
    print(f"  {len(satellites)} satellites")

    # ── 7. Propagation Modes ──────────────────────────────────────────────────
    print("\n[7/7] Propagation modes")
    prop_modes = []
    propmodes_elem = root.find("propmodes")
    if propmodes_elem is not None:
        for pm in propmodes_elem.findall("propmode"):
            name = pm.get("name", "")
            desc = (pm.text or "").strip() or name
            if name:
                prop_modes.append({"name": name, "description": desc})
    write_json("propmodes.json", prop_modes)
    print(f"  {len(prop_modes)} propagation modes")

    print("\n✓ All JSON files written to:", ASSETS_DIR)


if __name__ == "__main__":
    main()
