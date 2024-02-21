# Camel Braintree Component

# Running the integration tests

This component contains integration tests that must be executed against the Braintree sandbox. In 
order to do so, you need to setup an account as explained in the [testing guide](https://developers.braintreepayments.com/reference/general/testing/java).

Then, login to the Sandbox instance, collect the merchantId, publicKey and privateKey and edit the
test-options.properties file in the test resources directory. Also, uncomment the environment option. 

```
environment = SANDBOX
merchantId  = merchant ID taken from sandbox ui
publicKey   = key from sandbox ui 
privateKey  = private key from sandbox ui
```

Then, you can use the following commands to run the tests:

```
CAMEL_BRAINTREE_REPORT_DATE=$(date '+%Y-%m-%d') CAMEL_BRAINTREE_MERCHANT_ACCOUNT_ID="merchant ID taken from sandbox" mvn -Pbraintree-test -DbraintreeAuthenticationType=PUBLIC_PRIVATE_KEYS clean verify 
```

It's also possible to run a smaller set of tests by running them without the environment variables:
```
mvn -DbraintreeAuthenticationType=PUBLIC_PRIVATE_KEYS clean verify
```

# Authentication types

The authentication type can be passed via `braintreeAuthenticationType` system property. 
You can use one of: 

* PUBLIC_PRIVATE_KEYS
* ACCESS_TOKEN


I.e.:

```
mvn -DbraintreeAuthenticationType=ACCESS_TOKEN clean verify
```
