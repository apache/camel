# SQL Query

Execute SQL queries against DataSource beans registered in the Camel application.

## Usage
- Type a SQL query in the input field and press **F5** to execute
- Type **file:query.sql** to load SQL from a file
- Use **Enter** for new lines in the query
- Use **Up/Down** arrows to move cursor within the query
- Paste multi-line queries from clipboard
- Use **Ctrl+E** to open query history (select with Enter, dismiss with Esc)
- Use **Tab** to toggle focus between input and results table
- Use **Esc** to return focus to the input field from results
- Use **Ctrl+Left/Right** to switch between DataSources (when multiple exist)

## Inline Editing
- For simple single-table SELECT queries, press **F4** on a result row to edit
- Primary key columns (marked with *) are read-only
- Changed values are highlighted in green
- Press **F5** to save changes (executes an UPDATE statement)
- Press **Esc** to cancel editing
- The query is automatically re-executed after a successful update

## Supported Queries
- SELECT queries return a result table
- INSERT, UPDATE, DELETE return an update count
- Any valid SQL supported by the underlying database

## Safety
- Results are limited to 100 rows by default
- Query timeout is 30 seconds by default
- This feature is only available when dev console is enabled (dev profile)
