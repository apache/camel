
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

## Ingress with Traefik

An Identity Provider should only be accessed with transport layer security (TLS) in place. This is in the nature
of communicating privacy/security sensitive data over any communication channel.

Therefore, we place Keycloak behind an TLS terminating proxy (Traefik). It has the advantage that any traffic 
(i.e. not only for Keycloak) can be secured at ingress level.

https://doc.traefik.io/traefik/

```
helm repo add traefik https://traefik.github.io/charts
helm repo update
helm install traefik traefik/traefik
```

## Ingress TLS Certificate

```
# Generate TLS Certificate
openssl req -x509 -newkey rsa:4096 -keyout ./helm/etc/cluster.key -out ./helm/etc/cluster.crt -days 365 -nodes -config ./helm/etc/san.cnf

# Import TLS Certificate to Java Keystore (i.e. trust the certificate)
sudo keytool -import -alias keycloak -file ./helm/etc/cluster.crt -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit

# Remove TLS Certificate from Java Keystore
sudo keytool -delete -alias keycloak -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit
```

### Verify with TLS access

```
helm upgrade --install traefik-secret ./helm -f ./helm/values-traefik-secret.yaml
helm upgrade --install whoami ./helm -f ./helm/values-whoami.yaml
```

https://cluster.local/who

## Keycloak as the Identity Provider

Currently, we use Keycloak as OIDC Provider - Hashicorp Vault is underway.

Keycloak can be configured/deployed via Helm like this...

```
kubectl config use-context docker-desktop \
    && helm upgrade --install keycloak ./helm -f ./helm/values-keycloak.yaml \
    && kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=keycloak --timeout=20s \
    && kubectl logs --tail 400 -f -l app.kubernetes.io/name=keycloak

helm uninstall keycloak
```

https://keycloak.local/kc

Admin:  admin/admin
User:   alice/alice

### Keycloak Admin Tasks

Create realm 'camel' if not already imported

```
kcadm config credentials --server https://keycloak.local/kc --realm master --user admin --password admin

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

Show realm, client, user configuration

```
kcadm get realms | jq -r '.[] | select(.realm=="camel")'

kcadm get clients -r camel | jq -r '.[] | select(.clientId=="camel-client")'

kcadm get users -r camel | jq -r '.[] | select(.username=="alice")'
```

## Kafka on Kubernetes

### Extract the Keycloak cert

In this configuration, Keycloak is deployed behind Traefik, which is our TLS terminating proxy.
The domain `keycloak.local` is mapped to an actual IP in `/etc/hosts`.

```
echo -n | openssl s_client -connect keycloak.local:443 -servername keycloak.local | openssl x509 > keycloak.crt
cat keycloak.crt | openssl x509 -noout -text
```

Deploy a single node Kafka cluster

```
kubectl config use-context docker-desktop \
    && helm upgrade --install kafka ./helm -f ./helm/values-kafka.yaml \
    && kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=kafka --timeout=20s \
    && kubectl logs --tail 400 -f -l app.kubernetes.io/name=kafka

helm uninstall kafka
```
