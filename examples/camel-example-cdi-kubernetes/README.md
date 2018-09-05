# Kubernetes Example - CDI

### Introduction

This example illustrates the integration between Camel, CDI and Kubernetes.

The example gets the list of pods from a Kubernetes cluster and emulates
the output of the `kubectl get pods` command.

The `camel-cdi` and `camel-kubernetes` components are used in this example.
The example assumes you have a running Kubernetes cluster in your environment. 
For example you can use [minikube](https://github.com/kubernetes/minikube)
or the [vagrant openshift image](https://github.com/fabric8io/fabric8-installer/tree/master/vagrant/openshift) from
[Fabric8 team](http://fabric8.io/).

By default, the example use the `KUBERNETES_MASTER` environment variable
to retrieve the Kubernetes master URL. Besides, it tries to find the current
login token and namespace by parsing the users `~/.kube/config` file.
However, you can edit the `application.properties` file to override the default
and provide the Kubernetes master URL and OAuth token for your environment.

To retrieve the tokens in your kubeconfig file, you can execute the following command:
```sh
$ kubectl config view -o jsonpath='{range .users[?(@.user.token != "")]}{.name}{":\t"}{.user.token}{"\n"}{end}'
```

Alternatively, if you're using OpenShift, you can retrieve the token for the current user with:
```sh
$ oc whoami -t
```

### Build

You can build this example using:

```sh
$ mvn package
```

### Run

You can run this example using:

```sh
$ KUBERNETES_MASTER="https://$(minikube ip):8443/" mvn camel:run
```

When the Camel application runs, you should see the pods list being logged
periodically, e.g.:
```
2016-08-22 18:29:46,449 [ timer://client] INFO kubernetes-client - We currently have 11 pods:
NAME                                READY     STATUS    RESTARTS   AGE
content-repository-yhg9u            1/1       Running   34         49d
docker-registry-1-bv5vk             1/1       Running   34         49d
fabric8-docker-registry-9jd9z       1/1       Running   34         49d
fabric8-forge-8gnro                 1/1       Running   11         17d
fabric8-kw532                       1/1       Running   34         49d
gogs-2exwf                          1/1       Running   22         32d
jenkins-kixdv                       1/1       Running   12         18d
mysql-5-f8trz                       1/1       Running   8          13d
nexus-5j60s                         1/1       Running   71         49d
router-1-7eiu5                      2/2       Running   26         49d
spring-boot-camel-rest-s-13-559vm   1/1       Running   0          6h

```

The timer has a `period` option equals to `10s`.

The Camel application can be stopped pressing <kbd>ctrl</kbd>+<kbd>c</kbd> in the shell.

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may have. Enjoy!

The Camel riders!
