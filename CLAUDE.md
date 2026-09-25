# Zprávy — pokyny pro práci na projektu

## Co to je

Čtečka **zpravodajství** — RSS/Atom kanály, ne chat. Potvrzeno zadavatelem.
Větev se jmenuje `claude/android-messaging-app-atmauj`, což svádí k opaku;
je to artefakt pojmenování při zakládání session, ne záměr. Neřiď se jím.

## Jak je to myšlené

Ne nekonečný proud, ale **denní vydání**. Zadavatel výslovně nechce sledovat
zprávy průběžně a nechce jich mnoho. Přehled proto defaultně ukazuje jen to, co
přiteklo od posledního sestavení (`Settings.currentEditionAt`, hranice se měří
podle `fetchedAt`, ne `publishedAt`), shora omezené počtem a stropem na zdroj.
Aktualizace běží jednou denně, notifikace je jedna na vydání. Filtr *Vše* nechá
projít všechno — nic se nemaže, jen se necpe dopředu.

Než přidáš cokoli, co zvyšuje objem nebo četnost upozornění, zvaž, že jde proti
zadání.

## Výtah

Jádro aplikace není seznam, ale **výtah**: vydání se pošle Claudovi
(`claude-opus-5`, strukturovaný výstup) a vrátí se pár témat, o která ve
skutečnosti jde. Články pod ním jsou podklad, ne produkt.

Klíč zadává uživatel v nastavení, leží v DataStore a je vyloučený ze zálohy
(`data_extraction_rules.xml`). Do APK se klíč nikdy nezašívá — z balíčku ho
kdokoli vytáhne.

Selhání výtahu nesmí shodit vydání: články jsou na obrazovce tak jako tak.
`refreshDigest` proto chybu vrací, nevyhazuje, a starý výtah nechá být.

## Instalace na zařízení

**Buildy neposílej přes GitHub Release ani přes artefakty Actions.** Instaluj
rovnou do připojeného telefonu přes kabel:

```bash
./gradlew installDebug        # sestaví a nainstaluje přes adb
adb shell am start -n cz.balvin.news.debug/cz.balvin.news.ui.MainActivity
```

Předpoklady na stroji, kde session běží: `adb` v PATH (Android SDK
platform-tools), na telefonu zapnuté USB ladění a autorizovaný počítač.
Ověření: `adb devices` musí zařízení vypsat jako `device`, ne `unauthorized`.

To vyžaduje, aby Claude Code běžel **lokálně** na počítači s tím kabelem —
CLI v terminálu, desktop aplikace nebo IDE rozšíření. Session spuštěná z webu
běží ve vzdáleném kontejneru bez USB sběrnice a na telefon nedosáhne; v takovém
případě to řekni rovnou, nevymýšlej náhradní cestu.

Debug build z CI je podepsaný jiným klíčem než lokální. Při konfliktu podpisů
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) nejdřív `adb uninstall cz.balvin.news.debug`.

## Zdroje kanálů

`app/src/main/java/cz/balvin/news/data/local/DefaultFeeds.kt` se seeduje jen při
prvním spuštění na prázdné tabulce.

Výběr médií není libovolný: jsou to všechna s hodnocením **A** od Nadačního
fondu nezávislé žurnalistiky (nfnz.cz/rating-medii), jak si zadavatel vyžádal.
Médium s A− a níž tam nepatří — než nějaké přidáš, zeptej se.

Adresy kanálů součástí toho ratingu nejsou a ověřené nejsou; zdroj, který
neodpovídá, se ukáže červeně v záložce *Zdroje*.

## Testy

```bash
./gradlew testDebugUnitTest   # parser, datumy, HTML, DAO a repository (Robolectric)
./gradlew lintDebug
```

Testy datové vrstvy běží na `@Config(application = Application::class)` — bez toho
Robolectric nabootuje `NewsApplication`, ta plánuje WorkManager a ten v unit testu
není inicializovaný.
