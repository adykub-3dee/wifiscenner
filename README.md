# Wi‑Fi Analyzer Android v1.0

Natívna Android verzia analyzátora Wi‑Fi. Sken vykonáva priamo telefón cez Android `WifiManager`; HTML/Canvas vrstva slúži iba ako responzívne používateľské rozhranie.

## Čo appka robí

- skenuje okolité Wi‑Fi siete, nielen aktuálne pripojenú sieť,
- zobrazuje SSID, BSSID, RSSI, frekvenciu, kanál, pásmo a šírku kanála,
- rozlišuje 2,4 / 5 / 6 GHz podľa schopností telefónu,
- vytvára spektrálny graf,
- hodnotí kanály a ukazuje jasný verdikt: pásmo + kanál + odporúčaná šírka,
- pole **Moje siete** (predvolene `ADD3, ADD5`) vyradí vlastné AP zo skóre cudzieho rušenia,
- pri 2,4 GHz hodnotí kanály 1 / 6 / 11 a odporúča 20 MHz,
- automatický sken je nastavený na 30 sekúnd kvôli Android scan throttlingu,
- ak systém nový scan obmedzí, aplikácia použije posledné dostupné výsledky a označí ich ako cache/throttled.

## Kompatibilita

- minimálne Android 8.0 (API 26),
- target/compile SDK 35,
- telefón musí mať Wi‑Fi,
- 5 GHz/6 GHz sa zobrazí iba ak ho podporuje telefón,
- na Android 10+ musia byť pre Wi‑Fi scan zapnuté služby **Poloha/Location**,
- aplikácia si vyžiada potrebné runtime oprávnenia.

## Zostavenie APK cez GitHub Actions

Projekt obsahuje `.github/workflows/build-apk.yml`.

1. Nahraj celý obsah projektu do GitHub repozitára.
2. Push do `main` automaticky spustí build.
3. V GitHub -> Actions otvor `Build Android APK`.
4. V artefaktoch stiahni `WiFi-Analyzer-Android-v1.0`.
5. V ZIPe bude `app-debug.apk`.
6. Prenes APK do Android telefónu a povoľ inštaláciu z daného zdroja.

Debug APK je pre osobné používanie normálne inštalovateľný. Pre publikovanie v Play Store by sa vytvoril release podpis a AAB.

## Android Studio

Projekt nemá externé knižnice. Otvor koreňový priečinok v Android Studio, nechaj dokončiť Gradle Sync a použi **Build > Build APK(s)**.

## Súkromie

Aplikácia neposiela výsledky nikam na internet. Nemá povolenie INTERNET. Scan a analýza sa vykonávajú lokálne v telefóne.
