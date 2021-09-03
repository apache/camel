# Camel Kubernetes

# Running the tests

This component contains unit and integration tests. Some of them - like the consumer ones - require a Kubernetes environment. 

It is possible to run the integration tests using Kind. To do so, follow these steps:

1. Create a cluster:

```
kind create cluster
```

2. Get the auth token:

```
export KUBE_TOKEN=$(kubectl get secrets -o jsonpath="{.items[?(@.metadata.annotations['kubernetes\.io/service-account\.name']=='default')].data.token}"|base64 --decode)
```

4. Get the host:

```
export KIND_PORT=$(docker inspect --format='{{range $p, $conf := .NetworkSettings.Ports}}{{(index $conf 0).HostPort}}{{end}}'  kind-control-plane)
export KUBE_HOST=https://localhost:$KIND_PORT
```

5. Run the test:
```
mvn -Dkubernetes.test.auth="$KUBE_TOKEN" -Dkubernetes.test.host=$KUBE_HOST -Dkubernetes.test.host.k8s=true clean verify
```

