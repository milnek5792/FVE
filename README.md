# FVE3 / FVE 1

ESP32 regulátor FVE + MQTT + PWA / nativní Android APK.

## PWA

Zdroj: [`WebApp/pwa/`](WebApp/pwa/) — kontrolní panel (MQTT přes WSS).

## Android APK (nativní)

Stejný přístup jako **Rotenso Windmi**: nativní **Kotlin** aplikace v [`android-app/`](android-app/) (XML UI + HiveMQ MQTT přes MQTTS, stejné topicy jako PWA).

| | |
|--|--|
| App ID | `cz.fve1.control` |
| Broker (výchozí) | `mqtts://n9e16b3c.ala.eu-central-1.emqxsl.com:8883` |
| Topicy | `fve/mereni/*`, `fve/stav/rele*`, příkaz `fve/prikazy` → `reset_day` |
| Workflow | [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml) → artifact `Fve1Ctrl-apk` |

### Stažení APK

1. Push do `main` (změny v `android-app/**`) nebo Actions → **Build APK** → **Run workflow**
2. Actions → běh → Artifacts → **Fve1Ctrl-apk**
3. Sideload `Fve1Ctrl-latest.apk` (povolit neznámé zdroje)

### Lokální build

```bash
cd android-app
./gradlew assembleDebug
```

Výstup: `app/build/outputs/apk/debug/app-debug.apk`

## Firmware

PlatformIO projekt (`platformio.ini`, `src/main.cpp`). Dokumentace: `FVE3_dokumentace.txt`.
