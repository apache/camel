# HTTP

The HTTP tab shows all HTTP endpoints exposed by this integration and
lets you send test requests interactively — a lightweight Postman built
into the terminal. This includes REST API endpoints, management endpoints
(health, metrics), and any other HTTP routes.

## Endpoint List

- **METHOD** — HTTP method: `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, etc.
- **PATH** — URL path for this endpoint
- **TOTAL** — Number of HTTP requests received since startup
- **CONSUMES** — Content-Type this endpoint accepts
- **PRODUCES** — Content-Type this endpoint returns
- **SOURCE** — How the endpoint was registered: REST(code), REST(contract), HTTP, Management
- **STATE** — Endpoint state: `Started` or `Stopped`

## HTTP Probe

Press `Enter` on an endpoint to open the interactive HTTP probe.

The probe has these sections:

- **Method**: `Left/Right` arrows to cycle through HTTP methods
- **URL**: Read-only full URL (with resolved placeholders). Clickable as hyperlink
- **Path**: Editable URL path template (e.g., `/api/users/{id}`)
- **Path Params**: Auto-detected from `{xxx}` placeholders in the path. Fill in values and they get substituted in the URL
- **Query Params**: Key-value pairs appended to the URL as `?key=value`. Press `+` to add
- **Content-Type**: Cycle through common content types with `Left/Right` arrows
- **Accept**: Cycle through common accept types with `Left/Right` arrows
- **Headers**: Custom request headers. Press `+` to add
- **Body**: Multi-line request body (Enter for newline). Supports `file:payload.json` to load from disk
- **Response**: HTTP status code, elapsed time, response headers, and body
- **History**: Recent requests with replay support

Press `F5` to send the request. Press `p` to toggle pretty-print for JSON responses.

## Keys

### Endpoint List
- `Up/Down` — select endpoint
- `Enter` — open HTTP probe for selected endpoint
- `s` — cycle sort column
- `S` — reverse sort order
- `f` — cycle filter (all / rest / http)
- `m` — toggle management endpoints
- `c` — view OpenAPI spec (when available)

### HTTP Probe
- `F5` — send request
- `Tab/Down` — next field
- `Up` — previous field
- `Enter` — newline in body, advance in other fields
- `Left/Right` — cycle method, content-type, accept; cursor in text fields
- `+` — add query param or header
- `p` — toggle pretty-print
- `PgUp/PgDn` — scroll response
- `Esc` — close probe
