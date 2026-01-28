Most of the tests are not played on CI due to resource constraints.

There are 3 kind of tests that can be enabled outside of the CI.

* Integration tests: They rely on TestContainers. They require that `ci.env.name` is not set to be executed.
* Unit tests with API mode: They require docling-serve to be running locally, for instance with `podman run -p 5001:5001 ghcr.io/docling-project/docling-serve:latest`. To activate them, you need to set the property `docling.serve.test.enabled` to `true`
* Unit tests with CLI mode: They require `docling` to be installed, for instance via `pip install docling`. To active them, you need to set the property `docling.test.enabled` to `true`.
