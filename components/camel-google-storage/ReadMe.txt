## Camel Google Storage Component
This component is based on the https://github.com/googleapis/java-storage[google java storage library] that works as a client for the Google Cloud Storage.


## Camel Google Storage Component testing

The unit tests provided are somewhat limited.
Due to the nature of the component, it needs to be tested against a google Storage instance because although there are some emulators
they doesn't cover all the functionalities.

The tests are organized into two packages:
* Unit : <br>
  Standalone tests that can be conducted on their own
* Integration : <br>
  Tests against a Google Storage instance

For the Unit tests has been extended the emulator https://github.com/googleapis/java-storage-nio/blob/master/google-cloud-nio/src/main/java/com/google/cloud/storage/contrib/nio/testing/FakeStorageRpc.java[FakeStorageRpc]
adding some functionalities (bucket creation and deletion). However there are still some unsupported operations.


### Execution of integration tests

A Google Cloud account with a project...

Running tests against Storage instance:

```
mvn -Pgoogle-storage-test verify
```


