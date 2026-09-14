# WASM test fixtures

`authz.wasm` and `authz-bundle.tar.gz` are **generated artifacts**, compiled from `authz.rego` in this
directory. They are committed only so the `evaluationMode=wasm` tests can run before CAMEL-24742 adds a
build step that compiles them, at which point both files should be deleted.

Regenerate after any change to `authz.rego` — nothing currently checks that they agree, which is exactly
why CAMEL-24742 exists:

```sh
docker run --rm -v "$PWD":/w:Z -w /w mirror.gcr.io/openpolicyagent/opa:1.9.0-static \
    build -t wasm -e authz/allow -e authz/decision authz.rego
tar xzf bundle.tar.gz ./policy.wasm && mv policy.wasm authz.wasm && mv bundle.tar.gz authz-bundle.tar.gz
```

`-e` names the entrypoints. An entrypoint is fixed at build time and is not the same thing as a data
path, which is why `camel-opa` lets `entrypoint` be set separately from `policyPath`.
