The Metabase plugin allows you to orchestrate and automate [Metabase](https://www.metabase.com/) business intelligence operations within Kestra workflows.

## Features

- **Databases**: Synchronize database schemas and rescan field values after ELT or ETL jobs complete (`databases.SyncSchema`, `databases.RescanValues`).
- **Cards & Questions**: Execute saved cards/questions (`cards.Run`) and capture results into Kestra outputs or store them in Kestra internal storage.
- **Queries**: Execute native SQL queries (`queries.Run`) against your Metabase data sources directly.
- **Dashboards**: Fetch dashboard metadata and card configurations (`dashboards.Get`, `dashboards.List`), and trigger executions when dashboards are created or updated (`dashboards.Trigger`).

## Authentication

All tasks in this plugin support:
- `apiKey`: Metabase API Key passed in the `x-api-key` header (recommended).
- `sessionToken`: Metabase Session Token passed in the `X-Metabase-Session` header.
- `username` & `password`: Automatically obtains a session token via `POST /api/session`.
