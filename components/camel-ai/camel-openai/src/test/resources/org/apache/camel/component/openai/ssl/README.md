# Test Resources

## SSL Test Keystores

### JKS keystores (test-keystore.jks / test-truststore.jks)

Used by `OpenAISslConfigurationTest`, `OpenAISslMockTest`, and `OpenAIMtlsMockTest`.
Store password and key password are both `changeit`.

```bash
keytool -genkeypair -alias test -keyalg RSA -keysize 2048 -validity 3650 \
  -keystore src/test/resources/org/apache/camel/component/openai/ssl/test-keystore.jks \
  -storepass changeit -keypass changeit \
  -dname "CN=localhost, OU=Test, O=Apache, L=Test, ST=Test, C=US" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"

keytool -exportcert -alias test \
  -keystore src/test/resources/org/apache/camel/component/openai/ssl/test-keystore.jks -storepass changeit \
  -file /tmp/test-cert.cer

keytool -importcert -alias test -file /tmp/test-cert.cer \
  -keystore src/test/resources/org/apache/camel/component/openai/ssl/test-truststore.jks -storepass changeit -noprompt
```

### JKS keystores with different key password (test-keystore-diffpass.jks / test-truststore-diffpass.jks)

Used by `OpenAIMtlsMockTest` to test the `sslKeyPassword` fallback behavior.
Store password is `changeit`, key password is `keypass123`.

```bash
keytool -genkeypair -alias test -keyalg RSA -keysize 2048 -validity 3650 \
  -storetype JKS \
  -keystore src/test/resources/org/apache/camel/component/openai/ssl/test-keystore-diffpass.jks \
  -storepass changeit -keypass keypass123 \
  -dname "CN=localhost, OU=Test, O=Apache, L=Test, ST=Test, C=US" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"

keytool -exportcert -alias test \
  -keystore src/test/resources/org/apache/camel/component/openai/ssl/test-keystore-diffpass.jks -storepass changeit \
  -file /tmp/test-cert-diffpass.cer

keytool -importcert -alias test -file /tmp/test-cert-diffpass.cer \
  -storetype JKS \
  -keystore src/test/resources/org/apache/camel/component/openai/ssl/test-truststore-diffpass.jks -storepass changeit -noprompt
```

### PKCS12 keystores (test-keystore.p12 / test-truststore.p12)

Used by `OpenAISslMockTest` to test `sslTruststoreType=PKCS12`.
Store password is `changeit`.

```bash
keytool -genkeypair -alias test -keyalg RSA -keysize 2048 -validity 3650 \
  -storetype PKCS12 \
  -keystore src/test/resources/org/apache/camel/component/openai/ssl/test-keystore.p12 \
  -storepass changeit \
  -dname "CN=localhost, OU=Test, O=Apache, L=Test, ST=Test, C=US" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"

keytool -exportcert -alias test \
  -keystore src/test/resources/org/apache/camel/component/openai/ssl/test-keystore.p12 -storepass changeit \
  -file /tmp/test-cert-p12.cer

keytool -importcert -alias test -file /tmp/test-cert-p12.cer \
  -storetype PKCS12 \
  -keystore src/test/resources/org/apache/camel/component/openai/ssl/test-truststore.p12 -storepass changeit -noprompt
```
