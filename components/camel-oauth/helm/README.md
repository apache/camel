# Local Kubernetes Cluster

To keep the entry barrier for Camel OAuth low, we initially deploy Keycloak as our Identity Provider on Rancher Desktop Kubernetes.
This is a single node Kubernetes cluster running on localhost.

## Ingress with Traefik

Keycloak should only be accessed with transport layer security (TLS) in place. This is in the nature
of exchanging privacy/security sensitive data over any channel.

Here we place Keycloak behind a TLS terminating proxy (Traefik). It has the advantage that any traffic
(i.e. not only for Keycloak) can be secured at ingress level. Traefik should already be installed with Rancher Desktop.

https://doc.traefik.io/traefik/


Create and install TLS edge certificate

```
brew install mkcert nss

# Make sure the mkcert root CA is trusted
mkcert --install

mkcert "localtest.me" "*.localtest.me"
mkdir -p helm/tls && mv localtest.* helm/tls

kubectl delete secret edge-tls --ignore-not-found=true
kubectl create secret tls edge-tls \
    --cert=helm/tls/localtest.me+1.pem \
    --key=helm/tls/localtest.me+1-key.pem

# The above shold also install the root cert with the Java system truststore. This is matter of mkcert finding the correct JRE home.
# In case it does not, you may need to import the mkcert rootCA.pem manually to the Java truststore.
keytool -delete -cacerts -alias mkcert-root -storepass changeit
keytool -importcert -cacerts -alias mkcert-root -storepass changeit -noprompt \
    -file "$(mkcert -CAROOT)/rootCA.pem"
```

... and verify that TLS access is working

```
helm upgrade --install whoami ./helm -f ./helm/values-whoami.yaml
```

https://localtest.me/who


## Installing Keycloak

Using Helm, we can also install a pre-configured instance of Keycloak behind Traefik like this ... 

```
helm upgrade --install keycloak ./helm -f ./helm/values-keycloak.yaml \
    && kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=keycloak --timeout=20s \
    && kubectl logs --tail 400 -f -l app.kubernetes.io/name=keycloak

helm uninstall keycloak
```

https://oauth.localtest.me

Admin:  admin/admin
User:   alice/alice

You should now be able to examine the 'camel' realm and its pre-configured clients.

Note, in case you see `NoSuchAlgorithmException: RSA-OAEP`, we can disable that [like this](https://github.com/tdiesler/camel-cloud-examples/issues/16). 

### Keycloak Configuration

Create realm 'camel' if not already imported

```
kcadm config credentials --server https://oauth.localtest.me/kc --realm master --user admin --password admin

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

## Installing Kafka

We can install a single node Kafka cluster like this

```
helm upgrade --install kafka ./helm -f ./helm/values-kafka.yaml \
    && kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=kafka --timeout=20s \
    && kubectl logs --tail 400 -f -l app.kubernetes.io/name=kafka

helm uninstall kafka
```

# OpenShift

First, we create a new project on the OpenShift cluster

```
oc new-project camel
```

## Installing Keycloak

```
export OPENSHIFT_HOSTNAME=apps.rosa.scvka-fwa2e-54s.9pbs.p3.openshiftapps.com
helm upgrade --install keycloak --namespace examples --set openshift.hostName=${OPENSHIFT_HOSTNAME} ./helm -f ./helm/values-keycloak-openshift.yaml \
    && kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=keycloak --timeout=20s \
    && kubectl logs --tail 400 -f -l app.kubernetes.io/name=keycloak

helm uninstall keycloak
```

Verify access to the OIDC configuration

```
curl -s https://keycloak.${OPENSHIFT_HOSTNAME}/realms/camel/.well-known/openid-configuration | jq .
```

### Modify Keycloak OIDC Config for OpenShift

```sh
kcadm config credentials --server https://keycloak.${OPENSHIFT_HOSTNAME} --realm master --user admin --password admin

# Show client config
kcadm get clients -r camel | jq '.[] | select(.clientId=="camel-client")'

# Update redirect URIs
CLIENT_ID=$(kcadm get clients -r camel --fields id,clientId | jq -r '.[] | select(.clientId=="camel-client").id') \
  && kcadm update clients/$CLIENT_ID -r camel -s 'redirectUris=["https://webapp.'${OPENSHIFT_HOSTNAME}'/auth"]' \
  && kcadm update clients/$CLIENT_ID -r camel -s 'attributes."post.logout.redirect.uris"="https://webapp.'${OPENSHIFT_HOSTNAME}'/"'
```