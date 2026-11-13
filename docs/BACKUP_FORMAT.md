# Backup format

A backup (Settings → Backup → Export) is a zip archive.

| Entry | Since | Content |
|-------|-------|---------|
| `Database.json` | before 4.0 | Entries.db tables: `{"<table>":[{column:value,…},…],…}` |
| `Settings.json` | before 4.0 | shared preferences `Settings`: `{"<key>":{"<TYPE>":value}}` |
| `ScrapedTags.json` | before 4.0 | shared preferences `ScrapedTags`, same shape |
| `EHentaiBackup.json` | 4.7.0 | ehentai.db (e-hentai history, local favorites, downloads) |

`<TYPE>` is one of `STRING`, `INT`, `LONG`, `FLOAT`, `BOOLEAN`, `STRING_SET`.

## EHentaiBackup.json (4.7.0)

The entry uses the shared-preferences shape on purpose:

```json
{
  "payload": {"STRING": "{\"format\":1,\"tables\":{\"history\":[…],\"favorites\":[…],\"downloads\":[…]}}"},
  "version": {"INT": 1}
}
```

`payload` holds the ehentai.db tables as a JSON string. Each row lists the table's columns:

- all tables: `gid`, `token`, `ex` (0/1), `title`, `thumb`, `category` (e-hentai category bit), `pages`
- `history`: `last_page`, `read_count`, `read_seconds`, `last_read` (epoch ms)
- `favorites`: `added` (epoch ms)
- `downloads`: `state` (0 queued, 1 running, 2 done, 3 failed, 4 paused), `done`, `added`

Compatibility:

- **Old backup → 4.7.0:** there is no `EHentaiBackup.json`; the import works as before and
  ehentai.db is left as it is.
- **4.7.0 backup → older build:** older importers read every entry other than `Database.json`
  as shared preferences. Because of the shape above they store it as an unused preferences
  file named `EHentaiBackup` and the rest of the import is unaffected. The e-hentai data is
  not restored there.
- **4.7.0 backup → 4.7.0:** rows are merged into ehentai.db; a row with the same `gid`
  replaces the local one. Unknown tables and columns are ignored. Downloaded image files are
  not in the backup; a restored download whose files are missing reads online.
- e-hentai login cookies (`EHentaiAuth` preferences) are **never** exported.
- Entries.db, its tables and `Database.json` are unchanged.
