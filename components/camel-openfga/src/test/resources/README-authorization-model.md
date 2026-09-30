# The test authorization model

`authorization-model.json` is the authorization model `OpenFgaIT` writes into a fresh store before it runs. It is the
JSON form of this OpenFGA DSL:

```
model
  schema 1.1

type user

type document
  relations
    define owner: [user]
    define reader: [user, user:*] or owner
    define writer: [user] or owner
```

Two things in it are load-bearing for the tests:

- `reader` is a union of a direct assignment and `owner`, so `owner` implies `reader`. That is what makes
  `check(user:anne, reader, document:budget)` answer true from an `owner` tuple alone - an inherited permission rather
  than a tuple that was written directly.
- `reader` also admits `user:*`, the typed wildcard. Without it the store could not hold a public-access tuple, and the
  test that shows why a wildcard is refused as a *checking* subject would have nothing to demonstrate.

It is kept as JSON rather than as the DSL because that is what the API takes; the SDK has no DSL parser.
