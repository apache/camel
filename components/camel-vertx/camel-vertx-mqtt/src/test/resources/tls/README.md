# Test certificates

Self-signed, valid for 100 years, password `changeit`, for the TLS tests only.

| File | Content |
|---|---|
| `broker.p12` | key and certificate of the broker, SAN `IP:127.0.0.1` |
| `broker-other-name.p12` | key and certificate of a broker named `other.example.com`, trusted but for another host |
| `client.p12` | key and certificate of a client, for mutual TLS |
| `client-trust.p12` | what the client trusts: both broker certificates |
| `broker-trust.p12` | what the broker trusts: the client certificate |

Made with:

```
keytool -genkeypair -alias broker -keyalg EC -groupname secp256r1 -dname "CN=127.0.0.1" -ext SAN=ip:127.0.0.1 -validity 36500 -keystore broker.p12 -storetype PKCS12 -storepass changeit
keytool -genkeypair -alias broker -keyalg EC -groupname secp256r1 -dname "CN=other.example.com" -ext SAN=dns:other.example.com -validity 36500 -keystore broker-other-name.p12 -storetype PKCS12 -storepass changeit
keytool -genkeypair -alias client -keyalg EC -groupname secp256r1 -dname "CN=test-client" -validity 36500 -keystore client.p12 -storetype PKCS12 -storepass changeit
```

then each certificate exported with `keytool -exportcert` and imported with `keytool -importcert` into the trust stores.
