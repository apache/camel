# Camel Hazelcast route on Kubernetes cluster

This quickstart run in a Java standalone container, using Spring with Apache Camel (Hazelcast component).

This quickstart is based on the Kubernetes example here: https://github.com/kubernetes/kubernetes/tree/master/examples/storage/hazelcast

This example is based on:

- Minikube (Kubernetes version >= 1.5) or Minishift (Openshift >= 3.5)
- Fabric8 Maven Plugin (version >= 3.2)

First thing you'll need to do is preparing the environment.

Once your Minikube node is up and running you'll need to run the following command.
In your src/main/resources/fabric8/ folder you'll find two yaml file. Run the following command using them:

```
$ kubectl create -f src/main/resources/fabric8/hazelcast-service.yaml
$ kubectl create -f src/main/resources/fabric8/hazelcast-deployment.yaml
```

or once your Minishift cluster is up and running:

```
$ oc create -f src/main/resources/fabric8/hazelcast-service.yaml
$ oc create -f src/main/resources/fabric8/hazelcast-deployment.yaml
```

To check the correct startup of the Hazelcast instance run the following command:

```
$ kubectl get deployment
NAME        DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
hazelcast   1         1         1            1           1m
```

or on Minishift

```
$ oc get deployment
NAME        DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
hazelcast   1         1         1            1           1m
```

and check the status of the pod

```
$ kubectl get pods
NAME                         READY     STATUS    RESTARTS   AGE
hazelcast-69df7cd6c-ccbft    1/1       Running   0          54s
```

on Minishift:

```
$ oc get pods
NAME                         READY     STATUS    RESTARTS   AGE
hazelcast-1638707704-n64tk   1/1       Running   0          1m
```

Now you can decide to scale-up your Hazelcast cluster

```
$ kubectl scale deployment hazelcast --replicas 4
```

on Minishift

```
$ oc scale deployment hazelcast --replicas=4
```

and again check the status of your pods

```
$ kubectl get pods
NAME                         READY     STATUS    RESTARTS   AGE
hazelcast-69df7cd6c-2ps79    1/1       Running   0          30s
hazelcast-69df7cd6c-ccbft    1/1       Running   0          1m
hazelcast-69df7cd6c-csdwr    1/1       Running   0          30s
hazelcast-69df7cd6c-ghxgq    1/1       Running   0          30s

```

on Minishift

```
$ oc get pods
NAME                         READY     STATUS    RESTARTS   AGE
hazelcast-1638707704-g8qwh   1/1       Running   0          1m
hazelcast-1638707704-n64tk   1/1       Running   0          3m
hazelcast-1638707704-wwwff   1/1       Running   0          1m
hazelcast-1638707704-z1g6r   1/1       Running   0          1m

```

You can also take a look at the logs from the pods with kubectl or oc

