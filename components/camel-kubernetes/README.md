# Camel Kubernetes

# Running the tests

This component contains unit and integration tests. Some of them - like the consumer ones - require a Kubernetes environment. 

## Running using Kind

It is possible to run the integration tests using Kind. To do so, follow these steps:

1. Create a cluster:

```
kind create cluster
```

2. Set the default namespace to work with (optional):

```
kubectl config set-context --current --namespace=default
```

3. Create a ServiceAccount and Secret:

```
kubectl create serviceaccount camel
```

```
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Secret
metadata:
  name: camel-token
  annotations:
    kubernetes.io/service-account.name: camel
type: kubernetes.io/service-account-token
EOF
```

4. Get the auth token:

```
export KUBE_TOKEN=$(kubectl get secrets -o jsonpath="{.items[?(@.metadata.annotations['kubernetes\.io/service-account\.name']=='camel')].data.token}"|base64 --decode)
```

5. Get the host:

```
export KIND_PORT=$(docker inspect --format='{{range $p, $conf := .NetworkSettings.Ports}}{{(index $conf 0).HostPort}}{{end}}'  kind-control-plane)
export KUBE_HOST=https://localhost:$KIND_PORT
```

6. Run the test:
```
mvn -Dkubernetes.test.auth="$KUBE_TOKEN" -Dkubernetes.test.host=$KUBE_HOST -Dkubernetes.test.host.k8s=true clean verify
```


## Running using Minikube

It is possible to run the integration tests using Minikube. To do so, follow these steps:

1. Create a cluster:

```
minikube start
```

2. Get the auth token:

```
export KUBE_TOKEN=$(kubectl get secrets -o jsonpath="{.items[?(@.metadata.annotations['kubernetes\.io/service-account\.name']=='default')].data.token}"|base64 --decode)
```

4. Get the host:

Find out the URL where the control plane is running:

````
kubectl cluster-info
````

And then set that as export, for example:

```
export KUBE_HOST=https://127.0.0.1:50179
```

5. Run the test:
```
mvn -Dkubernetes.test.auth="$KUBE_TOKEN" -Dkubernetes.test.host=$KUBE_HOST -Dkubernetes.test.host.k8s=true clean verify
```

