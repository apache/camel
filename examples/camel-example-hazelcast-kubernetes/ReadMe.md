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
hazelcast-7c7b47dd84-22zcp   1/1       Running   0          6m
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
hazelcast-7c7b47dd84-22zcp   1/1       Running   0          8m
hazelcast-7c7b47dd84-gphbv   1/1       Running   0          1m
hazelcast-7c7b47dd84-qstjb   1/1       Running   0          1m
hazelcast-7c7b47dd84-swxm9   1/1       Running   0          1m

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
kubectl logs hazelcast-7c7b47dd84-22zcp
2018-01-15 11:00:32.006  INFO 7 --- [           main] com.github.pires.hazelcast.Application   : Starting Application on hazelcast-7c7b47dd84-22zcp with PID 7 (/bootstrapper.jar started by root in /)
2018-01-15 11:00:32.017  INFO 7 --- [           main] com.github.pires.hazelcast.Application   : No active profile set, falling back to default profiles: default
2018-01-15 11:00:32.084  INFO 7 --- [           main] s.c.a.AnnotationConfigApplicationContext : Refreshing org.springframework.context.annotation.AnnotationConfigApplicationContext@5f4da5c3: startup date [Mon Jan 15 11:00:32 GMT 2018]; root of context hierarchy
2018-01-15 11:00:32.852  INFO 7 --- [           main] o.s.j.e.a.AnnotationMBeanExporter        : Registering beans for JMX exposure on startup
2018-01-15 11:00:32.862  INFO 7 --- [           main] c.g.p.h.HazelcastDiscoveryController     : Asking k8s registry at https://kubernetes.default.svc.cluster.local..
2018-01-15 11:00:33.181  INFO 7 --- [           main] c.g.p.h.HazelcastDiscoveryController     : Found 1 pods running Hazelcast.
2018-01-15 11:00:33.245  INFO 7 --- [           main] com.hazelcast.instance.AddressPicker     : [LOCAL] [someGroup] [3.9.2] Interfaces is disabled, trying to pick one address from TCP-IP config addresses: [172.17.0.4]
2018-01-15 11:00:33.245  INFO 7 --- [           main] com.hazelcast.instance.AddressPicker     : [LOCAL] [someGroup] [3.9.2] Prefer IPv4 stack is true.
2018-01-15 11:00:33.250  INFO 7 --- [           main] com.hazelcast.instance.AddressPicker     : [LOCAL] [someGroup] [3.9.2] Picked [172.17.0.4]:5701, using socket ServerSocket[addr=/0.0.0.0,localport=5701], bind any local is true
2018-01-15 11:00:33.269  INFO 7 --- [           main] com.hazelcast.system                     : [172.17.0.4]:5701 [someGroup] [3.9.2] Hazelcast 3.9.2 (20180103 - 17e4ec3) starting at [172.17.0.4]:5701
2018-01-15 11:00:33.269  INFO 7 --- [           main] com.hazelcast.system                     : [172.17.0.4]:5701 [someGroup] [3.9.2] Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
2018-01-15 11:00:33.270  INFO 7 --- [           main] com.hazelcast.system                     : [172.17.0.4]:5701 [someGroup] [3.9.2] Configured Hazelcast Serialization version: 1
2018-01-15 11:00:33.523  INFO 7 --- [           main] c.h.s.i.o.impl.BackpressureRegulator     : [172.17.0.4]:5701 [someGroup] [3.9.2] Backpressure is disabled
2018-01-15 11:00:34.034  INFO 7 --- [           main] com.hazelcast.instance.Node              : [172.17.0.4]:5701 [someGroup] [3.9.2] Creating TcpIpJoiner
2018-01-15 11:00:34.205  INFO 7 --- [           main] c.h.s.i.o.impl.OperationExecutorImpl     : [172.17.0.4]:5701 [someGroup] [3.9.2] Starting 2 partition threads and 3 generic threads (1 dedicated for priority tasks)
2018-01-15 11:00:34.215  INFO 7 --- [           main] c.h.internal.diagnostics.Diagnostics     : [172.17.0.4]:5701 [someGroup] [3.9.2] Diagnostics disabled. To enable add -Dhazelcast.diagnostics.enabled=true to the JVM arguments.
2018-01-15 11:00:34.223  INFO 7 --- [           main] com.hazelcast.core.LifecycleService      : [172.17.0.4]:5701 [someGroup] [3.9.2] [172.17.0.4]:5701 is STARTING
2018-01-15 11:00:34.236  INFO 7 --- [           main] com.hazelcast.system                     : [172.17.0.4]:5701 [someGroup] [3.9.2] Cluster version set to 3.9
2018-01-15 11:00:34.238  INFO 7 --- [           main] c.h.internal.cluster.ClusterService      : [172.17.0.4]:5701 [someGroup] [3.9.2] 

