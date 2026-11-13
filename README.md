# Start TV

Prosty launcher-agregator dla Android TV / Google TV (m.in. Chromecast z Google TV), napisany w Kotlinie.
Ekran składa się z dwóch dużych kafelków obsługiwanych wyłącznie pilotem (D-Pad + OK):

| Kafelek | Tytuł | Podtytuł | Akcja |
| --- | --- | --- | --- |
| 1 | MAGENTA TV | Sport i Republika | uruchamia aplikację `com.pl.tmobile.magentatv`, a gdy jej nie ma — otwiera Google Play |
| 2 | wPOLSCE24 | Darmowy Stream | otwiera transmisję na żywo `https://www.youtube.com/@TelewizjawPolsce24/live` w aplikacji `com.google.android.youtube.tv`, z fallbackiem do dowolnej aplikacji obsługującej link |

## Wymagania

- JDK 17
- Android SDK z platformą **API 36** (compileSdk/targetSdk 36, minSdk 23)
- Gradle pobierany automatycznie przez wrapper (8.14)

`local.properties` (ścieżka do SDK) jest plikiem lokalnym i nie trafia do repozytorium:

```properties
sdk.dir=C\:\\Users\\<użytkownik>\\AppData\\Local\\Android\\Sdk
```

## Budowanie

```bash
./gradlew assembleDebug
```

Windows (PowerShell):

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
.\gradlew.bat assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Pozostałe przydatne zadania:

```bash
./gradlew lintDebug      # analiza statyczna
./gradlew assembleRelease
```

`assembleRelease` wymaga skonfigurowania własnego keystore (`signingConfigs`) — w obecnej konfiguracji minifikacja jest wyłączona, a build release nie jest podpisany.

## Instalacja na Chromecast z Google TV

1. Włącz *Debugowanie USB/ADB* w opcjach programistycznych urządzenia.
2. Połącz się bezprzewodowo (paraowanie na ekranie TV):

   ```bash
   adb connect <IP_TV>:5555
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. Uruchom aplikację:

   ```bash
   adb shell am start -n pl.aleks.starttv/.MainActivity
   ```

Aplikacja pojawia się na ekranie głównym Google TV dzięki kategorii `LEANBACK_LAUNCHER`
i banerowi telewizyjnemu `res/drawable-xhdpi/tv_banner.png` (320 × 180 px, wymóg launchera).

## Struktura

```
app/
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml
    ├── java/pl/aleks/starttv/MainActivity.kt
    └── res/
        ├── drawable/
        │   ├── ic_launcher.xml          # ikona aplikacji (wektor)
        │   ├── tile_magenta_tv.xml      # tło kafelka 1 (#E20074 -> #B0005B)
        │   ├── tile_wpolsce24.xml       # tło kafelka 2 (#1F3A60 -> #15294A)
        │   └── tile_selector.xml        # StateListDrawable: focus / pressed / default
        ├── drawable-xhdpi/tv_banner.png # baner TV 320x180
        ├── layout/activity_main.xml
        ├── values/                      # colors, dimens, strings, themes
        └── xml/data_extraction_rules.xml
```

## Implementacja

- **Układ** — poziomy `LinearLayout` z tłem `#121212`, keyline 48 dp (bezpieczny obszar overscanu) i dwoma kafelkami `layout_weight="1"`.
  Do paddingu dodawane są insety systemowe, więc układ nie nachodzi na paski systemowe na urządzeniach innych niż TV.
- **Fokus D-Pada** — oba kafelki mają `focusable`/`focusableInTouchMode`, jawną nawigację `nextFocusLeft`/`nextFocusRight`
  (lewo/prawo przełącza kafelki w pętli, bez martwych punktów) oraz wyłączone domyślne podświetlenie systemowe.
  Kafelek 1 dostaje focus po dołączeniu do hierarchii widoków (`doOnAttach { requestFocus() }`), a `onResume` przywraca go po powrocie z innej aplikacji.
- **Wskaźnik fokusu** — `tile_selector.xml` (używany jako `android:foreground`) rysuje białą ramkę 6 dp
  i delikatne rozjaśnienie tła; stan `focused + pressed` przyciemnia kafelek, żeby wciśnięcie OK było widoczne
  podczas zimnego startu aplikacji docelowej.
- **Dostępność** — kafelki mają `contentDescription` („MAGENTA TV – Sport i Republika”, „wPOLSCE24 – Darmowy Stream”),
  a teksty potomne są wyłączone z drzewa dostępności, aby TalkBack nie czytał ich dwukrotnie.
- **Widoczność pakietów (Android 11+)** — `MainActivity` używa `getLaunchIntentForPackage` i `setPackage`,
  dlatego manifest zawiera sekcję `<queries>` z `com.pl.tmobile.magentatv` i `com.google.android.youtube.tv`.
  Bez niej oba wywołania zwracałyby `null`/`ActivityNotFoundException` na Google TV.
- **Ścieżki awaryjne** — każdy `startActivity` jest zabezpieczony `try-catch (ActivityNotFoundException)`;
  brak MagentaTV → `Toast` + `market://details?id=com.pl.tmobile.magentatv`
  z fallbackiem na kartę aplikacji w Google Play;
  brak YouTube TV → ten sam link bez wymuszania pakietu.

## Uwagi

- Identyfikator pakietu MagentaTV to `com.pl.tmobile.magentatv` (T-Mobile Polska S.A.) — wersja `pl.tmobile.magentatv`
  nie istnieje w Google Play, więc poprawiony identyfikator jest używany zarówno w kodzie, jak i w `<queries>`.
  Aplikacja jest oficjalnie dostępna również na telewizorach i przystawkach z Android TV.
- Fallback sklepu prowadzi bezpośrednio do karty aplikacji:
  `https://play.google.com/store/apps/details?id=com.pl.tmobile.magentatv`.
- Kafelek 2 otwiera transmisję na żywo kanału (`/@TelewizjawPolsce24/live`); gdy kanał nie nadaje,
  YouTube pokaże jego stronę. Adres transmisji można podmienić w stałej `WPOLSCE24_STREAM_URL`.
- Aplikacja nie deklaruje uprawnienia `INTERNET` — wszystkie treści otwierane są w zewnętrznych aplikacjach.
