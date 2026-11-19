# Storage and Database

The `storage` section configures the database for the features that keep data: [long-term memory](Long-Term-Memory) and [relationships](World-Awareness-and-Personality#relationships). **Nothing is created while they are off.**

## SQLite (default)

```yaml
storage:
  type: sqlite
  sqlite:
    file: ai-friend.db
    pool-size: 3
```

One file in the plugin folder. No setup.

## MariaDB / MySQL

```yaml
storage:
  type: mariadb
  mariadb:
    host: localhost
    port: 3306
    database: aifriend
    user: aifriend
    password: change-me
    pool-size: 6
    ssl-mode: ""
```

Use it to share notes and relationships between the servers of a network. Tables are prefixed `aifriend_`, so they can live in a shared database.

Create the database and user first, for example:

```sql
CREATE DATABASE aifriend CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'aifriend'@'%' IDENTIFIED BY 'a-strong-password';
GRANT ALL PRIVILEGES ON aifriend.* TO 'aifriend'@'%';
```

### `ssl-mode`

| Value | Meaning |
| :--- | :--- |
| empty | Driver default |
| `trust` | Encrypted, certificate not verified. Use it for MySQL 8+ with the default self-signed certificate (error: *Cannot use authentication plugin caching_sha2_password with a Self signed certificates*) |
| `disable` | No TLS, for a trusted local network |
| `verify-ca`, `verify-full` | Need a proper certificate |

## Tables

| Table | Holds |
| :--- | :--- |
| `aifriend_schema_version` | Schema version, upgraded automatically on start |
| `aifriend_player_notes` | Long-term notes: player UUID, name, note, created / last used, optional embedding |
| `aifriend_relationships` | UUID, last name, exchange counter, first and last time, nickname |

## Behaviour

- JDBC drivers and the HikariCP pool are downloaded from Maven Central with the other libraries on the first start.
- All database access is asynchronous, never on the main thread.
- The schema is versioned and upgraded automatically; later releases only append new steps.
- If the database cannot be opened, one line is logged and only long-term memory and saved relationships stay off. The bot keeps running.
- `/aifriend reload` reopens the database after a change in `storage`.

## Not in the database

These live in files or in memory, not in the database:

| Data | Where |
| :--- | :--- |
| Muted players | `muted-players.yml` |
| Taught facts | `knowledge-taught.txt` |
| Learned facts | `knowledge-learned.json` |
| Conversation memory, limits, cost totals, cache, mood | Memory only, lost on restart |