Members {size:1, ver:1} [
	Member [172.17.0.4]:5701 - a14cd4e8-803d-4da2-990a-ed1e979b8a14 this
]

2018-01-15 11:00:34.262  INFO 7 --- [           main] com.hazelcast.core.LifecycleService      : [172.17.0.4]:5701 [someGroup] [3.9.2] [172.17.0.4]:5701 is STARTED
2018-01-15 11:00:34.264  INFO 7 --- [           main] com.github.pires.hazelcast.Application   : Started Application in 2.734 seconds (JVM running for 3.101)
2018-01-15 11:07:15.059  INFO 7 --- [thread-Acceptor] com.hazelcast.nio.tcp.TcpIpAcceptor      : [172.17.0.4]:5701 [someGroup] [3.9.2] Accepting socket connection from /172.17.0.6:57515
2018-01-15 11:07:15.072  INFO 7 --- [cached.thread-2] c.h.nio.tcp.TcpIpConnectionManager       : [172.17.0.4]:5701 [someGroup] [3.9.2] Established socket connection between /172.17.0.4:5701 and /172.17.0.6:57515
2018-01-15 11:07:21.056  INFO 7 --- [thread-Acceptor] com.hazelcast.nio.tcp.TcpIpAcceptor      : [172.17.0.4]:5701 [someGroup] [3.9.2] Accepting socket connection from /172.17.0.7:54285
2018-01-15 11:07:21.057  INFO 7 --- [cached.thread-2] c.h.nio.tcp.TcpIpConnectionManager       : [172.17.0.4]:5701 [someGroup] [3.9.2] Established socket connection between /172.17.0.4:5701 and /172.17.0.7:54285
2018-01-15 11:07:22.072  INFO 7 --- [ration.thread-1] c.h.internal.cluster.ClusterService      : [172.17.0.4]:5701 [someGroup] [3.9.2] 

Members {size:2, ver:2} [
	Member [172.17.0.4]:5701 - a14cd4e8-803d-4da2-990a-ed1e979b8a14 this
	Member [172.17.0.6]:5701 - afba6c0c-5f7e-4e1f-b51e-a5ed9f586427
]

2018-01-15 11:07:22.581  INFO 7 --- [thread-Acceptor] com.hazelcast.nio.tcp.TcpIpAcceptor      : [172.17.0.4]:5701 [someGroup] [3.9.2] Accepting socket connection from /172.17.0.5:54921
2018-01-15 11:07:22.584  INFO 7 --- [cached.thread-2] c.h.nio.tcp.TcpIpConnectionManager       : [172.17.0.4]:5701 [someGroup] [3.9.2] Established socket connection between /172.17.0.4:5701 and /172.17.0.5:54921
2018-01-15 11:07:29.071  INFO 7 --- [ration.thread-0] c.h.internal.cluster.ClusterService      : [172.17.0.4]:5701 [someGroup] [3.9.2] 

