# Data Type Transformers

The Data Type Transformers tab shows all transformers registered in the
Camel transformer registry. Data type transformers are used to convert
message bodies between declared data types as part of Camel's contract
mechanism (inputType/outputType on routes).

When a route declares an inputType or outputType, Camel automatically
looks up a matching transformer and applies it to convert the message
body between the source and target data types.

## Table Columns

- **NAME** — Transformer name (e.g. `json:jackson`, `xml:jaxb`)
- **FROM** — Source data type (`*` means any input type)
- **TO** — Target data type (`*` means any output type)

## Keys

- `Up/Down` — select transformer
- `s` — cycle sort column
- `S` — reverse sort order
- `Esc` — back
