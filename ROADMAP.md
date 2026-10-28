# AI Friend – plán vývoje

Tento dokument shrnuje opravy a nové funkce vzešlé z code review a brainstormingu. Je řazený podle priority; fáze na sebe navazují, ale body uvnitř fáze jdou dělat nezávisle.

Legenda: ⬜ nezačato · 🔧 rozpracováno · ✅ hotovo

---

## Fáze 1 – Stabilita a bezpečnost (kritické opravy)

Cíl: plugin nepadá, nezamrzá a neuniká z něj nic citlivého.

### 1.1 Vlákna a HTTP
- ✅ Nahradit `java.util.Timer` Bukkit schedulerem (`runTaskTimerAsynchronously`).
- ✅ Odpověď do chatu odesílat zpět na hlavní vlákno (`runTask`), ne z vlákna timeru.
- ✅ Nastavit connect a read timeout na HTTP spojení.
- ✅ Zachytávat všechny výjimky při volání API (výpadek sítě už nesmí ukončit smyčku).
- ✅ Backoff při opakovaných chybách (např. 1 → 2 → 4 min, max 15 min).

### 1.2 Sdílený stav
- ✅ Nahradit `mutableListOf()` thread-safe frontou (`ConcurrentLinkedQueue`) s limitem velikosti.
- ✅ Při odesílání odebrat jen zprávy, které byly skutečně odeslány (neztrácet zprávy přijaté během HTTP volání).

### 1.3 JSON a prompt injection
- ✅ Skládat i parsovat JSON knihovnou (Gson) místo string templates a regexu na `\uXXXX`.
- ✅ Oddělit systémové instrukce od hráčského textu (proxy přijímá jediný `message`, takže: hráčské řádky jsou escapované JSON v ohraničeném bloku označeném jako nedůvěryhodná data; samostatné role přijdou s přímým voláním OpenAI ve Fázi 4).
- ✅ Nespoléhat na `contains("STOP MESSAGING")` – strukturovaná odpověď (např. JSON pole `action`).
- ✅ Ochrana proti tomu, aby jeden hráč napsáním magické fráze vypnul bota všem.

### 1.4 Příkazy a oprávnění
- ✅ Opravit `/aifriendreload`: neregistrovat listenery znovu, rušit staré timery, validovat `timer_interval`.
- ✅ Zrušit i 30minutový „spánek“ timer při reloadu/disable (`onDisable` musí vše uklidit).
- ✅ Maskovat API klíč ve výpisu `/aifriend` (např. `sk-…abcd`).
- ✅ Povolit příkazy i z konzole (`sender !is Player`).
- ✅ Přejít z `isOp` na permissions (`aifriend.admin`, `aifriend.reload`) v `plugin.yml`.
- ✅ Tab-complete pro příkazy.

### 1.5 Konfigurace
- ✅ `saveDefaultConfig()` + komentovaný `config.yml` v resources místo přepisování souboru při každém startu.
- ✅ Zachovat uživatelovy komentáře a neznámé klíče; přidat `config_version` a migrace.
- ✅ Zpětná kompatibilita se starými klíči: při prvním startu 2.0.0 se automaticky přejmenují (záloha `config.yml.bak-v0`).
- ✅ Přestat stínit `JavaPlugin.config` vlastní proměnnou.

### 1.6 UpdateChecker
- ✅ Verzi číst z `plugin.description.version` (dnes hardcoded `1.4.0` vs. `1.4.1` v `pom.xml`).
- ✅ Kontrola asynchronně, při startu neblokovat server; výsledek cachovat (ne HTTP při každém joinu opa).
- ✅ Porovnávat verze sémanticky, ne jako řetězce.

### 1.7 Důvěrnost
- ✅ Zdokumentovat v README, že požadavky jdou přes `gpt.netuj.eu`, a co se tam ukládá.
- ⬜ Volitelný režim přímého volání OpenAI s vlastním klíčem (viz Fáze 4).

---

## Fáze 2 – Úklid repa a kvalita

- ✅ Odstranit `target/`, `dependency-reduced-pom.xml` a `.idea/` z gitu, doplnit `.gitignore`.
- ✅ Kotlin odstraněn: přepis na Java 25 + Gradle podle `_template` (verze 2.0.0, jen Paper).
- ✅ Sloučit duplicitní ANSI konstanty do jednoho helperu; nahradit deprecated `ChatColor` (Adventure/MiniMessage tam, kde to Spigot/Paper dovolí).
- ✅ Rozdělit `AiFriend.kt` na menší celky: `ConfigManager`, `AiClient`, `ChatListener`, `Commands`, `Scheduler`.
- ✅ README: opravit rozdíly proti kódu (`openai_chatbot_name` → `chatbot_name`, chybějící `gpt_version`, `player_dissinterest`), přidat sekci o oprávněních a soukromí.
- ✅ Unit testy (parsování odpovědí, sestavení promptu, rate limit) a GitHub Actions CI (build + testy).
- ✅ Changelog (`CHANGELOG.md`) a sémantické verzování.

---

## Fáze 3 – Základní nové funkce (nejlepší poměr užitečnost/práce)

