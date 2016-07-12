# Metrics Example - CDI

### Introduction

This example illustrates the integration between Camel, CDI and Kubernetes.

The example get the list of pods from a Kubernetes cluster and print name and status of each one of the pods returned.

The `camel-cdi`, `camel-core` and `camel-kubernetes` components are used in this example.
The example assumes you have a running Kubernetes cluster in your environment. 
For example you can use [minikube](https://github.com/kubernetes/minikube) or the [vagrant openshift image](https://github.com/fabric8io/fabric8-installer/tree/master/vagrant/openshift) from [Fabric8 Team](http://fabric8.io/).
Remember to edit the apache-deltaspike.properties file to use the correct Kubernetes Master URL and OAuth token for your environment.

### Build

You will need to build this example first:

```sh
$ mvn install
```

### Run

You can run this example using:

```sh
$ mvn compile camel:run
```

When the Camel application runs, you should see the Pods name and status. For example:
```
We currently have 13 pods
Pod name docker-registry-1-c6ie5 with status Running
Pod name fabric8-docker-registry-pgo5y with status Running
Pod name fabric8-forge-wvyw7 with status Running
Pod name fabric8-tr0b9 with status Running
Pod name gogs-2p4mn with status Running
Pod name grafana-754y7 with status Running
Pod name infinispan-client-a7z3k with status Running
Pod name infinispan-client-iubag with status Running
Pod name infinispan-server-wl0in with status Running
Pod name jenkins-cr2ez with status Running
Pod name nexus-aarks with status Running
Pod name prometheus-mp0kr with status Running
Pod name router-1-dkjsb with status Running
```

The timer has a repeatCount option equals to 3. So you should see this output 3 times.

The Camel application can be stopped pressing <kbd>ctrl</kbd>+<kbd>c</kbd> in the shell.

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may have. Enjoy!

The Camel riders!
