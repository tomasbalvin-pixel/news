# Zprávy

[![Android](https://github.com/tomasbalvin-pixel/news/actions/workflows/android.yml/badge.svg?branch=claude/android-messaging-app-atmauj)](https://github.com/tomasbalvin-pixel/news/actions/workflows/android.yml)

Čtečka zpráv pro Android. Stahuje RSS/Atom kanály, drží je offline v Room databázi
a skládá z nich jeden proud článků tříděný podle času vydání.

## Co umí

- **Zdroje** — seznam kanálů se dá přidávat, mazat, pozastavovat a řadit do rubrik.
  Přidání zdroje kanál nejdřív stáhne a rozparsuje; neplatná adresa se neuloží.
- **Přehled** — sloučený proud přes všechny aktivní zdroje, s náhledovým obrázkem,
  názvem zdroje a relativním časem. Nepřečtené jsou vyznačené.
- **Filtry** — vše / nepřečtené / uložené, plus filtr podle rubriky.
- **Hledání** — nad titulkem a perexem uložených článků, s debounce 200 ms.
- **Čtečka** — titulek, obrázek, obsah kanálu s klikacími odkazy, sdílení,
  uložení mezi záložky a otevření originálu přes Custom Tabs.
- **Offline** — co se jednou stáhlo, jde číst bez sítě.
- **Aktualizace na pozadí** — WorkManager v nastavitelném intervalu (15 min – 12 h),
  volitelně s upozorněním na počet nových článků.
- **Úklid** — články starší než zvolená retence se mažou; uložené zůstávají.
- **Motiv** — podle systému / světlý / tmavý, s dynamickými barvami na Androidu 12+.

## Stack

Kotlin, Jetpack Compose (Material 3), Room, WorkManager, DataStore, OkHttp, Coil,
Navigation Compose. Žádný DI framework — graf závislostí je jeden repository a jeden
settings store, drží ho `AppContainer`.

- `minSdk` 26, `compileSdk`/`targetSdk` 35
- JVM target 17

## Struktura

```
app/src/main/java/cz/balvin/news/
├── AppContainer.kt          ruční DI
├── NewsApplication.kt       seed zdrojů + naplánování refreshe
├── data/
│   ├── local/               Room: entity, DAO, databáze, výchozí zdroje
│   ├── remote/              OkHttp fetch + parser RSS 2.0 / RSS 1.0 / Atom
│   ├── repository/          NewsRepository — jediný vstup k datům
│   └── prefs/               DataStore nastavení
├── work/                    RefreshWorker, plánovač, notifikace
├── ui/                      Compose obrazovky a ViewModely
└── util/                    parsování dat, práce s HTML
```

### Parser

`RssParser` čte DOM bez namespace awareness a porovnává jen lokální část názvu tagu,
takže `content:encoded`, `dc:creator` i `media:thumbnail` fungují bez ohledu na to,
jaký prefix si kanál zvolil. Doctype a externí entity jsou vypnuté — kanál je
nedůvěryhodný vstup. DOM místo pull parseru proto, že se chová stejně na Androidu
i na JVM, takže parser pokrývají běžné unit testy.

Obrázek se hledá v tomto pořadí: `enclosure` typu image → `media:content` /
`media:thumbnail` → `image/url` → první `<img>` v obsahu.

Datum se zkouší proti RFC 822 (číselný offset i pojmenovaná zóna), ISO 8601
s frakcemi i bez, a holému datu. Když nic nesedí, článek dostane čas stažení.

### Šetrnost k serverům

Repository si u každého kanálu drží `ETag` a `Last-Modified` a posílá je zpátky.
Nezměněný kanál tak stojí jednu 304 odpověď místo celého dokumentu. Souběžné
stahování je omezené na čtyři kanály.

## Build

```bash
./gradlew assembleDebug        # APK
./gradlew testDebugUnitTest    # unit testy
./gradlew lintDebug            # lint
```

Stejné tři kroky běží v CI na každý push; podepsané debug APK je ke stažení
jako artefakt `app-debug` z běhu workflow.

## Zdroje

Výchozí seznam v `data/local/DefaultFeeds.kt` se nasadí **jen při prvním spuštění**,
když je tabulka zdrojů prázdná. Pozdější úpravy seznamu patří do aplikace, ne do kódu.