- ⬜ **Oslovení bota** (`@AI Friend` / jménem) a příkaz `/ai <dotaz>` pro přímou otázku.
- ⬜ **Reakce na události**: smrt hráče, achievement, první join, Ender Dragon / Wither, výbuch.
- ⬜ **Persony** v configu (pirát, rytíř, sarkastický robot, moudrý stařec, Creeper…), případně dle denní doby či světa.
- ⬜ **Per-hráč opt-out**: `/aifriend mute` a `unmute` (bez vypnutí bota všem).
- ⬜ **Cooldown a rate limit** pro hráče + denní limit volání/tokenů.
- ⬜ **Filtr odpovědí**: max. délka natvrdo, odstranění `§` kódů, zakázaná slova.
- ⬜ **Dynamická doba spánku** (`sleep_minutes`) a příkaz `/aifriend sleep|wake|status`.
- ⬜ **Testovací režim**: `/aifriend simulate` ukáže odpověď bez odeslání do chatu.
- ⬜ **Náklady**: `/aifriend cost` + upozornění při překročení limitu.
- ⬜ **Paměť konverzace**: posledních N výměn jako chat historie místo jediného `lastMessage`.
- ✅ **Lokalizace hlášek** pluginu (`messages.yml`).

---

## Fáze 4 – Poskytovatelé a technické rozšíření

- ⬜ Abstrakce `AiProvider` (OpenAI přímo, stávající proxy, Anthropic, Ollama / lokální modely, vlastní endpoint).
- ⬜ Failover mezi poskytovateli.
- ⬜ Cache odpovědí na opakované otázky.
- ⬜ Dlouhodobá paměť (SQLite + embeddings), bot si pamatuje hráče a jejich historii.
- ⬜ Veřejné API a eventy (`AiFriendResponseEvent`) pro ostatní pluginy.
- 🔧 bStats (zapojeno ze šablony; chybí registrace na bstats.org a `bstatsId`).
- ⬜ PlaceholderAPI (`%aifriend_mood%`, `%aifriend_lastmessage%`).

---

## Fáze 5 – Vnímání světa a osobnost

- ⬜ **Kontext ze serveru**: online hráči, svět, biom, počasí, denní doba, blízcí moby.
- ⬜ **Nálada bota** měnící se podle dění (úspěchy, hromadná úmrtí, griefing).
- ⬜ **Vývoj vztahu** s hráčem (přezdívky, vtipy pro zasvěcené).
- ⬜ **Rozpoznání nudy**: při tichém serveru navrhne téma, kvíz nebo výzvu.
- ⬜ **Dohled nad chatem**: přátelské upozornění na spam, caps lock a nadávky (bez automatických banů).
- ⬜ **Více botů najednou** s vlastními personami, které spolu občas diskutují.

---

## Fáze 6 – Hry a zábava

- ⬜ Kvízy o Minecraftu s body, žebříčkem a odměnami.
- ⬜ Hádanky, kámen–nůžky–papír, piškvorky v chatu.
- ⬜ Lov pokladu (zašifrované nápovědy; ukrytí truhly vždy se schválením admina).
- ⬜ Denní úkoly generované AI s odměnami podle pravidel v configu.
- ⬜ Kronika serveru (týdenní souhrn, kniha ve hře / Discord).
- ⬜ Textová multiplayer RPG s botem jako vypravěčem.

---

## Fáze 7 – Pomoc hráčům

- ⬜ Průvodce pro nováčky (pravidla, příkazy, ochrana pozemku, spawn).
- ⬜ Dotazy na recepty a mechaniky s ověřenými daty (wiki) místo halucinací.
- ⬜ Překladač chatu (hráč píše česky, ostatní vidí svůj jazyk).
- ⬜ Podpora hráčů („Jak se dostanu domů?“ → návrh `/home`) a vytvoření ticketu pro adminy.
- ⬜ FAQ / RAG z dokumentace a pravidel serveru zadaných adminem.

---

## Fáze 8 – Nástroje pro adminy

- ⬜ Denní souhrn chatu (o čem se hráči bavili, co je trápí) do Discordu / e-mailu.
- ⬜ Detekce toxicity a podvodů se skóre a návrhem pro moderátora.
- ⬜ Návrhy eventů při poklesu aktivity.
- ⬜ Příkazový režim v přirozené řeči (**vždy jen po potvrzení admina**).
- ⬜ Webový panel: nastavení, logy, náklady na API, historie odpovědí.

---

## Fáze 9 – Integrace a kreativní rozšíření

- ⬜ Discord most (chat i hlas).
- ⬜ Hlasový chat (Simple Voice Chat): TTS a přepis řeči.
- ⬜ Vault / ekonomika (odměny, mince za úkoly).
- ⬜ Dynmap („Kde je Franta?“ – jen se souhlasem hráče).
- ⬜ Webhooky ven (Twitch, Telegram, Slack).
- ⬜ NPC přes Citizens (obchodník, hospodský, stařešina) s vlastní osobností.
- ⬜ Generování jmen a příběhových popisků itemů, vlastní questy a mapy.
- ⬜ Básně, písničky a hádanky na přání (oslavy, výročí hráčů).
- ⬜ Oznámení na cedulích / v bossbaru (restart serveru, počasí).

---

## Zásady pro všechny funkce

- Cokoli, co spouští příkazy nebo mění svět, vyžaduje potvrzení admina a je ve výchozím stavu vypnuté.
- Každá nová funkce má vlastní přepínač v `config.yml` a oprávnění v `plugin.yml`.
- Hráčský obsah se nikdy nevkládá do systémových instrukcí.
- Každá funkce s voláním API respektuje cooldown, limity a timeouty z Fáze 1 a 3.
