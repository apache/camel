# Camel Google Functions Component
This component is based on the [google java functions library](https://github.com/googleapis/java-functions) that works as a client for the Google Cloud Functions.


## Camel Google Storage Functions testing

The unit tests provided are somewhat limited.
Due to the nature of the component, it needs to be tested against a google Functions instance because although there are some emulators
they doesn't cover all the functionalities.

The tests are organized into two packages:
* **Unit** : Standalone tests that can be conducted on their own
* **Integration** : Tests against a Google Functions instance

### Execution of integration tests

To run the Google Functions client library, you must first set up authentication by creating a service account key.
You can find more info at: [Google Cloud Authentication](https://github.com/googleapis/google-cloud-java#authentication).

When you have the **service account key** you can provide authentication credentials to your application code by setting the environment variable:

`export GOOGLE_APPLICATION_CREDENTIALS="/home/user/Downloads/my-key.json"`

or for windows:

`$Env:GOOGLE_APPLICATION_CREDENTIALS = "/home/user/Downloads/my-key.json"`

or directly through the component endpoint

`from("google-functions://myCamelFunction?serviceAccountKey=/home/user/Downloads/my-key.json")`


Running tests against Google Functions instance:

```
mvn verify
```