```
kubectl logs hazelcast-69df7cd6c-ghxgq
2018-02-19 07:14:43.728  INFO 5 --- [           main] com.github.pires.hazelcast.Application   : Starting Application on hazelcast-69df7cd6c-ghxgq with PID 5 (/bootstrapper.jar started by root in /)
2018-02-19 07:14:43.751  INFO 5 --- [           main] com.github.pires.hazelcast.Application   : No active profile set, falling back to default profiles: default
2018-02-19 07:14:43.841  INFO 5 --- [           main] s.c.a.AnnotationConfigApplicationContext : Refreshing org.springframework.context.annotation.AnnotationConfigApplicationContext@5f4da5c3: startup date [Mon Feb 19 07:14:43 GMT 2018]; root of context hierarchy
2018-02-19 07:14:44.636  INFO 5 --- [           main] o.s.j.e.a.AnnotationMBeanExporter        : Registering beans for JMX exposure on startup
2018-02-19 07:14:44.647  INFO 5 --- [           main] c.g.p.h.HazelcastDiscoveryController     : Asking k8s registry at https://kubernetes.default.svc.cluster.local..
2018-02-19 07:14:44.993  INFO 5 --- [           main] c.g.p.h.HazelcastDiscoveryController     : Found 2 pods running Hazelcast.
2018-02-19 07:14:45.060  INFO 5 --- [           main] com.hazelcast.instance.AddressPicker     : [LOCAL] [someGroup] [3.9.3] Interfaces is disabled, trying to pick one address from TCP-IP config addresses: [172.17.0.4, 172.17.0.5]
2018-02-19 07:14:45.060  INFO 5 --- [           main] com.hazelcast.instance.AddressPicker     : [LOCAL] [someGroup] [3.9.3] Prefer IPv4 stack is true.
2018-02-19 07:14:45.065  INFO 5 --- [           main] com.hazelcast.instance.AddressPicker     : [LOCAL] [someGroup] [3.9.3] Picked [172.17.0.5]:5701, using socket ServerSocket[addr=/0.0.0.0,localport=5701], bind any local is true
2018-02-19 07:14:45.105  INFO 5 --- [           main] com.hazelcast.system                     : [172.17.0.5]:5701 [someGroup] [3.9.3] Hazelcast 3.9.3 (20180216 - 539b124) starting at [172.17.0.5]:5701
2018-02-19 07:14:45.105  INFO 5 --- [           main] com.hazelcast.system                     : [172.17.0.5]:5701 [someGroup] [3.9.3] Copyright (c) 2008-2018, Hazelcast, Inc. All Rights Reserved.
2018-02-19 07:14:45.105  INFO 5 --- [           main] com.hazelcast.system                     : [172.17.0.5]:5701 [someGroup] [3.9.3] Configured Hazelcast Serialization version: 1
2018-02-19 07:14:45.370  INFO 5 --- [           main] c.h.s.i.o.impl.BackpressureRegulator     : [172.17.0.5]:5701 [someGroup] [3.9.3] Backpressure is disabled
2018-02-19 07:14:46.712  INFO 5 --- [           main] com.hazelcast.instance.Node              : [172.17.0.5]:5701 [someGroup] [3.9.3] Creating TcpIpJoiner
2018-02-19 07:14:47.218  INFO 5 --- [           main] c.h.s.i.o.impl.OperationExecutorImpl     : [172.17.0.5]:5701 [someGroup] [3.9.3] Starting 2 partition threads and 3 generic threads (1 dedicated for priority tasks)
2018-02-19 07:14:47.221  INFO 5 --- [           main] c.h.internal.diagnostics.Diagnostics     : [172.17.0.5]:5701 [someGroup] [3.9.3] Diagnostics disabled. To enable add -Dhazelcast.diagnostics.enabled=true to the JVM arguments.
2018-02-19 07:14:47.227  INFO 5 --- [           main] com.hazelcast.core.LifecycleService      : [172.17.0.5]:5701 [someGroup] [3.9.3] [172.17.0.5]:5701 is STARTING
2018-02-19 07:14:47.274  INFO 5 --- [cached.thread-3] com.hazelcast.nio.tcp.TcpIpConnector     : [172.17.0.5]:5701 [someGroup] [3.9.3] Connecting to /172.17.0.4:5701, timeout: 0, bind-any: true
2018-02-19 07:14:47.283  INFO 5 --- [cached.thread-3] c.h.nio.tcp.TcpIpConnectionManager       : [172.17.0.5]:5701 [someGroup] [3.9.3] Established socket connection between /172.17.0.5:34227 and /172.17.0.4:5701
2018-02-19 07:14:54.177  INFO 5 --- [thread-Acceptor] com.hazelcast.nio.tcp.TcpIpAcceptor      : [172.17.0.5]:5701 [someGroup] [3.9.3] Accepting socket connection from /172.17.0.7:59967
2018-02-19 07:14:54.200  INFO 5 --- [cached.thread-3] c.h.nio.tcp.TcpIpConnectionManager       : [172.17.0.5]:5701 [someGroup] [3.9.3] Established socket connection between /172.17.0.5:5701 and /172.17.0.7:59967
2018-02-19 07:14:54.411  INFO 5 --- [ration.thread-0] com.hazelcast.system                     : [172.17.0.5]:5701 [someGroup] [3.9.3] Cluster version set to 3.9
2018-02-19 07:14:54.429  INFO 5 --- [ration.thread-0] c.h.internal.cluster.ClusterService      : [172.17.0.5]:5701 [someGroup] [3.9.3] 

Members {size:2, ver:2} [
	Member [172.17.0.4]:5701 - 59045d20-faf3-4a73-b4de-e8036f4b7caa
	Member [172.17.0.5]:5701 - e737cd89-cbf1-4358-8d5a-f5b06a464c4a this
]

2018-02-19 07:14:55.482  INFO 5 --- [thread-Acceptor] com.hazelcast.nio.tcp.TcpIpAcceptor      : [172.17.0.5]:5701 [someGroup] [3.9.3] Accepting socket connection from /172.17.0.6:38585
2018-02-19 07:14:55.516  INFO 5 --- [cached.thread-3] c.h.nio.tcp.TcpIpConnectionManager       : [172.17.0.5]:5701 [someGroup] [3.9.3] Established socket connection between /172.17.0.5:5701 and /172.17.0.6:38585
2018-02-19 07:14:56.330  INFO 5 --- [           main] com.hazelcast.core.LifecycleService      : [172.17.0.5]:5701 [someGroup] [3.9.3] [172.17.0.5]:5701 is STARTED
2018-02-19 07:14:56.339  INFO 5 --- [           main] com.github.pires.hazelcast.Application   : Started Application in 13.151 seconds (JVM running for 13.526)
2018-02-19 07:15:02.079  INFO 5 --- [ration.thread-0] c.h.internal.cluster.ClusterService      : [172.17.0.5]:5701 [someGroup] [3.9.3] 

Members {size:4, ver:3} [
	Member [172.17.0.4]:5701 - 59045d20-faf3-4a73-b4de-e8036f4b7caa
	Member [172.17.0.5]:5701 - e737cd89-cbf1-4358-8d5a-f5b06a464c4a this
	Member [172.17.0.7]:5701 - d80f7b66-26b1-4b48-92ea-c07ddac05314
	Member [172.17.0.6]:5701 - b1c0aa3a-760e-4d89-955b-c1650e1e5661
]


```

