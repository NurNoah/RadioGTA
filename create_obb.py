#!/usr/bin/env python3
import os
import zipfile
from pathlib import Path

PACKAGE_NAME = "com.example.radiogta"
VERSION_CODE = 1
RADIO_DIR = "radio_files"
OUTPUT_DIR = "obb_output"

# Max Größe pro OBB (z.B. 2.5 GB um sicher zu sein)
MAX_SIZE_BYTES = 2.5 * 1024 * 1024 * 1024 

def create_obb(filename, file_list):
    path = Path(OUTPUT_DIR) / filename
    print(f"📦 Erstelle {filename} mit {len(file_list)} Dateien...")
    
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_STORED) as zipf:
        for src_file in file_list:
            arcname = f"radio/{src_file.name}"
            print(f"  + {src_file.name}")
            zipf.write(src_file, arcname)
    
    size_gb = path.stat().st_size / (1024**3)
    print(f"✅ {filename} fertig: {size_gb:.2f} GB")

def main():
    radio_path = Path(RADIO_DIR)
    output_path = Path(OUTPUT_DIR)
    output_path.mkdir(exist_ok=True)
    
    mp3_files = sorted(list(radio_path.glob("*.mp3")), key=lambda f: f.stat().st_size, reverse=True)
    
    main_files = []
    patch_files = []
    current_main_size = 0
    
    # Greedy Verteilung: Fülle Main bis voll, Rest in Patch
    for f in mp3_files:
        size = f.stat().st_size
        if current_main_size + size < MAX_SIZE_BYTES:
            main_files.append(f)
            current_main_size += size
        else:
            patch_files.append(f)
            
    # OBBs erstellen
    create_obb(f"main.{VERSION_CODE}.{PACKAGE_NAME}.obb", main_files)
    
    if patch_files:
        create_obb(f"patch.{VERSION_CODE}.{PACKAGE_NAME}.obb", patch_files)
    else:
        print("ℹ️ Alle Dateien passten in die Main-OBB (kein Patch nötig).")

    print("\nADB Commands:")
    print(f"adb push {output_path}/*.obb /sdcard/Android/obb/{PACKAGE_NAME}/")

if __name__ == "__main__":
    main()