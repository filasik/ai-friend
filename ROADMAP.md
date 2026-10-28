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
- ✅ Volitelný přímý režim (OpenAI, Anthropic, Ollama, vlastní endpoint) od 2.1.0; hráčský text je tam v samostatné roli.

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

- ✅ **Oslovení bota** (`@AI Friend` / jménem) a příkaz `/ai <dotaz>` pro přímou otázku.
- ✅ **Reakce na události**: smrt hráče, achievement, první join, Ender Dragon / Wither, výbuch.
- ✅ **Persony** v configu (pirát, rytíř, sarkastický robot, moudrý stařec, Creeper…), případně dle denní doby či světa.
- ✅ **Per-hráč opt-out**: `/aifriend mute` a `unmute` (bez vypnutí bota všem).
- ✅ **Cooldown a rate limit** pro hráče + denní limit volání/tokenů.
- ✅ **Filtr odpovědí**: max. délka natvrdo, odstranění `§` kódů, zakázaná slova.
- ✅ **Dynamická doba spánku** (`sleep_minutes`) a příkaz `/aifriend sleep|wake|status`.
- ✅ **Testovací režim**: `/aifriend simulate` ukáže odpověď bez odeslání do chatu.
- ✅ **Náklady**: `/aifriend cost` + upozornění při překročení limitu.
- ✅ **Paměť konverzace**: posledních N výměn jako chat historie místo jediného `lastMessage`.
- ✅ **Lokalizace hlášek** pluginu (`messages.yml`).

---

## Fáze 4 – Poskytovatelé a technické rozšíření

Verze 2.1.0 (Fáze 3 a 4). Přímé volání OpenAI a Anthropic je zatím ověřené jen proti lokálním testovacím serverům; živě je vyzkoušený OpenAI-kompatibilní endpoint (DeepSeek) a SQLite.

- ✅ Abstrakce `AiProvider` (OpenAI přímo, Anthropic, Deepseek, Openrouter, Ollama / lokální modely, vlastní endpoint).
- ✅ Failover mezi poskytovateli.
- ✅ Cache odpovědí (v paměti, ve výchozím stavu vypnutá; pro chat je shoda zřídka).
- ✅ Dlouhodobá paměť (SQLite nebo MariaDB/MySQL, embeddings přes OpenAI/Ollama/vlastní endpoint, jinak klíčová slova): bot si pamatuje krátké poznámky o hráčích, `/aifriend memory|forget|forget-all`, retence a limit poznámek. Ve výchozím stavu vypnuté; MariaDB zatím neotestována na živém serveru.
- ✅ Veřejné API a eventy: `AiFriendResponseEvent` (asynchronní, zrušitelný, lze změnit text) a `AiFriendApi` přes Bukkit ServicesManager.
- ✅ bStats (`bstatsId` 34987).
- ✅ PlaceholderAPI (volitelná závislost): `%aifriend_state%`, `%aifriend_mood%`, `%aifriend_lastmessage%`, `%aifriend_provider%`, `%aifriend_queued%`, `%aifriend_muted%`, `%aifriend_calls_left%`. `mood` je zatím odvozená ze stavu bota (happy/sleepy/confused/off), skutečná nálada patří do Fáze 5.

---

## Fáze 5 – Vnímání světa a osobnost

Verze 2.2.0 (kromě více botů). Všechno je ve výchozím stavu vypnuté.

- ✅ **Kontext ze serveru** (`server-context`): denní doba, počasí, počet hráčů online a u hráčů, kteří právě psali, svět, biom a blízcí nepřátelští moby (bez souřadnic).
- ✅ **Nálada bota** (`mood`) měnící se podle dění: úspěchy a zabití bosse ji zvedají, smrti (hromadné víc) a opakované výbuchy (griefing) snižují; časem se vrací do klidu. Ukazuje ji `/aifriend status` a `%aifriend_mood%`.
- ✅ **Vývoj vztahu** s hráčem (`relationships`): cizinec → známý → štamgast → kamarád podle počtu zodpovězených výměn, přezdívka podle volby hráče (`/aifriend nickname`), uloženo v databázi (migrace v2), mazání přes `/aifriend forget`.
- ✅ **Rozpoznání nudy** (`boredom`): v tichém chatu navrhne téma, kvíz, výzvu, vtip nebo zajímavost. Jen když je online aspoň `min-players` hráčů (výchozí 1), nikdy na prázdném serveru, s cooldownem, který roste, když nikdo nereaguje, a denním limitem.
- ✅ **Dohled nad chatem** (`supervision`): přátelské soukromé upozornění na caps lock, spam a nadávky (bez automatických banů, nic se neukládá).
- ⬜ **Více botů najednou** s vlastními personami, které spolu občas diskutují (odloženo, bude se promýšlet).

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
