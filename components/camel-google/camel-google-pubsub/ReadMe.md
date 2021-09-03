## Camel Google PubSub Component testing

The unit tests provided are somewhat limited. 
Due to the nature of the component, it needs to be tested against a server. To assist with this task Google has provided 
a PubSub Emulator. The test for the component, therefore, have been split into two groups :

* Unit : <br> 
  Standalone tests that can be conducted on their own
* Integration : <br>
  Tests against the emulator

### Emulator local installation

Emulator is being distributed with the [Google SDK](https://cloud.google.com/sdk/).
Once the SDK has been installed and configured, add PubSub Emulator:

```
gcloud components install pubsub-emulator
```

Please note the folder where it is installed and configure _GCLOUD_SDK_PATH_ environmental variable.
It is a custom variable, used by mvn plugin to find the installation.
 

### Execution

Maven is configured to start the emulator prior the integration tests and shut down afterwards.
The emulator is configured to listen to port 8383. 

Integration tests and the emulator will eb available as part of _google-pubsub-test_ profile:

```
mvn -Pgoogle-pubsub-test verify
```