Members {size:4, ver:3} [
	Member [172.17.0.4]:5701 - a14cd4e8-803d-4da2-990a-ed1e979b8a14 this
	Member [172.17.0.6]:5701 - afba6c0c-5f7e-4e1f-b51e-a5ed9f586427
	Member [172.17.0.7]:5701 - ce905f72-648a-4cdc-9e43-ac7f44ca96bc
	Member [172.17.0.5]:5701 - 2cceb0c0-a88f-4d7e-9308-a4d10645897d
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
Jan 15, 2018 11:13:04 AM com.hazelcast.client.connection.ClientConnectionManager
INFO: hz.client_0 [someGroup] [3.9.2] Setting ClientConnection{alive=true, connectionId=1, channel=NioChannel{/172.17.0.8:42365->hazelcast/10.102.23.30:5701}, remoteEndpoint=[172.17.0.5]:5701, lastReadTime=2018-01-15 11:13:04.747, lastWriteTime=2018-01-15 11:13:04.735, closedTime=never, lastHeartbeatRequested=never, lastHeartbeatReceived=never, connected server version=3.9.2} as owner with principal ClientPrincipal{uuid='50b01afc-9ac5-4500-b925-2538b7600848', ownerUuid='2cceb0c0-a88f-4d7e-9308-a4d10645897d'}
Jan 15, 2018 11:13:04 AM com.hazelcast.core.LifecycleService
INFO: hz.client_0 [someGroup] [3.9.2] HazelcastClient 3.9.2 (20180103 - 17e4ec3) is CLIENT_CONNECTED
Jan 15, 2018 11:13:04 AM com.hazelcast.internal.diagnostics.Diagnostics
INFO: hz.client_0 [someGroup] [3.9.2] Diagnostics disabled. To enable add -Dhazelcast.diagnostics.enabled=true to the JVM arguments.
2018-01-15 11:13:04,788 [main           ] INFO  SpringCamelContext             - Apache Camel 2.21.0-SNAPSHOT (CamelContext: camel-1) is starting
2018-01-15 11:13:04,789 [main           ] INFO  ManagedManagementStrategy      - JMX is enabled
2018-01-15 11:13:04,869 [main           ] INFO  DefaultTypeConverter           - Type converters loaded (core: 193, classpath: 1)
2018-01-15 11:13:04,948 [main           ] INFO  SpringCamelContext             - StreamCaching is not in use. If using streams then its recommended to enable stream caching. See more details at http://camel.apache.org/stream-caching.html
Jan 15, 2018 11:13:05 AM com.hazelcast.client.connection.ClientConnectionManager
INFO: hz.client_0 [someGroup] [3.9.2] Authenticated with server [172.17.0.4]:5701, server version:3.9.2 Local address: /172.17.0.8:39383
Jan 15, 2018 11:13:05 AM com.hazelcast.client.connection.ClientConnectionManager
INFO: hz.client_0 [someGroup] [3.9.2] Authenticated with server [172.17.0.6]:5701, server version:3.9.2 Local address: /172.17.0.8:39605
Jan 15, 2018 11:13:05 AM com.hazelcast.client.connection.ClientConnectionManager
INFO: hz.client_0 [someGroup] [3.9.2] Authenticated with server [172.17.0.7]:5701, server version:3.9.2 Local address: /172.17.0.8:37819
2018-01-15 11:13:05,162 [main           ] INFO  SpringCamelContext             - Route: route1 started and consuming from: timer://foo?period=5000
2018-01-15 11:13:05,163 [main           ] INFO  SpringCamelContext             - Route: route2 started and consuming from: hazelcast-topic://foo
2018-01-15 11:13:05,163 [main           ] INFO  SpringCamelContext             - Total 2 routes, of which 2 are started
2018-01-15 11:13:05,170 [main           ] INFO  SpringCamelContext             - Apache Camel 2.21.0-SNAPSHOT (CamelContext: camel-1) started in 0.382 seconds
2018-01-15 11:13:05,172 [main           ] INFO  DefaultLifecycleProcessor      - Starting beans in phase 2147483646
2018-01-15 11:13:06,170 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2018-01-15 11:13:06,188 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2018-01-15 11:13:11,164 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2018-01-15 11:13:11,167 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2018-01-15 11:13:16,165 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2018-01-15 11:13:16,167 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2018-01-15 11:13:21,166 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2018-01-15 11:13:21,174 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2018-01-15 11:13:26,167 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2018-01-15 11:13:26,169 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2018-01-15 11:13:31,166 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2018-01-15 11:13:31,168 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2018-01-15 11:13:36,167 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2018-01-15 11:13:36,169 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2018-01-15 11:13:41,167 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2018-01-15 11:13:41,169 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2018-01-15 11:13:46,168 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2018-01-15 11:13:46,170 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received

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
