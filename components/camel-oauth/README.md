
# Camel OAuth (Preview)

The camel-oauth module comes with a set of Processors that can be added to a route on the client and resource owner side
like [this|https://github.com/tdiesler/camel-cloud-examples/blob/main/camel-main/platform-http-oauth/platform-http-files/platform-http-route.yaml] ...

## Supported OIDC, OAuth functionality

* OIDC Authentication using Authorization Code Flow (OAuthCodeFlowProcessor, OAuthCodeFlowCallbackProcessor)
* OAuth Client Credentials Grant (OAuthClientCredentialsProcessor, OAuthBearerTokenProcessor)
* Identity Provider (Keycloak only) logout (OAuthLogoutProcessor)

For details, see these specs ...

* [OAuth 2.0|https://datatracker.ietf.org/doc/html/rfc6749]
* [OIDC 1.0|https://openid.net/specs/openid-connect-core-1_0.html]

Our Identity Provider (Keycloak) can be deployed to a local k8s cluster with a [helm chart|./helm] that comes with this project.

Respective jbang projects live [here|https://github.com/tdiesler/camel-cloud-examples/tree/main/camel-main]

For Kafka we use strimzi kafka-oauth-client directly, which is documented [here|https://github.com/tdiesler/camel-cloud-examples/blob/main/camel-main/kafka-oauth/kafka-oauth-route.yaml].

## Keycloak as the Identity Provider

Currently, we use Keycloak as OIDC Provider - Hashicorp Vault is underway. 

### Keycloak Certificate

```
# Generate TLS Certificate
openssl req -x509 -newkey rsa:4096 -keyout ./helm/etc/keycloak.key -out ./helm/etc/keycloak.crt -days 365 -nodes -config ./helm/etc/san.cnf

# Import TLS Certificate to Java Keystore (i.e. trust the certificate)
sudo keytool -import -alias keycloak -file ./helm/etc/keycloak.crt -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit

# Remove TLS Certificate from Java Keystore
sudo keytool -delete -alias keycloak -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit
```

### Keycloak on Kubernetes

Deploy Keycloak as Identity Provider

Admin:  admin/admin
User:   alice/alice

https://keycloak.local:30443/

Host 'keycloak.local' should be mapped (/etc/hosts) to your local k8s IP

```
kubectl config use-context docker-desktop \
    && helm upgrade --install keycloak ./helm -f ./helm/values-keycloak.yaml \
    && kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=keycloak --timeout=20s \
    && kubectl logs --tail 400 -f -l app.kubernetes.io/name=keycloak

helm uninstall keycloak
```

### Keycloak Admin Tasks

Run this when realm 'camel' if not already imported

```
kcadm config credentials --server https://keycloak.local:30443 --realm master --user admin --password admin

kcadm create realms -s realm=camel -s enabled=true

kcadm create clients -r camel \
    -s clientId=camel-client \
    -s publicClient=false \
    -s standardFlowEnabled=true \
    -s serviceAccountsEnabled=true \
    -s "redirectUris=[\"http://127.0.0.1:8080/auth\"]" \
    -s "attributes.\"post.logout.redirect.uris\"=\"http://127.0.0.1:8080/\""
    
clientId=$(kcadm get clients -r camel -q clientId=camel-client --fields id --format csv --noquotes)
kcadm update clients/${clientId} -r camel -s secret="camel-client-secret"

kcadm create users -r camel \
    -s username=alice \
    -s email=alice@example.com \
    -s emailVerified=true \
    -s firstName=Alice \
    -s lastName=Brown \
    -s enabled=true
    
userid=$(kcadm get users -r camel -q username=alice --fields id --format csv --noquotes)
kcadm set-password -r camel --userid=${userid} --new-password alice    

kcadm delete realms/camel -r master
```

Show client/user configuration

```
kcadm get clients -r camel | jq -r '.[] | select(.clientId=="camel-client")'

kcadm get users -r camel | jq -r '.[] | select(.username=="alice")'
```
