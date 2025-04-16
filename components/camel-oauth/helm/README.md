# Local Kubernetes Cluster

To keep the entry barrier for Camel OAuth low, we initially deploy Keycloak as our Identity Provider on Docker Desktop Kubernetes.
This is a single node Kubernetes cluster running on localhost.

## Ingress with Traefik

Keycloak should only be accessed with transport layer security (TLS) in place. This is in the nature
of exchanging privacy/security sensitive data over any channel.

Here we place Keycloak behind a TLS terminating proxy (Traefik). It has the advantage that any traffic
(i.e. not only for Keycloak) can be secured at ingress level.

https://doc.traefik.io/traefik/

```
helm repo add traefik https://traefik.github.io/charts
helm repo update
helm install traefik traefik/traefik
```

Once Traefik is installed, we create a Kubernetes TLS 'secret'.  

In case you'd like to regenerate the TLS certificate and key, do this ...
Also, a Java app that wants to access Keycloak over TLS, must trust that certificate. 

```
# Generate TLS Certificate
openssl req -x509 -newkey rsa:4096 -keyout ./helm/etc/cluster.key -out ./helm/etc/cluster.crt -days 365 -nodes -config ./helm/etc/san.cnf

# Show Certificate
cat ./helm/etc/cluster.crt | openssl x509 -noout -text

# Import TLS Certificate to Java Keystore (i.e. trust the certificate)
sudo keytool -import -alias camel-oauth -file ./helm/etc/cluster.crt -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit

# Remove TLS Certificate from Java Keystore
sudo keytool -delete -alias camel-oauth -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit

# Trust this cert on macOS
sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain ./helm/etc/cluster.crt

# Trust this cert on Rock9
sudo cp ./helm/etc/cluster.crt /etc/pki/ca-trust/source/anchors/ && sudo update-ca-trust
```

Once we have the TLS certificate, we can install the TLS secret like this ...

```
helm upgrade --install traefik-tls ./helm -f ./helm/values-traefik-tls.yaml
```

... and verify that TLS access is working

```
helm upgrade --install whoami ./helm -f ./helm/values-whoami.yaml
```

https://example.local/who

Note, the domains `example.local` and `keycloak.local` are mapped to an actual IP in `/etc/hosts`.

## Installing Keycloak

Using Helm, we can install a pre-configured instance of Keycloak behind Traefik like this ... 

```
helm upgrade --install keycloak ./helm -f ./helm/values-keycloak.yaml \
    && kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=keycloak --timeout=20s \
    && kubectl logs --tail 400 -f -l app.kubernetes.io/name=keycloak

helm uninstall keycloak
```

https://keycloak.local/kc

Admin:  admin/admin
User:   alice/alice

You should now be able to examine the 'camel' realm and its pre-configured clients.

Note, in case you see `NoSuchAlgorithmException: RSA-OAEP`, we can disable that [like this](https://github.com/tdiesler/camel-cloud-examples/issues/16). 

### Keycloak Configuration

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

## Installing Kafka

We can install a single node Kafka cluster like this

```
helm upgrade --install kafka ./helm -f ./helm/values-kafka.yaml \
    && kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=kafka --timeout=20s \
    && kubectl logs --tail 400 -f -l app.kubernetes.io/name=kafka

helm uninstall kafka
```

# Remote Kubernetes Cluster

Next level up, we run a single node cluster that we access remotely - [K3S](https://k3s.io/) is an excellent choice for that.

Once K3s is running, we can use [Lens](https://k8slens.dev/), [kubectx](https://github.com/ahmetb/kubectx) or plain `kubectl config` for context switching to k3s.

As above, we need to install the TLS secret

```
helm upgrade --install traefik-tls ./helm -f ./helm/values-traefik-tls.yaml
```

... and then Keycloak

```
helm upgrade --install keycloak ./helm -f ./helm/values-keycloak.yaml \
    && kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=keycloak --timeout=20s \
    && kubectl logs --tail 400 -f -l app.kubernetes.io/name=keycloak

helm uninstall keycloak
```

https://keycloak.k3s/kc

## Modifying CoreDNS

Unlike DockerDesktop Kubernetes, pods deployed on K3S do not see /etc/hosts from the host system. Instead, K3S uses 
CoreDNS to resolve host names, which we can use to add the required mapping.

```
kubectl -n kube-system edit configmap coredns

  Corefile: |                                           
    .:53 {
        ...                                              
        hosts /etc/coredns/NodeHosts {
          <host-ip> keycloak.k3s
          ttl 60                  
          reload 15s              
          fallthrough                        
        }                                    
```

Please let us know, when there is a better way to provide a host mapping such that traffic goes through the Keycloak 
IngressRoute, which references our custom TLS certificate.

## Private Registry

Most of our examples reference images that are deployed to the private registry of the given cluster (i.e. these images
are not available in public registries). [camel-cloud-examples](https://github.com/tdiesler/camel-cloud-examples/tree/main)
provides [Ansible playbooks](https://github.com/tdiesler/camel-cloud-examples/tree/main/ansible) that show how ton install 
a private registry in K3S. There is also some documentation in K3S [directly](https://docs.k3s.io/installation/private-registry).

# OpenShift

First, we create a new project on the OpenShift cluster

```
oc new-project camel
```

## Installing Keycloak

```
export OPENSHIFT_HOSTNAME=apps.rosa.nxus7-dbdhd-pp7.vxfp.p3.openshiftapps.com
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

CLIENT_ID=$(kcadm get clients -r camel --fields id,clientId | jq -r '.[] | select(.clientId=="camel-client").id')

# Update redirect URIs
kcadm update clients/$CLIENT_ID -r camel -s 'redirectUris=["https://webapp.'${OPENSHIFT_HOSTNAME}'/auth"]'

# Update post-logout redirect URIs
kcadm update clients/$CLIENT_ID -r camel -s 'attributes."post.logout.redirect.uris"="https://webapp.'${OPENSHIFT_HOSTNAME}'/"'

kcadm get realms | jq '.[] | select(.realm=="camel")'

kcadm get keys -r camel
```