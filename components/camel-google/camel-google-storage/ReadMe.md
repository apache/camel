# Camel Google Storage Component
This component is based on the [google java storage library](https://github.com/googleapis/java-storage) that works as a client for the Google Cloud Storage.


## Camel Google Storage Component testing

The unit tests provided are somewhat limited.
Due to the nature of the component, it needs to be tested against a google Storage instance because although there are some emulators
they doesn't cover all the functionalities.

The tests are organized into two packages:
* **Unit** : Standalone tests that can be conducted on their own
* **Integration** : Tests against a Google Storage instance

For the Unit tests has been extended the emulator [google-cloud-nio](https://github.com/googleapis/java-storage-nio/tree/master/google-cloud-nio/src/main/java/com/google/cloud/storage/contrib/nio/testing)
adding some functionalities (bucket creation and deletion). However there are still some unsupported operations.


### Execution of integration tests

To run the Google Storage client library, you must first set up authentication by creating a service account key.
You can find more info at: [cloud.google.com/storage/docs/reference/libraries#setting_up_authentication](https://cloud.google.com/storage/docs/reference/libraries#setting_up_authentication).

When you have the **service account key** you can provide authentication credentials to your application code by setting the environment variable:

`export GOOGLE_APPLICATION_CREDENTIALS="/home/user/Downloads/my-key.json"`

or for windows:

`$Env:GOOGLE_APPLICATION_CREDENTIALS = "/home/user/Downloads/my-key.json"`

or directly through the component endpoint

`from("google-storage://myCamelBucket?serviceAccountKey=/home/user/Downloads/my-key.json")`

Running integration tests will be automatically executed if the GOOGLE_APPLICATION_CREDENTIALS is exported, and the `verify` target is executed:

```
mvn verify
```


