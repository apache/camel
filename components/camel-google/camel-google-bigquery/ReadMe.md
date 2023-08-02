## Camel Google BigQuery Component testing

The unit tests provided are somewhat limited.

Due to the nature of the component, it needs to be tested against a google BigQuery instance as no
emulator is available.

* Unit : <br>
  Standalone tests that can be conducted on their own
* Integration : <br>
  Tests against a Google BigQuery instance

### Execution of integration tests

A Google Cloud account with a configured BigQuery instance is required with a dataset created.

Google BigQuery component authentication is targeted for use with the GCP Service Accounts.
For more information please refer to [Google Cloud Platform Auth Guide](https://cloud.google.com/docs/authentication)

Google security credentials for the tests can be set in the `src/test/resources/simple.properties` file by setting
either one of the following in order of preference:

* Service Account Email and Service Account Key (PEM format) (`service.account` and `service.key`)
* GCP credentials file location (`service.credentialsFileLocation`)

Or implicitly, where the connection factory falls back on [Application Default Credentials](https://developers.google.com/identity/protocols/application-default-credentials#howtheywork).

*OBS!* The location of the default credentials file is configurable - via GOOGLE_APPLICATION_CREDENTIALS environment variable.

Service Account Email and Service Account Key can be found in the GCP JSON credentials file as client_email and private_key respectively.

For the tests the `project.id` and `bigquery.datasetId` needs to be configured. By default,
the current google user will be used to connect but credentials can be provided either by
account/key (via `service.account` and `service.key`) or a credentials file (`service.credentialsFileLocation`)

Running tests against BigQuery instance:

```
mvn verify
```
