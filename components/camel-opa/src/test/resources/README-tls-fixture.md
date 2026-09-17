# TLS test fixture

`opa-server.p12` is a throwaway self-signed keypair for `OpaRestTransportTest`, used both as the HTTPS
listener's key material and as the truststore the passing case is given. It is not used anywhere outside the
tests and grants nothing: the private key is public in this repository.

Regenerate with:

```sh
keytool -genkeypair -alias opa -keyalg RSA -keysize 2048 -validity 36500 \
    -dname "CN=localhost, OU=camel-opa tests, O=Apache Camel" \
    -ext "SAN=dns:localhost,ip:127.0.0.1" \
    -keystore opa-server.p12 -storetype PKCS12 -storepass changeit -keypass changeit
```

The certificate is valid for 100 years, so the tests will not start failing on an expiry nobody is watching.
