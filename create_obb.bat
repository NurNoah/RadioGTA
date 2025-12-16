@echo off
REM Skript zum Erstellen der OBB-Datei für RadioGTA (Windows)

SET PACKAGE_NAME=com.example.radiogta
SET VERSION_CODE=1
SET OBB_FILENAME=main.%VERSION_CODE%.%PACKAGE_NAME%.obb
SET RADIO_DIR=radio_files
SET OUTPUT_DIR=obb_output

echo ======================================
echo Erstelle OBB-Datei für RadioGTA
echo ======================================
echo.

REM Prüfe ob das Radio-Verzeichnis existiert
if not exist "%RADIO_DIR%" (
    echo Fehler: Verzeichnis %RADIO_DIR% nicht gefunden!
    echo Stelle sicher, dass die MP3-Dateien in %RADIO_DIR% liegen.
    pause
    exit /b 1
)

REM Erstelle Output-Verzeichnis
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

echo Erstelle OBB-Datei: %OBB_FILENAME%
echo.

REM Erstelle temp Ordner mit korrekter Struktur
if exist temp_obb rmdir /s /q temp_obb
mkdir temp_obb\radio
copy "%RADIO_DIR%\*.mp3" temp_obb\radio\

REM Erstelle OBB mit 7-Zip
where 7z >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    cd temp_obb
    7z a -tzip -mx0 "..\%OUTPUT_DIR%\%OBB_FILENAME%" radio\*.mp3
    cd ..
) else (
    REM Versuche mit PowerShell (erstellt erst .zip, dann umbenennen)
    echo 7-Zip nicht gefunden, verwende PowerShell...
    powershell -Command "Compress-Archive -Path 'temp_obb\radio\*' -DestinationPath '%OUTPUT_DIR%\temp.zip' -CompressionLevel NoCompression -Force"
    move /Y "%OUTPUT_DIR%\temp.zip" "%OUTPUT_DIR%\%OBB_FILENAME%"
)

REM Aufräumen
rmdir /s /q temp_obb

REM Prüfe ob erfolgreich
if exist "%OUTPUT_DIR%\%OBB_FILENAME%" (
    echo.
    echo OBB-Datei erfolgreich erstellt!
    echo Datei: %OUTPUT_DIR%\%OBB_FILENAME%
    echo.
    echo ======================================
    echo Installation auf dem Gerät:
    echo ======================================
    echo 1. Kopiere die OBB-Datei auf dein Android-Gerät:
    echo    adb push %OUTPUT_DIR%\%OBB_FILENAME% /sdcard/
    echo.
    echo 2. Verschiebe sie in den OBB-Ordner:
    echo    adb shell
    echo    mkdir -p /sdcard/Android/obb/%PACKAGE_NAME%/
    echo    mv /sdcard/%OBB_FILENAME% /sdcard/Android/obb/%PACKAGE_NAME%/
    echo    exit
    echo.
    echo 3. Installiere die APK normal über Android Studio
    echo ======================================
) else (
    echo Fehler beim Erstellen der OBB-Datei
    pause
    exit /b 1
)

pause