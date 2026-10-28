<div align="center">

<img src="docs/assets/icon.svg" width="112" alt="AI Friend">

# AI Friend

**Přátelský AI společník v chatu pro servery Paper.**
Čte veřejný chat a jednou za minutu pošle jednu krátkou odpověď jako bot s vaším jménem, jazykem a povahou.

[![Paper](https://img.shields.io/badge/Paper-26.3-1f6feb?style=flat-square)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25-e0a21a?style=flat-square)](https://adoptium.net)
[![License](https://img.shields.io/badge/license-MIT-2ea043?style=flat-square)](LICENSE)

[English](README.md)

</div>

---

> **Soukromí na prvním místě.** Když je bot aktivní, poslední řádky chatu (se jmény hráčů) se odesílají třetí straně. Před zapnutím na veřejném serveru si přečtěte [Soukromí a data](#soukromí-a-data).

## Hlavní vlastnosti

- **Společník v chatu.** Sbírá, co hráči píšou, a v každém intervalu odpoví jednou krátkou zprávou ve tvaru `<chatbot-name>: <odpověď>`.
- **Podle vás.** Jméno bota, jazyk odpovědí a volný text `personalization` (web, vtipy, pravidla serveru, ...).
- **Ví, kdy mlčet.** Pokud hráče bot zjevně štve, usne na `sleep-minutes`. Jediný hráč ho nemůže umlčet pro všechny (viz [Jak to funguje](#jak-to-funguje)).
- **Neblokuje server.** Síťová volání jsou asynchronní s timeouty, při chybách se prodlužuje pauza (exponenciálně) a každá chyba skončí jedním jasným řádkem v logu.
- **Zkušební klíč.** Speciální klíč `trial` funguje 3 dny, bez účtu.
- **Nízká údržba.** Okomentovaný `config.yml`, který se nikdy nepřepisuje a při aktualizaci dostane nové volby, upravitelné zprávy (angličtina a čeština), upozornění na novou verzi z GitHubu.
- **Kód kompatibilní s Folií.** Žádný `BukkitScheduler`, vše běží přes region/global/async schedulery.

## Rychlý start

1. Dejte `ai-friend-2.0.0.jar` do `plugins/` a spusťte server.
2. V `plugins/ai-friend/config.yml` nastavte `api-key: "trial"` (3 dny zdarma) nebo vlastní OpenAI klíč.
3. Spusťte `/aifriend reload` a pak `/aifriend status`, abyste ověřili, že bot běží.

> Výchozí hodnota `your_openai_api_key_here` znamená "nenastaveno": bot mlčí a nic se nikam neodesílá.

## Požadavky

| | |
|---|---|
| Server | Paper **26.3** nebo novější (**jen Paper**; Spigot a čisté Bukkit se od 2.0.0 nepodporují) |
| Java | **25** |
| Síť | Odchozí HTTPS na `gpt.netuj.eu` (a `api.github.com` pro kontrolu aktualizací) |
| Klíč | `trial` nebo OpenAI API klíč |

Starý jar 1.x (kompatibilní se Spigotem) zůstává ke stažení v [GitHub releases](https://github.com/filasik/ai-friend/releases).

## Instalace

1. Stáhněte jar z [Modrinthu](https://modrinth.com/plugin/ai-friend) nebo [GitHub Releases](https://github.com/filasik/ai-friend/releases).
2. Vložte ho do složky `plugins/` serveru Paper a spusťte server.
3. Upravte `plugins/ai-friend/config.yml` (viz [Konfigurace](#konfigurace)) a spusťte `/aifriend reload`.

První start potřebuje internet, protože zavaděč pluginu jednorázově stáhne knihovny z Maven Central.

## Jak to funguje

1. Každá zpráva z chatu se přidá do omezené fronty (`max-queued-messages`).
2. Každých `interval-seconds` se fronta odešle jako jedna dávka AI backendu spolu s vaším nastavením (jméno bota, jazyk, personalizace).
3. Odpověď se v chatu zobrazí na hlavním vlákně jako `<chatbot-name>: <odpověď>` (zkrácená na `max-reply-length` znaků).
4. Zprávy, které přijdou během požadavku, se ponechají pro další dávku. Při chybě se dávka zachová a zkusí se znovu po pauze 1, 2, 4, ... nejvýše 15 minut.

**Usnutí.** Se `stop-when-ignored: true` může model nahlásit, že hráče bot štve. Plugin pak mlčí `sleep-minutes`. Respektuje to jen tehdy, když se dávky účastnili alespoň 2 různí hráči (nebo všichni online hráči, pokud jich je méně), takže jediný hráč nemůže bota umlčet všem.

**Nedůvěryhodný vstup.** Text hráčů se escapuje do JSONu a posílá se jako jasně oddělená data, nikdy jako instrukce modelu. Kreativní hráč může model přesto zmást; berte bota jako kteréhokoli účastníka veřejného chatu.

## Příkazy

`/aifriend`. Další aliasy lze přidat v `command.aliases` (nutný restart). `/aifriendreload` zůstává jako starý alias pro `/aifriend reload`. Všechny příkazy fungují z konzole a mají tab doplňování.

| Příkaz | Co dělá | Oprávnění |
|---|---|---|
| `/aifriend` | Verze pluginu a krátká nápověda | `aifriend.use` |
| `/aifriend status` | Stav (zapnuto, spí, pauza po chybě), maskovaný API klíč (např. `sk-...abcd`), velikost fronty | `aifriend.admin` |
| `/aifriend reload` | Znovu načte `config.yml` a zprávy | `aifriend.reload` |

## Oprávnění

| Oprávnění | Výchozí | |
|---|---|---|
| `aifriend.use` | všichni | Základní info `/aifriend` |
| `aifriend.admin` | op | `/aifriend status`, upozornění na aktualizace |
| `aifriend.reload` | op | `/aifriend reload` |

## Konfigurace

`plugins/ai-friend/config.yml` je celý okomentovaný. Nikdy se nepřepisuje; při aktualizaci pluginu se volby přidané novou verzí doplní i s komentáři (`config-version`), vaše hodnoty zůstanou a nejdřív se uloží záloha `config.yml.bak-v<stará>`. Neplatné hodnoty se nahradí výchozími s varováním v logu.

| Klíč | Výchozí | Popis |
|---|---|---|
| `locale` | `en` | Jazyk zpráv pluginu: `en` nebo `cs` |
| `update-check` | `true` | Kontrola nové verze na GitHubu (asynchronně, s cache) |
| `metrics` | `true` | Anonymní statistiky bStats |
| `command.aliases` | `[]` | Další aliasy `/aifriend` (nutný restart) |
| `enabled` | `true` | Zapnutí/vypnutí bota |
| `api-key` | `your_openai_api_key_here` | `trial` pro 3denní zkoušku, nebo váš OpenAI klíč. Výchozí hodnota znamená "nenastaveno" |
| `model` | `gpt-4o-mini` | Název modelu. S klíčem `trial` se ignoruje |
| `chatbot-name` | `AI Friend` | Jméno před odpovědí |
| `language` | `English` | Jazyk odpovědí bota (volný text, např. `Czech`) |
| `personalization` | prázdné | Volné instrukce pro bota: web, vtipy, tón |
| `interval-seconds` | `60` | Jak často se dávka odesílá. Povoleno 5 až 3600 |
| `stop-when-ignored` | `true` | Nechat bota usnout, když hráče zjevně štve |
| `sleep-minutes` | `30` | Jak dlouho bot spí |
| `max-queued-messages` | `50` | Horní mez fronty chatu (limit paměti) |
| `max-reply-length` | `256` | Delší odpovědi se zkrátí |
| `http.connect-timeout-seconds` | `10` | Timeout připojení |
| `http.read-timeout-seconds` | `30` | Timeout čtení |

Příklad:

```yaml
enabled: true
api-key: "trial"
chatbot-name: "Kamarád"
language: "Czech"
personalization: "Jsi přátelský pomocník serveru example.com. Novým hráčům připomeň /spawn."
interval-seconds: 90
```

## Zprávy a lokalizace

Zprávy jsou v `plugins/ai-friend/messages_<locale>.yml` (MiniMessage) a vybírají se přes `locale`. V balíčku jsou angličtina (`en`) a čeština (`cs`). Soubory můžete libovolně upravit; chybějící klíč se vezme z vestavěného textu daného jazyka a poté z angličtiny. Nový jazyk: zkopírujte `messages_en.yml` na `messages_<kód>.yml` a nastavte `locale: <kód>`.

Jazyk, kterým *mluví bot*, je samostatné nastavení: `language`.

## Soukromí a data

Když je bot aktivní, AI Friend posílá HTTPS požadavky na proxy `https://gpt.netuj.eu/api/ai_friend.php`. **Proxy provozuje autor pluginu** a předává požadavek poskytovateli AI. Požadavek obsahuje:

- poslední řádky chatu od posledního požadavku **včetně jmen hráčů**,
- prompt (jméno bota, jazyk, váš text personalizace),
- váš `api-key` (`trial` nebo OpenAI klíč),
- název `model`,
- adresu vašeho serveru, jak ji vidí proxy (IP adresa, ze které se připojuje).

Upozornění:

- **Chat vašich hráčů se odesílá třetí straně** (proxy a poskytovateli AI za ní). Na veřejném serveru to uveďte v pravidlech nebo zásadách ochrany soukromí a splňte informační povinnosti podle vaší jurisdikce.
- **Nic se neodesílá**, pokud je `api-key` nenastaven nebo je `enabled` `false`.
- Jediná další síťová volání jsou anonymní statistiky [bStats](https://bstats.org) (vypnete `metrics: false`) a kontrola aktualizací na GitHubu (vypnete `update-check: false`). Žádná jiná telemetrie neexistuje.
- API klíč je v `config.yml` uložen jako prostý text, stejně jako jiná nastavení pluginů. V `/aifriend status` se zobrazuje maskovaný.

I s vlastním OpenAI klíčem požadavek jde přes proxy. Přímý přístup k OpenAI a další poskytovatelé jsou v [roadmapě](ROADMAP.md), zatím nejsou implementováni.

## Přechod z 1.x

2.0.0 je přepis. Nový jar dejte na místo starého; složka `plugins/ai-friend/` je stejná, takže se nastavení zachová.

- Staré klíče se při prvním startu migrují automaticky (starý soubor se zálohuje): `openai_api_key` -> `api-key`, `gpt_version` -> `model`, `chatbot_name` -> `chatbot-name`, `preffered_language` -> `language`, `timer_interval` -> `interval-seconds`, `player_dissinterest` -> `stop-when-ignored`; `enabled` a `personalization` si názvy ponechávají.
- **Nyní je nutný Paper.** 1.x běželo i na Spigotu, 2.0.0 ne. Na Spigotu zůstaňte na 1.4.1.
- `/aifriend` má nově podpříkazy (`status`, `reload`); `/aifriendreload` stále funguje. Příkazy používají oprávnění místo statusu opa.

Podrobnosti v [CHANGELOG](CHANGELOG.md).

## Časté dotazy a řešení potíží

**Bot nic neříká.** Zkontrolujte `/aifriend status`:
- *API klíč nenastaven*: nastavte `api-key` (výchozí `your_openai_api_key_here` = nenastaveno) a `/aifriend reload`.
- *Vypnuto*: `enabled: false` v configu.
- *Spí*: bot usnul kvůli otráveným hráčům; probudí se po `sleep-minutes`.
- *Pauza po chybě*: poslední požadavky selhaly, další pokus je odložen (až 15 minut). V logu je jeden řádek s důvodem u každé chyby.
- Od posledního intervalu nikdo nic nenapsal (bez zpráv se nic neodesílá).

**`401 Unauthorized` v logu.** Klíč byl odmítnut. Zkontrolujte překlepy a mezery, že je OpenAI klíč aktivní a má kredit a že jste nenechali výchozí zástupný text.

**Zkušební klíč přestal fungovat.** Klíč `trial` platí 3 dny. Poté použijte vlastní OpenAI klíč.

**Funguje to na Spigotu nebo Bukkitu?** Ne. Použijte 1.4.1 nebo přejděte na Paper.

**Funguje to na Folii?** Kód používá schedulery kompatibilní s Folií; plugin je deklarován pro Paper a na Folii zatím nebyl testován.

**Dá se to používat bez posílání chatu na vaši proxy?** Zatím ne. Přímý přístup k poskytovatelům je v roadmapě.

**Čte bot soukromé zprávy nebo příkazy?** Sbírá pouze veřejné zprávy chatu.

## Sestavení ze zdrojů

```bash
./gradlew build         # JDK 25, varování jsou chyby, spustí testy, jar v build/libs
./gradlew test          # jen unit testy
./gradlew runServer     # lokální testovací Paper (-PrunDir=<cesta> použije existující adresář serveru)
```

## Roadmapa

Plánovaná práce je v [ROADMAP.md](ROADMAP.md).

## Autoři - Podpora

- Filasik - Discord ID: filasik
- Patr1xCZ - Discord ID: paprik_man

Chyby a nápady: [GitHub issues](https://github.com/filasik/ai-friend/issues). Web: [minebook.eu](https://minebook.eu).

Plugin je zdarma a nenabízí žádné výhody ve hře. Odkazy na podporu sem přibudou, až budou existovat.

## Licence

[MIT](LICENSE) © Filip Skerik