### Building and running

Navigate to the project folder and the example can be built with

    $ mvn clean -Pkubernetes-install fabric8:deploy

When the example runs in fabric8, you can use the Kubectl command tool to inspect the status

To list all the running pods on Minikube:

    $ kubectl get pods

Then find the name on Minikube of the pod that runs this quickstart, and output the logs from the running pods with:

    $ kubectl logs <name of pod>

To list all the running pods on Minishift:

    $ oc get pods

Then find the name on Minishift of the pod that runs this quickstart, and output the logs from the running pods with:

    $ kubectl logs <name of pod>

and you should see something like this:

```
Feb 19, 2018 7:18:39 AM com.hazelcast.client.connection.ClientConnectionManager
INFO: hz.client_0 [someGroup] [3.9.2] Setting ClientConnection{alive=true, connectionId=1, channel=NioChannel{/172.17.0.8:41011->hazelcast/10.102.1.255:5701}, remoteEndpoint=[172.17.0.4]:5701, lastReadTime=2018-02-19 07:18:39.464, lastWriteTime=2018-02-19 07:18:39.424, closedTime=never, lastHeartbeatRequested=never, lastHeartbeatReceived=never, connected server version=3.9.3} as owner with principal ClientPrincipal{uuid='0daabf2b-0b33-4a55-8453-683d7fa0436e', ownerUuid='59045d20-faf3-4a73-b4de-e8036f4b7caa'}
Feb 19, 2018 7:18:39 AM com.hazelcast.core.LifecycleService
INFO: hz.client_0 [someGroup] [3.9.2] HazelcastClient 3.9.2 (20180103 - 17e4ec3) is CLIENT_CONNECTED
Feb 19, 2018 7:18:39 AM com.hazelcast.internal.diagnostics.Diagnostics
INFO: hz.client_0 [someGroup] [3.9.2] Diagnostics disabled. To enable add -Dhazelcast.diagnostics.enabled=true to the JVM arguments.
2018-02-19 07:18:39,582 [main           ] INFO  SpringCamelContext             - Apache Camel 2.21.0-SNAPSHOT (CamelContext: camel-1) is starting
2018-02-19 07:18:39,583 [main           ] INFO  ManagedManagementStrategy      - JMX is enabled
2018-02-19 07:18:39,842 [main           ] INFO  DefaultTypeConverter           - Type converters loaded (core: 193, classpath: 1)
2018-02-19 07:18:40,028 [main           ] INFO  SpringCamelContext             - StreamCaching is not in use. If using streams then its recommended to enable stream caching. See more details at http://camel.apache.org/stream-caching.html
Feb 19, 2018 7:18:40 AM com.hazelcast.client.connection.ClientConnectionManager
INFO: hz.client_0 [someGroup] [3.9.2] Authenticated with server [172.17.0.6]:5701, server version:3.9.3 Local address: /172.17.0.8:46877
Feb 19, 2018 7:18:40 AM com.hazelcast.client.connection.ClientConnectionManager
INFO: hz.client_0 [someGroup] [3.9.2] Authenticated with server [172.17.0.5]:5701, server version:3.9.3 Local address: /172.17.0.8:36763
Feb 19, 2018 7:18:40 AM com.hazelcast.client.connection.ClientConnectionManager
INFO: hz.client_0 [someGroup] [3.9.2] Authenticated with server [172.17.0.7]:5701, server version:3.9.3 Local address: /172.17.0.8:34969
2018-02-19 07:18:40,837 [main           ] INFO  SpringCamelContext             - Route: route1 started and consuming from: timer://foo?period=5000
2018-02-19 07:18:40,838 [main           ] INFO  SpringCamelContext             - Route: route2 started and consuming from: hazelcast-topic://foo
2018-02-19 07:18:40,838 [main           ] INFO  SpringCamelContext             - Total 2 routes, of which 2 are started
2018-02-19 07:18:40,840 [main           ] INFO  SpringCamelContext             - Apache Camel 2.21.0-SNAPSHOT (CamelContext: camel-1) started in 1.258 seconds
2018-02-19 07:18:40,843 [main           ] INFO  DefaultLifecycleProcessor      - Starting beans in phase 2147483646
2018-02-19 07:18:41,846 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2018-02-19 07:18:41,886 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2018-02-19 07:18:46,840 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2018-02-19 07:18:46,842 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2018-02-19 07:18:51,840 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2018-02-19 07:18:51,842 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received

```

### Cleanup

Run following to undeploy on Minikube

```
$ mvn -Pkubernetes-install fabric8:undeploy
$ kubectl delete -f src/main/resources/fabric8/hazelcast-deployment.yaml
$ kubectl delete -f src/main/resources/fabric8/hazelcast-service.yaml
```

Run following to undeploy on Minishift

```
$ mvn -Pkubernetes-install fabric8:undeploy
$ oc delete -f src/main/resources/fabric8/hazelcast-deployment.yaml
$ oc delete -f src/main/resources/fabric8/hazelcast-service.yaml
```

Make sure no pod is running
```
$ kubectl get pods
No resources found.
```

```
$ oc get pods
No resources found.
```
