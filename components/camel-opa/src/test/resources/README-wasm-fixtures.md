# WASM test fixtures

`authz.wasm`, `authz-bundle.tar.gz` and `roles-bundle.tar.gz` are **generated artifacts**, compiled from the
Rego sources next to them. They are committed only so the `evaluationMode=wasm` tests can run before
CAMEL-24742 adds a build step that compiles them, at which point all three should be deleted.

Regenerate after any change to the sources — nothing currently checks that they agree, which is exactly why
CAMEL-24742 exists. Use the pinned version below: a different OPA release can emit a module built against a
different WebAssembly ABI than `opa-java-wasm` supports.

```sh
OPA='docker run --rm -v "$PWD":/w:Z -w /w mirror.gcr.io/openpolicyagent/opa:1.9.0-static'

# authz.wasm + authz-bundle.tar.gz, from authz.rego
eval $OPA build -t wasm -e authz/allow -e authz/decision -e authz/strict_allow authz.rego
tar xzf bundle.tar.gz --wildcards '*policy.wasm' && mv policy.wasm authz.wasm
mv bundle.tar.gz authz-bundle.tar.gz

# roles-bundle.tar.gz, from wasm-data/ (roles.rego plus the data.json that opa build packs beside it)
(cd wasm-data && eval $OPA build -t wasm -e roles/allow .)
mv wasm-data/bundle.tar.gz roles-bundle.tar.gz
```

`-e` names the entrypoints. An entrypoint is fixed at build time and is not the same thing as a data path,
which is why `camel-opa` lets `entrypoint` be set separately from `policyPath`.

`authz.rego` is shared with `OpaIT`, which uploads it to a real OPA server: the two engines must decide the
same way, so a rule added here should be exercised from both test classes.
