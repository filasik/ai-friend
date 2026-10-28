<div align="center">

<img src="docs/assets/icon.svg" width="112" alt="AI Friend">

# AI Friend

**Přátelský AI společník v chatu pro servery Paper.**
Čte veřejný chat a posílá krátké odpovědi jako bot s vaším jménem, jazykem a povahou. Využije OpenAI, Claude, lokální model Ollama nebo libovolný server kompatibilní s OpenAI.

[![Paper](https://img.shields.io/badge/Paper-26.3-1f6feb?style=flat-square)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25-e0a21a?style=flat-square)](https://adoptium.net)
[![License](https://img.shields.io/badge/license-MIT-2ea043?style=flat-square)](LICENSE)

[English](README.md)

</div>

---

> **Soukromí na prvním místě.** Řádky chatu včetně jmen hráčů se odesílají jen AI poskytovateli, kterého si sami zvolíte (OpenAI, Anthropic, vaše Ollama nebo jiný server kompatibilní s OpenAI). Nic neprochází přes autora pluginu a plugin nemá žádný vlastní server. Před zapnutím na veřejném serveru si přečtěte [Soukromí a data](#soukromí-a-data).

## Hlavní vlastnosti

- **Společník v chatu.** Sbírá, co hráči píšou, a v každém intervalu odpoví jednou krátkou zprávou ve tvaru `<chatbot-name>: <odpověď>`.
- **Vaše volba AI.** OpenAI (výchozí), Anthropic Claude, lokální server Ollama nebo libovolný endpoint kompatibilní s OpenAI, s řetězem záložních poskytovatelů a volitelnou cache odpovědí.
- **Chytrý chat, vše volitelné a ve výchozím stavu vypnuté.** Persony, paměť konverzace, limity, odhad nákladů, filtr odpovědí, přímé dotazy (`/ai`, `@Jméno`), vypnutí bota pro jednotlivého hráče a reakce na události ve hře.
- **Dlouhodobá paměť** (volitelná, vypnutá). Bot si pamatuje, co mu hráči o sobě řekli, v SQLite nebo MariaDB; hráči své poznámky vidí a mažou.
- **Integrace.** Veřejné API (`AiFriendResponseEvent`, `AiFriendApi`) a placeholdery PlaceholderAPI, např. `%aifriend_mood%`.
- **Podle vás.** Jméno bota, jazyk odpovědí a volný text `personalization` (web, vtipy, pravidla serveru, ...).
- **Ví, kdy mlčet.** Pokud hráče bot zjevně štve, usne na `sleep-minutes`. Jediný hráč ho nemůže umlčet pro všechny.
- **Neblokuje server.** Síťová volání jsou asynchronní s timeouty, při chybách se prodlužuje pauza (exponenciálně) a každá chyba skončí jedním jasným řádkem v logu.
- **Nízká údržba.** Okomentovaný `config.yml`, který se nikdy nepřepisuje a při aktualizaci dostane nové volby, upravitelné zprávy (angličtina a čeština), upozornění na novou verzi z GitHubu.
- **Kód kompatibilní s Folií.** Žádný `BukkitScheduler`, vše běží přes region/global/async schedulery.

## Rychlý start

1. Dejte `ai-friend-3.0.0.jar` do `plugins/` a spusťte server.
2. V `plugins/ai-friend/config.yml` vyberte poskytovatele a jeho API klíč zadejte do `provider.providers.<název>.api-key`, viz [Nastavení poskytovatele](#nastavení-poskytovatele).
3. Spusťte `/aifriend reload` a pak `/aifriend status`, abyste ověřili, že bot běží.

> Výchozí zástupné hodnoty (`your_openai_api_key_here`, `your_anthropic_api_key_here`) znamenají "nenastaveno": bot mlčí a nic se nikam neodesílá.

## Požadavky

| | |
|---|---|
| Server | Paper **26.3** nebo novější (**jen Paper**; Spigot a čisté Bukkit se od 2.0.0 nepodporují) |
| Java | **25** |
| Síť | Odchozí HTTPS k poskytovateli, kterého používáte: `api.openai.com`, `api.anthropic.com`, váš vlastní endpoint, nebo nic mimo vaši síť u lokální Ollamy. Plus `api.github.com` pro kontrolu aktualizací |
| Klíč | OpenAI klíč, Anthropic klíč, nebo žádný u Ollamy / lokálních serverů bez klíče |

Starý jar 1.x (kompatibilní se Spigotem) zůstává ke stažení v [GitHub releases](https://github.com/filasik/ai-friend/releases).

## Instalace

1. Stáhněte jar z [Modrinthu](https://modrinth.com/plugin/aifriend) nebo [GitHub Releases](https://github.com/filasik/ai-friend/releases).
2. Vložte ho do složky `plugins/` serveru Paper a spusťte server.
3. Upravte `plugins/ai-friend/config.yml` (viz [Konfigurace](#konfigurace)) a spusťte `/aifriend reload`.

První start potřebuje internet, protože zavaděč pluginu jednorázově stáhne knihovny z Maven Central.

## Jak to funguje

1. Každá zpráva z chatu se přidá do omezené fronty (`max-queued-messages`). Řádky umlčených hráčů se přeskočí.
2. Každých `interval-seconds` se fronta odešle jako jedna dávka vybranému poskytovateli: pravidla a persona jako systémová zpráva, řádky chatu jako jasně oddělená data v uživatelské zprávě.
3. Odpověď projde filtrem odpovědí a v chatu se zobrazí na hlavním vlákně jako `<chatbot-name>: <odpověď>`.
4. Zprávy, které přijdou během požadavku, se ponechají pro další dávku. Při chybě se dávka zachová a zkusí se znovu po pauze 1, 2, 4, ... nejvýše 15 minut. S řetězem záložních poskytovatelů se nejdřív zkusí další poskytovatel.

Hráči mohou s botem mluvit i přímo (`/ai <dotaz>`, `@AI Friend ...`); bot pak odpoví hned a nečeká na interval. Přímé dotazy, události i časované odpovědi sdílejí stejné limity, filtr a paměť.

**Usnutí.** Se `stop-when-ignored: true` může model nahlásit, že hráče bot štve. Plugin pak mlčí `sleep-minutes`. Respektuje to jen tehdy, když se dávky účastnili alespoň 2 různí hráči (nebo všichni online hráči, pokud jich je méně), takže jediný hráč nemůže bota umlčet všem. Admin může použít i `/aifriend sleep` a `/aifriend wake`.

**Nedůvěryhodný vstup.** Text hráčů se escapuje do JSONu a posílá se jako jasně oddělená data, nikdy jako instrukce modelu; stejně tak paměť konverzace. Kreativní hráč může model přesto zmást; berte bota jako kteréhokoli účastníka veřejného chatu.

## Nastavení poskytovatele

Nastavte `provider.primary` a vyplňte daného poskytovatele v `provider.providers`. Stačí uvést jen to, co měníte. Poté spusťte `/aifriend reload` a zkontrolujte `/aifriend status`.

| Poskytovatel | `provider.primary` | Potřebuje | Chat jde na |
|---|---|---|---|
| OpenAI (výchozí) | `openai` | `providers.openai.api-key` | `api.openai.com` |
| Anthropic Claude | `anthropic` | `providers.anthropic.api-key` | `api.anthropic.com` |
| Ollama | `ollama` | běžící Ollamu, bez klíče | váš server Ollama (výchozí `localhost`) |
| Vlastní (kompatibilní s OpenAI) | `custom` | `base-url` a `model`, klíč pokud ho server chce | `base-url`, který nastavíte |

**OpenAI**

```yaml
provider:
  primary: openai
  providers:
    openai:
      api-key: "sk-..."
      model: gpt-4o-mini
```

**Anthropic Claude**

```yaml
provider:
  primary: anthropic
  providers:
    anthropic:
      api-key: "sk-ant-..."
      model: claude-haiku-5-5
```

**Ollama (zcela lokálně)**

```yaml
provider:
  primary: ollama
  providers:
    ollama:
      model: llama3.2            # nejdřív spusťte "ollama pull llama3.2"
      base-url: http://localhost:11434
```

**OpenRouter nebo jiný server kompatibilní s OpenAI (LM Studio, vLLM, ...)**

```yaml
provider:
  primary: openrouter
  providers:
    openrouter:
      type: custom               # vlastní název potřebuje typ
      base-url: https://openrouter.ai/api/v1
      api-key: "sk-or-..."
      model: openai/gpt-4o-mini
```

U serveru bez klíče (LM Studio, např. `base-url: http://localhost:1234/v1`) nechte `api-key` prázdný. Místo vlastního názvu můžete použít vestavěné `custom`.

**Záloha (failover).** Když primární poskytovatel selže (chyba, timeout, limit, špatný klíč), zkusí se poskytovatelé z `failover` v pořadí. Selhávající poskytovatel se přeskakuje po dobu `failover-cooldown-seconds`. Poskytovatel s chybějícím nastavením se při startu přeskočí s varováním.

```yaml
provider:
  primary: openai
  failover: [anthropic, ollama]     # Claude, když OpenAI nejede, lokální model jako poslední možnost
  failover-cooldown-seconds: 60
  providers:
    openai:    { api-key: "sk-...",     model: gpt-4o-mini }
    anthropic: { api-key: "sk-ant-..." }
    ollama:    { model: llama3.2 }
```

**Cache.** `provider.cache.enabled: true` si pamatuje odpovědi na shodné požadavky (v paměti, po restartu zmizí). Chat se málokdy opakuje přesně, proto je ve výchozím stavu vypnutá a hodí se hlavně pro opakované přímé dotazy bez paměti.

Poznámky: přesměrování se nikdy nesledují (klíč nemůže odejít na jiný server); velikost odpovědi určuje `max-output-tokens` primárního poskytovatele; příliš malá hodnota může u modelů s uvažováním dát prázdnou odpověď.

## Funkce chatu

Všechny jsou ve výchozím stavu **vypnuté** a zapínají se zvlášť, takže po aktualizaci se nic nezmění, dokud se sami nerozhodnete.

- **Persony** (`personas`): seznam povah (jméno, prompt, volitelně `worlds`, `time: any|day|night`, `weight`). Pro každý požadavek se podle váhy vybere jedna vyhovující persona a její prompt se přidá do systémového promptu. Noc je světový čas 13000-22999.
- **Paměť** (`memory`): posledních `exchanges` výměn se posílá jako historie chatu, omezeno `max-chars`. Stojí víc tokenů na požadavek. Historie je jen v paměti.
- **Limity** (`limits`): `cooldown-seconds` mezi dvěma přímými požadavky hráče, denní volání/tokeny na hráče a na celý server (`0` = bez omezení). Den končí o půlnoci serverového času. Volání se počítá při získání slotu, takže i neúspěšný požadavek jeden spotřeboval. Hráči s `aifriend.bypass-limits` nejsou omezeni. `/aifriend limits` ukáže, co zbývá. Počítadla jsou jen v paměti.
- **Náklady** (`cost`): `/aifriend cost` ukáže dnešní a celkové tokeny a odhad ceny z `input-price-per-million` a `output-price-per-million` (ceny opište z ceníku poskytovatele; měna je vaše). `warn-daily-cost` upozorní adminy s `aifriend.cost` jednou denně. Odhad je tak přesný, jak přesné počty tokenů poskytovatel hlásí; součty se s restartem serveru nulují.
- **Filtr odpovědí** (`reply-filter`): `max-length`, `banned-words` (celá slova, `banned-mode: replace` je změní na `***`, `drop` neodešle nic), `block-urls`. Kódy `§` a řídicí znaky se odstraňují vždy. Detekce odkazů je odrazující opatření, ne bezpečnostní hranice.
- **Přímé dotazy** (`mentions`): `/ai <dotaz>`, `@AI Friend dotaz`, `AI Friend, dotaz`, plus další `triggers`. S `react-anywhere` stačí jméno kdekoli v řádku. S `direct-answer: false` je zmínka jen běžný chat.
- **Opt-out** (`opt-out`): `/aifriend mute` a `unmute` pro jednotlivého hráče. Řádky umlčených hráčů se nikdy neukládají ani neposílají a `/ai` jim řekne, ať se nejdřív vrátí. Seznam se ukládá do `muted-players.yml` ve složce pluginu.
- **Události ve hře** (`events`): bot může okomentovat smrt, achievement, první připojení hráče, zabití Ender Draka nebo Witheru a (je-li zapnuto) velké výbuchy (`explosion-min-power`). Jen jedna reakce za `cooldown-seconds` pro celý server. Výbuchy bývají časté na rušných survival serverech, proto jsou ve výchozím stavu vypnuté.

- **Dlouhodobá paměť** (`long-term-memory`, `storage`): poznámky o hráčích, které přežijí restart. Viz [Dlouhodobá paměť](#dlouhodobá-paměť) a [Databáze](#databáze).
- **Vnímání světa a osobnost** (`server-context`, `mood`, `relationships`, `boredom`, `supervision`): bot vidí denní dobu, počasí a okolí hráčů, má náladu, poznává hráče, v tichém chatu začne konverzaci a přátelsky upozorní na caps lock, spam a nadávky. Viz [Vnímání světa a osobnost](#vnímání-světa-a-osobnost).

Příkazem `/aifriend simulate` uvidíte, jak by vypadal další požadavek a co by bot odpověděl, aniž by se cokoli poslalo do chatu.

## Dlouhodobá paměť

Volitelná a ve výchozím stavu **vypnutá** (`long-term-memory.enabled`). Bot si pamatuje krátká fakta, která o sobě hráči prozradili (co stavějí, co mají rádi, co plánují), a vrací je do dalších odpovědí, takže se po restartu může zeptat „jak se daří hradu?“.

**Jak to funguje.**

1. Po každých `extract-every-exchanges` zodpovězených výměnách (výchozí 6) plugin pošle jeden dodatečný požadavek vašemu AI poskytovateli (stejný řetěz poskytovatelů, počítá se do serverových limitů i do odhadu nákladů). Model dostane poslední řádky chatu hráčů a vrátí nejvýše 3 poznámky po nejvýše 120 znacích, jen trvalé věci, které hráč řekl sám o sobě. Má pokyn ignorovat instrukce v chatu a neukládat nic citlivého (pravé jméno, adresu, věk, kontakty, hesla). Plugin navíc zahodí každou poznámku, která vypadá jako e-mail, telefon, odkaz nebo IP adresa.
2. Poznámky se ukládají podle UUID hráče do [databáze](#databáze) (`storage`), nejvýše `max-notes-per-player` na hráče (nejdéle nepoužité se zahazují) a s `retention-days` ne déle než tolik dní bez použití. Úklid běží jednou denně.
3. Ke každé odpovědi se do požadavku přidá `retrieval-top-k` nejvhodnějších poznámek o hráčích z aktuálního chatu jako řádky `{"note":"...","about":"Jméno"}` v bloku **dat**. Poznámky vycházejí z textu hráčů, takže jsou stejně nedůvěryhodná data jako řádky chatu a nikdy nejsou součástí systémových pravidel. Získávání poznámek nikdy nezdržuje odpověď: běží potom na vlastním úkolu a přeskočí se, když je bot omezený limity, spí nebo čeká po chybě.

**Které poznámky se hodí?** S `embeddings.backend: none` (výchozí) se poznámky hledají podle společných slov, což je zdarma a funguje s každým poskytovatelem. Nastavte `embeddings.backend` na název položky v `provider.providers` (`openai`, `custom` pro libovolný server kompatibilní s OpenAI, nebo `ollama`) a poznámky se budou hledat podle významu; výchozí modely jsou `text-embedding-3-small` (kompatibilní s OpenAI) a `nomic-embed-text` (Ollama), přepsat je lze v `embeddings.model` a `embeddings.base-url`. Anthropic embeddingy nemá, takže použijí slova. Když embedding služba selže, na minutu se použijí slova. Každá poznámka a text každého požadavku se tedy posílá i embedding službě.

**Soukromí.** Viz [Soukromí a data](#soukromí-a-data). Hráči své poznámky ovládají:

- `/aifriend memory` vypíše poznámky o vás, `/aifriend forget` je smaže (funguje i když jste bota ztlumili).
- Ztlumení bota (`/aifriend mute`) zastaví sběr poznámek o vás a s `delete-on-mute: true` (výchozí) je i smaže.
- Admini: `/aifriend memory <hráč>`, `/aifriend forget <hráč>` a `/aifriend forget-all`. Jméno hráče se hledá mezi online hráči nebo podle jména uloženého u poznámek, takže fungují i offline hráči.

`/aifriend reload` použije všechny volby včetně zapnutí a vypnutí funkce.

## Vnímání světa a osobnost

Novinka ve verzi 2.2.0, vše je ve výchozím stavu **vypnuté** a zapíná se zvlášť (`config.yml`, `/aifriend reload` změny použije).

**Kontext serveru** (`server-context`). S každým požadavkem dostane bot krátký obrázek serveru: kolik hráčů je online a **jen u hráčů, kteří psali v aktuální dávce**, jejich svět, biom, nepřátelské moby do `radius` bloků a denní dobu (morning, afternoon, evening, night, dawn) a počasí, **které ten hráč vidí** (`"2 zombie, 1 creeper"`), takže umí říct „pozor, za tebou je creeper“. Čas a počasí se čtou za každého hráče zvlášť, ne jednou za server: jiné světy (Multiverse) mají vlastní den a počasí a pluginy jako Residence, `/ptime` nebo `/pweather` je nastavují jednotlivému hráči, takže vyhrává hodnota hráče. Světy bez střídání dne a noci (Nether, End, vlastní dimenze) žádnou nedají. Obecný čas a počasí se posílají jen když je všichni hráči vidí stejně; řádky hráčů je obsahují jen tam, kde se liší. Nikdy nedostane souřadnice, inventáře, adresy ani nic o hráčích, kteří nepsali. Obrázek se obnovuje každých `refresh-seconds` na vlákně, které data vlastní (globální úloha a jedna úloha na hráče, takže je bezpečný pro Folii), bot čte jen hotovou kopii. `time`, `weather`, `world`, `biome` a `nearby-mobs` jde vypnout jednotlivě. Vše jde s chatem k vašemu AI poskytovateli, viz [Soukromí a data](#soukromí-a-data).

**Nálada** (`mood`). Číslo od -100 do 100, které se s poločasem (`half-life-minutes`) vrací do klidu. Úspěchy (+8), první připojení hráče (+10) a zabití Ender Draka nebo Witheru (+35) bota rozveselí; smrt (-12), tři smrti během pěti minut (navíc -25) a tři výbuchy síly 3 a víc během dvou minut (vypadá jako griefing, navíc -20) ho srazí. Úroveň (thrilled, cheerful, calm, uneasy, upset) jde do systémového promptu jako nápověda k tónu; nikdy nemění, co bot smí. Funguje i s vypnutými reakcemi `events`, ukazuje ji `/aifriend status` a `%aifriend_mood%` a po restartu začíná klidná.

**Vztahy** (`relationships`). Bot počítá zodpovězené výměny, kterých se hráč účastnil, a podle toho s ním zachází: cizinec, známý (`acquaintance-after`, výchozí 5), štamgast (`regular-after`, 25) a kamarád (`friend-after`, 80). Hráči, kteří nejsou cizinci, jsou v datovém bloku jako `{"relationship":"friend","player":"Alex","known_for_days":12,"nickname":"Ally"}`; pravidla modelu říkají, ať je k kamarádům a štamgastům vřelejší a použije přezdívku. Přezdívku si hráč volí `/aifriend nickname <text>` (písmena, číslice, mezera a `_ - . '`, nejvýše 24 znaků, a kontroluje se i filtrem odpovědí, protože ji bot může říct nahlas; `/aifriend nickname clear` ji smaže, `/aifriend nickname` ukáže, jak bot hráče vidí). Záznamy jsou v [databázi](#databáze) (tabulka `aifriend_relationships`: UUID, poslední jméno, počítadlo, první a poslední čas, přezdívka), načtou se při startu a ukládají se na pozadí každou minutu a při vypnutí; bez databáze žijí v paměti. `/aifriend forget`, `forget <hráč>`, `forget-all` a ztlumení bota je mažou.

**Nuda** (`boredom`). Když nikdo (hráči ani bot) nenapsal `quiet-minutes` minut (výchozí 10), může bot začít konverzaci, vybranou z `ideas`: `topic`, `quiz`, `challenge`, `joke`, `fact`. Podnět je pokyn v důvěryhodné systémové části, takže ho hráč nemůže řídit. Stane se to jen když je **online aspoň `min-players` hráčů** (výchozí 1: bot mluví i s jediným hráčem; zvyšte, pokud ho chcete jen na rušnějších serverech), nikdy na prázdném serveru (klid se počítá od příchodu prvního hráče, ne od odchodu posledního), nikdy když bot spí, je omezený limity nebo čeká po chybě, a nejvýše `max-per-day`krát denně. Mezi dvěma podněty uplyne aspoň `cooldown-minutes` a ta doba se (až osmkrát) zdvojnásobuje za každý podnět, na který nikdo nereagoval, takže prostě prázdný chat není otravován. Podněty se počítají do limitů i odhadu nákladů jako každá odpověď.

**Dohled nad chatem** (`supervision`). Přátelské **soukromé** upozornění hráči, který píše caps lockem (`caps-min-letters` písmen, z toho `caps-percent` velkých), spamuje (stejný řádek třikrát nebo šest řádků za deset sekund) nebo použije slovo ze `bad-words` (celá slova; akcenty, podobné znaky jako `@` nebo `0` a natažená písmena se ignorují; seznam je ve výchozím stavu prázdný, aby plugin nevnucoval jazyk). O stejném problému se hráč dozví nejvýše každých `cooldown-seconds`. Nic dalšího se neděje: žádný kick, mute ani ban, upozornění nikdo jiný nevidí, žádný chat se neukládá (detekce spamu drží v paměti jen hashe posledních řádků hráče po deset sekund) a nevolá se žádná AI. Hráči s `aifriend.bypass-supervision` a hráči, kteří bota ztlumili, se přeskakují. Tři texty jsou v `messages_<locale>.yml` (`supervision.caps`, `supervision.spam`, `supervision.language`).

## Databáze

Sekce `storage` nastavuje databázi pro funkce, které si ukládají data (zatím jen dlouhodobá paměť; dokud jsou vypnuté, nic se nevytváří). Dva typy:

- `sqlite` (výchozí): jeden soubor ve složce pluginu (`storage.sqlite.file`, výchozí `ai-friend.db`). Bez nastavování.
- `mariadb`: server MariaDB nebo MySQL (`storage.mariadb.*`: host, port, database, user, password, pool-size, `ssl-mode`). Hodí se ke sdílení poznámek mezi servery sítě. Tabulky mají předponu `aifriend_`, takže mohou být ve sdílené databázi.

JDBC ovladače a connection pool se stahují z Maven Central spolu s ostatními knihovnami při prvním startu. Veškerý přístup k databázi je asynchronní, nikdy na hlavním vlákně. Schéma je verzované (tabulka `aifriend_schema_version`) a při startu se samo povýší; další verze pouze přidávají nové kroky. Když se databázi nepodaří otevřít, zapíše se jeden řádek a vypne se jen dlouhodobá paměť, bot běží dál.

## Integrace

**Veřejné API pro jiné pluginy.**

- `AiFriendResponseEvent` se vyvolá těsně před odesláním odpovědi bota do chatu. Je **asynchronní** (mimo hlavní vlákno, posluchači musí být thread-safe a nesmí sahat na svět) a zrušitelný; `setText(...)` změní odesílaný text (výsledek se znovu očistí a zkrátí na `max-reply-length`). Obsahuje jméno bota, text, zdroj (`TIMED` nebo `DIRECT`) a tazatele (UUID nebo null).
- `AiFriendApi` se po dokončení startu registruje v Bukkit `ServicesManager`: `state()` (`RUNNING`, `DISABLED`, `NO_KEY`, `SLEEPING`, `BACKOFF`), `lastMessage()`, `provider()`, `queuedLines()`, `sleep(Duration)` a `wake()`.

```java
AiFriendApi api = Bukkit.getServicesManager().load(AiFriendApi.class); // null, dokud se AI Friend nespustí
```

**PlaceholderAPI** (volitelná závislost, nic se nenastavuje). Je-li PlaceholderAPI nainstalované, zaregistruje se expanze `aifriend`:

| Placeholder | Hodnota |
|---|---|
| `%aifriend_state%` | `running`, `disabled`, `no-key`, `sleeping`, `backoff` |
| `%aifriend_mood%` | S `mood.enabled` nálada: `thrilled`, `cheerful`, `calm`, `uneasy`, `upset`. Jinak `happy` (běží), `sleepy` (spí), `confused` (čeká po chybě), `off` |
| `%aifriend_relationship%` | Jak dobře bot hráče zná: `stranger`, `acquaintance`, `regular`, `friend` (prázdné, když je `relationships` vypnuté) |
| `%aifriend_nickname%` | Přezdívka, kterou si hráč zvolil (prázdné, pokud žádná) |
| `%aifriend_lastmessage%` | Poslední věc, kterou bot řekl |
| `%aifriend_provider%` | Id poskytovatele, který odpovídá (`-` když žádný) |
| `%aifriend_queued%` | Řádky chatu čekající na další odpověď |
| `%aifriend_muted%` | `true` / `false`: hráč bota ztlumil |
| `%aifriend_calls_left%` | Kolik dotazů hráči dnes zbývá, nebo `unlimited` |

## Příkazy

`/aifriend`. Další aliasy lze přidat v `command.aliases` (nutný restart). `/aifriendreload` zůstává jako starý alias pro `/aifriend reload`. Admin příkazy fungují z konzole, k dispozici je tab doplňování.

| Příkaz | Co dělá | Oprávnění |
|---|---|---|
| `/aifriend` | Verze pluginu a krátká nápověda | `aifriend.use` |
| `/aifriend status` | Stav (zapnuto, spí, pauza po chybě), poskytovatel, persona, paměť, umlčení hráči, maskovaný API klíč, velikost fronty | `aifriend.admin` |
| `/aifriend reload` | Znovu načte `config.yml` a zprávy | `aifriend.reload` |
| `/ai <dotaz>` | Zeptá se bota přímo (hráči) | `aifriend.ask` |
| `/aifriend nickname [text\|clear]` | Zvolte přezdívku, kterou vám smí bot říkat, smažte ji nebo zjistěte, jak vás bot zná (vyžaduje `relationships.enabled`) | `aifriend.nickname` |
| `/aifriend limits` | Vaše zbývající volání, tokeny a cooldown (hráči) | `aifriend.ask` |
| `/aifriend mute` / `unmute` | Vypne / zapne bota pro vás (hráči, vyžaduje `opt-out.enabled`) | `aifriend.mute` |
| `/aifriend memory` | Vypíše poznámky, které si bot o vás pamatuje (vyžaduje dlouhodobou paměť) | `aifriend.mute` |
| `/aifriend forget` | Smaže vaše poznámky a to, co o vás bot ví (funguje i když jste bota ztlumili) | `aifriend.mute` |
| `/aifriend memory <hráč>` | Vypíše poznámky o hráči (i offline) | `aifriend.admin` |
| `/aifriend forget <hráč>` | Smaže poznámky hráče | `aifriend.admin` |
| `/aifriend forget-all` | Smaže všechny poznámky | `aifriend.admin` |
| `/aifriend sleep [minuty]` | Bot mlčí 1-1440 minut (výchozí `sleep-minutes`) | `aifriend.sleep` |
| `/aifriend wake` | Probudí bota | `aifriend.sleep` |
| `/aifriend simulate [nocall]` | Zkušební běh: ukáže personu, požadavek, velikost historie, odhad tokenů, co brání odpovědi a vyfiltrovanou odpověď. `nocall` přeskočí volání poskytovatele, takže nestojí nic | `aifriend.simulate` |
| `/aifriend cost` | Součty tokenů a odhad nákladů (vyžaduje `cost.enabled`) | `aifriend.cost` |

## Oprávnění

| Oprávnění | Výchozí | |
|---|---|---|
| `aifriend.use` | všichni | Základní info `/aifriend` |
| `aifriend.admin` | op | `/aifriend status`, upozornění na aktualizace, `/aifriend forget <hráč>`, `forget-all`, `memory <hráč>` |
| `aifriend.reload` | op | `/aifriend reload` |
| `aifriend.mute` | všichni | `/aifriend mute`, `unmute` a vlastní `memory` a `forget` |
| `aifriend.ask` | všichni | `/ai`, `/aifriend limits`, oslovení bota v chatu |
| `aifriend.sleep` | op | `/aifriend sleep`, `wake` |
| `aifriend.simulate` | op | `/aifriend simulate` |
| `aifriend.cost` | op | `/aifriend cost`, denní upozornění na náklady |
| `aifriend.bypass-limits` | op | Nevztahuje se na něj cooldown ani denní limity |
| `aifriend.nickname` | všichni | `/aifriend nickname` |
| `aifriend.bypass-supervision` | op | Žádná přátelská upozornění na caps lock, spam a jazyk |

## Konfigurace

`plugins/ai-friend/config.yml` je kompletně okomentovaný. Nikdy se nepřepisuje; při aktualizaci pluginu se volby přidané novou verzí připojí i s komentáři (`config-version`), vaše hodnoty zůstanou a nejdřív se uloží záloha `config.yml.bak-v<stará>`. Neplatné hodnoty se nahradí výchozími s varováním v logu. `/aifriend reload` použije většinu změn (aliasy vyžadují restart).

| Klíč | Výchozí | Popis |
|---|---|---|
| `config-version` | `3` | Spravuje plugin, neměňte |
| `locale` | `en` | Jazyk zpráv pluginu: `en` nebo `cs` |
| `update-check` | `true` | Kontrola nové verze na GitHubu (async, s cache) |
| `metrics` | `true` | Anonymní statistiky bStats |
| `command.aliases` | `[]` | Další aliasy pro `/aifriend` (nutný restart) |
| `enabled` | `true` | Zapne nebo vypne bota |
| `chatbot-name` | `AI Friend` | Jméno před odpověďmi |
| `language` | `English` | Jazyk odpovědí bota (volný text, např. `Czech`) |
| `personalization` | prázdné | Volné instrukce pro bota: web, vtipy, tón |
| `interval-seconds` | `60` | Jak často se dávka odesílá. Rozsah 5 až 3600 |
| `stop-when-ignored` | `true` | Nechat bota usnout, když hráče štve |
| `sleep-minutes` | `30` | Jak dlouho bot spí |
| `max-queued-messages` | `50` | Horní mez fronty chatu (5-500) |
| `max-reply-length` | `256` | Delší odpovědi se zkrátí (20-1000) |
| `http.connect-timeout-seconds` | `10` | Timeout navázání spojení (1-120) |
| `http.read-timeout-seconds` | `30` | Timeout čtení (1-120) |
| `provider.primary` | `openai` | `openai`, `anthropic`, `ollama`, `custom` nebo vámi definovaný název |
| `provider.failover` | `[]` | Poskytovatelé k vyzkoušení v pořadí, když primární selže |
| `provider.failover-cooldown-seconds` | `60` | Jak dlouho se selhávající poskytovatel přeskakuje |
| `provider.cache.enabled` | `false` | Pamatovat si odpovědi na shodné požadavky |
| `provider.cache.max-entries` | `200` | Velikost cache |
| `provider.cache.ttl-minutes` | `10` | Jak dlouho se odpověď z cache používá |
| `provider.providers.openai.api-key` | zástupná hodnota | OpenAI klíč |
| `provider.providers.openai.model` | `gpt-4o-mini` | Model OpenAI |
| `provider.providers.openai.base-url` | `https://api.openai.com/v1` | Měňte jen pro kompatibilní bránu |
| `provider.providers.openai.max-output-tokens` | `200` | 16-4000 |
| `provider.providers.anthropic.api-key` | zástupná hodnota | Anthropic klíč |
| `provider.providers.anthropic.model` | `claude-haiku-5-5` | Model Claude |
| `provider.providers.anthropic.base-url` | `https://api.anthropic.com` | |
| `provider.providers.anthropic.max-output-tokens` | `200` | 16-4000 |
| `provider.providers.ollama.model` | `llama3.2` | Lokální model |
| `provider.providers.ollama.base-url` | `http://localhost:11434` | |
| `provider.providers.ollama.max-output-tokens` | `200` | 16-4000 |
| `provider.providers.custom.model` | prázdné | U `custom` povinné |
| `provider.providers.custom.base-url` | prázdné | Povinné, např. `https://openrouter.ai/api/v1` |
| `provider.providers.custom.api-key` | prázdné | U serverů bez klíče nechte prázdné |
| `provider.providers.custom.max-output-tokens` | `200` | 16-4000 |
| `provider.providers.<název>.type` | | Pro vlastní názvy: podle jakého druhu se chovat, např. `custom` |
| `personas.enabled` | `false` | Používat persony |
| `personas.list` | dva příklady | Každá: `name`, `prompt`, `worlds` (prázdné = všechny), `time` (`any`/`day`/`night`), `weight` (0-1000) |
| `memory.enabled` | `false` | Posílat historii konverzace |
| `memory.exchanges` | `5` | Zapamatované výměny (0-50) |
| `memory.max-chars` | `2000` | Pevný limit zapamatovaných znaků (100-20000) |
| `limits.enabled` | `false` | Používat limity |
| `limits.cooldown-seconds` | `30` | Čekání mezi dvěma požadavky hráče |
| `limits.player-daily-calls` | `50` | Na hráče a den, `0` = bez omezení |
| `limits.player-daily-tokens` | `0` | Na hráče a den |
| `limits.global-daily-calls` | `0` | Celý server a den (i automatické odpovědi a události) |
| `limits.global-daily-tokens` | `0` | Celý server a den |
| `cost.enabled` | `false` | Sledovat tokeny a odhadovat náklady |
| `cost.input-price-per-million` | `0.15` | Cena 1M vstupních tokenů |
| `cost.output-price-per-million` | `0.60` | Cena 1M výstupních tokenů |
| `cost.warn-daily-cost` | `0` | Upozornit adminy jednou denně při této ceně, `0` = nikdy |
| `reply-filter.enabled` | `false` | Použít zakázaná slova, odstranění odkazů a `max-length` |
| `reply-filter.max-length` | `256` | Při zapnutí přebíjí `max-reply-length` (20-1000) |
| `reply-filter.banned-words` | `[]` | Celá slova, bez ohledu na velikost písmen |
| `reply-filter.banned-mode` | `replace` | `replace` nebo `drop` |
| `reply-filter.block-urls` | `false` | Odstranit odkazy a webové adresy |
| `mentions.enabled` | `false` | `/ai` a oslovení v chatu |
| `mentions.direct-answer` | `true` | Odpovědět na oslovení hned |
| `mentions.triggers` | `[]` | Další jména, která bota volají |
| `mentions.react-anywhere` | `false` | Stačí jméno kdekoli v řádku |
| `opt-out.enabled` | `false` | Povolit `/aifriend mute` |
| `opt-out.file` | `muted-players.yml` | Kam se ukládají umlčení hráči |
| `events.enabled` | `false` | Reagovat na události ve hře |
| `events.cooldown-seconds` | `30` | Jedna reakce za tuto dobu, pro celý server |
| `events.death` | `true` | Smrt hráčů |
| `events.advancement` | `true` | Achievementy oznámené v chatu |
| `events.first-join` | `true` | Hráč se připojí poprvé |
| `events.boss-kill` | `true` | Zabití Ender Draka a Witheru |
| `events.explosion` | `false` | Velké výbuchy |
| `events.explosion-min-power` | `6` | Nejmenší výbuch, který se počítá |
| `storage.type` | `sqlite` | `sqlite` nebo `mariadb` (funguje i MySQL) |
| `storage.sqlite.file` | `ai-friend.db` | SQLite soubor ve složce pluginu |
| `storage.sqlite.pool-size` | `3` | Spojení s databází (1-32) |
| `storage.mariadb.host` / `port` | `localhost` / `3306` | Server MariaDB |
| `storage.mariadb.database` / `user` / `password` | `aifriend` / `aifriend` / `change-me` | Přihlašovací údaje; `config.yml` držte v soukromí |
| `storage.mariadb.pool-size` | `6` | Spojení s databází (1-64) |
| `storage.mariadb.ssl-mode` | prázdné | např. `trust` pro self-signed certifikát |
| `long-term-memory.enabled` | `false` | Pamatovat si fakta o hráčích ([podrobnosti](#dlouhodobá-paměť)) |
| `long-term-memory.extract-every-exchanges` | `6` | Jeden dodatečný požadavek poskytovateli za tolik odpovědí (1-1000) |
| `long-term-memory.max-notes-per-player` | `20` | Nejdéle nepoužité poznámky se zahazují (1-500) |
| `long-term-memory.retention-days` | `0` | Smazat poznámky nepoužité tak dlouho, `0` = ponechat (0-36500) |
| `long-term-memory.retrieval-top-k` | `5` | Poznámek v jednom požadavku (1-50) |
| `long-term-memory.delete-on-mute` | `true` | Smazat poznámky hráče, který bota ztlumí |
| `long-term-memory.embeddings.backend` | `none` | `none` (hledání podle slov) nebo název v `provider.providers` |
| `long-term-memory.embeddings.model` | prázdné | Prázdné = `text-embedding-3-small` / `nomic-embed-text` |
| `long-term-memory.embeddings.base-url` | prázdné | Prázdné = `base-url` poskytovatele |

Příklad:

```yaml
enabled: true
chatbot-name: "Buddy"
language: "Czech"
personalization: "Jsi přátelský pomocník serveru example.com. Novým hráčům zmiň /spawn."
interval-seconds: 90
```

## Zprávy a lokalizace

Zprávy jsou v `plugins/ai-friend/messages_<locale>.yml` (MiniMessage) a vybírají se přes `locale`. V balíčku jsou angličtina (`en`) a čeština (`cs`). Soubory můžete libovolně upravit; chybějící klíč se vezme z vestavěného textu daného jazyka a poté z angličtiny. Nový jazyk: zkopírujte `messages_en.yml` na `messages_<kód>.yml` a nastavte `locale: <kód>`.

Jazyk, kterým *mluví bot*, je samostatné nastavení: `language`.

## Soukromí a data

**Čte se jen veřejný chat.** Soukromé zprávy (`/msg`, `/tell`, `/r`), ostatní příkazy, zrušené zprávy a zprávy, které chatový plugin doručí jen části hráčů (staff, party nebo lokální kanály), se nikdy neshromažďují ani nikam neposílají. Výjimkou je dotaz, který hráč botovi úmyslně položí příkazem `/ai`. Plugin chatové řádky nezapisuje do vlastního logu.

Co opustí váš server, závisí na `provider.primary` (a `provider.failover`). Požadavek obsahuje poslední řádky chatu od předchozího požadavku **včetně jmen hráčů**, prompt (jméno bota, jazyk, personalizace, persona), paměť konverzace je-li zapnutá a krátké popisy událostí ve hře je-li zapnuto. Dokud není použitelný žádný poskytovatel nebo je `enabled` na `false`, neodesílá se nic.

| Poskytovatel | Co se posílá a kam |
|---|---|
| `openai` | HTTPS přímo na `api.openai.com` (nebo váš `base-url`), klíč v hlavičce `Authorization`. Nikdo mezi tím. Platí podmínky OpenAI |
| `anthropic` | HTTPS přímo na `api.anthropic.com`, klíč v hlavičce `x-api-key`. Nikdo mezi tím. Platí podmínky Anthropic |
| `ollama` | Jen na váš server Ollama (výchozí `localhost`). Pokud Ollama běží u vás, nic neopouští váš stroj; pokud `base-url` ukážete jinam, chat jde tam |
| `custom` | Na `base-url`, který jste nastavili (OpenRouter, váš server, ...). Provozovatel endpointu chat vidí |

Upozornění:

- **Chat vašich hráčů se posílá třetí straně**, pokud nepoužíváte lokální Ollamu nebo vlastní server. Provozujete-li veřejný server, uveďte to v pravidlech nebo zásadách ochrany soukromí a v oznámeních, která vyžaduje vaše jurisdikce.
- S řetězem záložních poskytovatelů jde chat tomu, kdo nakonec odpoví; každý člen řetězu je potenciální příjemce.
- API klíče jsou jen v `config.yml`, v čistém textu jako každé nastavení pluginu. Soubor držte v soukromí. Klíče jsou maskované v `/aifriend status` a odstraněné z chybových hlášek i logu; posílají se jen v hlavičkách požadavku svému poskytovateli. Přesměrování se nesledují.
- Umlčení hráči (`opt-out`) se nikdy neukládají ani neposílají. Seznam (`muted-players.yml`) obsahuje UUID hráčů na vašem serveru.
- **Vnímání světa** (vše ve výchozím stavu vypnuté). *Kontext serveru* posílá s každým požadavkem denní dobu, počasí, počet hráčů online a u hráčů, kteří psali, jejich svět, biom a nepřátelské moby poblíž (nikdy souřadnice) vašemu AI poskytovateli. *Vztahy* ukládají ve vaší databázi UUID hráče, poslední jméno, počet zodpovězených výměn, první a poslední čas a volitelnou přezdívku, kterou hráč napsal; `/aifriend forget` je smaže, ztlumení bota je smaže a zastaví sběr, a přezdívka s úrovní jdou poskytovateli spolu s řádky chatu daného hráče. *Nálada* a *nuda* drží v paměti jen počítadla. *Dohled nad chatem* čte veřejné řádky jen v paměti (nevolá AI, nic neukládá a hráče, kteří bota ztlumili, nesleduje).
- Paměť konverzace, limity, součty nákladů a cache jsou jen v paměti a s restartem zmizí (výjimkou je dlouhodobá paměť, viz níže).
- **Dlouhodobá paměť** (ve výchozím stavu vypnutá) je jediná funkce, která ukládá data o hráčích. Ve vaší vlastní databázi (`storage`) ukládá: UUID hráče, jméno hráče, krátkou poznámku (max 120 znaků, kterou z toho, co hráč řekl sám o sobě, napsal model), kdy vznikla a kdy byla naposledy použita, a případně embedding vektor poznámky. Nic dalšího z chatu se neukládá. **K nalezení poznámek se každých `extract-every-exchanges` odpovědí posílá dodatečný požadavek s posledními řádky chatu (se jmény hráčů) vašemu AI poskytovateli**, a s embeddingy jdou poznámky a text chatu i embedding službě. Řekněte to hráčům. Model má pokyn nic citlivého neukládat a poznámky podobné kontaktním údajům plugin zahodí, ale model se může splést: v případě pochybností zkontrolujte `/aifriend memory <hráč>`. Hráči poznámky vidí a mažou přes `/aifriend memory` a `/aifriend forget`, admini přes `forget <hráč>` a `forget-all`, ztlumení zastaví sběr a (ve výchozím stavu) poznámky smaže a `retention-days` odstraňuje staré. S MariaDB leží poznámky na vašem databázovém serveru a sdílí je všechny servery, které ji používají.
- Kromě výše uvedeného jsou jedinými dalšími síťovými voláními anonymní statistiky [bStats](https://bstats.org) (vypnete `metrics: false`) a kontrola aktualizací na GitHubu (vypnete `update-check: false`). Žádná další telemetrie neexistuje.

## Aktualizace

**Z 2.x na 3.0.0 (zásadní změna).** Poskytovatel, který 2.x používala jako výchozí (zprostředkovatel provozovaný autorem pluginu), a jeho bezplatný 3denní klíč jsou pryč, stejně jako volby `api-key` a `model` na nejvyšší úrovni: klíče a modely jsou nyní v `provider.providers.<název>`. Plugin nikdy sám nepřepne váš chat k jiné firmě:

- Pokud `config.yml` v `provider.primary` stále vybírá odstraněného poskytovatele, nebo má `api-key` na nejvyšší úrovni a poskytovatele nikdy nevybral, bot zůstane **neaktivní a nic neodesílá**; log to řekne jedním řádkem. Pokračujte nastavením `provider.primary` na `openai`, `anthropic`, `ollama` nebo `custom`, doplněním `api-key` toho poskytovatele (viz [Nastavení poskytovatele](#nastavení-poskytovatele)) a `/aifriend reload`.
- Odstraněný název v `provider.failover` se přeskočí s varováním.
- `api-key` na nejvyšší úrovni, který v souboru zůstal, se ignoruje (s varováním) a nikam se nekopíruje. Po přesunu klíče ho smažte.
- `config-version` se změní na 5; nejdřív se uloží záloha `config.yml.bak-v<stará>` a žádná vaše hodnota se nezmění.

Přesné staré názvy jsou v [CHANGELOGu](CHANGELOG.md).

**Z 2.1.0.** Vyměňte jar. Do `config.yml` se doplní `config-version: 4` a nové sekce (`server-context`, `mood`, `relationships`, `boredom`, `supervision`) s komentáři; nejdřív se uloží záloha `config.yml.bak-v3`. Schéma databáze se při dalším startu povýší na v2 (jedna nová tabulka, přidá se při příštím otevření databáze, což se děje jen se zapnutou dlouhodobou pamětí nebo vztahy; stávající poznámky zůstanou). Nic se nezmění, dokud něco nezapnete.

**Z 2.0.0.** Vyměňte jar. Do `config.yml` se doplní `config-version: 3` a nové sekce (`provider`, `personas`, `memory`, `limits`, `cost`, `reply-filter`, `mentions`, `opt-out`, `events`, `storage`, `long-term-memory`) s komentáři; nejdřív se uloží záloha `config.yml.bak-v1`. Nic se nezmění, dokud něco nezapnete: všechny funkce chatu jsou vypnuté. Pak postupujte podle poznámek k 3.0.0 výše.

**Z 1.x.** Verze 2.0.0 byla přepis. Vyměňte jar; složka `plugins/ai-friend/` je stejná, takže nastavení zůstane.

- Staré klíče se při prvním startu převedou automaticky (stará konfigurace se zálohuje jako `config.yml.bak-v0`): `chatbot_name` -> `chatbot-name`, `preffered_language` -> `language`, `timer_interval` -> `interval-seconds`, `player_dissinterest` -> `stop-when-ignored`; `enabled` a `personalization` si názvy ponechaly. Klíče z 1.x `openai_api_key` a `gpt_version` se **nepřevádějí** (patřily odstraněnému zprostředkovateli): klíč zadejte do `provider.providers.openai`.
- **Nyní je nutný Paper.** 1.x běžela i na Spigotu, 2.x ne. Na Spigotu zůstaňte u 1.4.1.
- `/aifriend` má podpříkazy; `/aifriendreload` stále funguje. Příkazy vyžadují oprávnění místo statusu operátora.

Podrobnosti v [CHANGELOG](CHANGELOG.md).

## Co ještě chybí

Přímá volání OpenAI/Claude/Ollamy/vlastního endpointu, výše popsané funkce chatu, dlouhodobá paměť, veřejné API a expanze PlaceholderAPI jsou hotové. Stále v plánu (viz [ROADMAP.md](ROADMAP.md)): hook Dynmap, uchování cache, limitů a nákladů přes restart a další fáze (vnímání světa, hry, pomoc hráčům, nástroje pro adminy).

## Časté dotazy a řešení potíží

**Bot nic neříká.** Zkontrolujte `/aifriend status`:
- *Klíč nenastaven* / *žádný použitelný poskytovatel*: nastavte klíč svého poskytovatele (zástupná hodnota znamená nenastaveno) a `/aifriend reload`. Varování v logu řeknou, co chybí.
- *Vypnuto*: `enabled: false` v configu.
- *Spí*: bot usnul kvůli otráveným hráčům, nebo admin použil `/aifriend sleep`; `/aifriend wake` ho probudí.
- *Pauza po chybě*: poslední požadavky selhaly, další pokus se odkládá (až 15 minut). V logu je řádek ke každému selhání s důvodem.
- Od posledního intervalu nikdo nic nenapsal (bez zpráv se nic neposílá), nebo jsou všichni autoři umlčeni.
- Byly dosaženy limity (`/aifriend limits`).

**V logu je `401 Unauthorized`.** Klíč byl odmítnut. Zkontrolujte překlepy a mezery, že je klíč aktivní a má kredit, že patří vybranému poskytovateli a že jste nenechali výchozí zástupnou hodnotu.

**Funguje to na Spigotu nebo Bukkitu?** Ne. Použijte 1.4.1 nebo přejděte na Paper.

**Funguje to na Folii?** Kód používá schedulery kompatibilní s Folií; plugin je deklarován jako Paper a na Folii zatím nebyl testován.

**Jde chat přes server autora pluginu?** Ne. Plugin nemá žádný vlastní server; chat jde jen poskytovateli, kterého nastavíte. S Ollamou na stejném stroji nic neopustí váš server.

**Kterého poskytovatele zvolit?** Pro nejnižší náklady a nejmenší práci `openai` s `gpt-4o-mini` nebo `anthropic` s malým modelem Claude; pro soukromí lokální `ollama` (chce výkonný stroj); pro výběr z mnoha modelů `custom` s OpenRouter.

**`/ai` hlásí, že je vypnuté.** Zapněte `mentions.enabled`. Podobně `/aifriend mute` a `opt-out.enabled`.

**Bot odpovídá moc často / stojí moc.** Zvyšte `interval-seconds`, zapněte `limits` a `cost`, `memory` nechte vypnutou nebo malou a použijte levný model.

**Čte bot soukromé zprávy nebo příkazy?** Sbírá jen veřejné zprávy z chatu.

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
