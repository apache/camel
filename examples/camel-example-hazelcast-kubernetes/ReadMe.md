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
hazelcast-1638707704-n64tk   1/1       Running   0          1m
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
hazelcast-1638707704-g8qwh   1/1       Running   0          1m
hazelcast-1638707704-n64tk   1/1       Running   0          3m
hazelcast-1638707704-wwwff   1/1       Running   0          1m
hazelcast-1638707704-z1g6r   1/1       Running   0          1m

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
kubectl logs hazelcast-414548760-fb5bh
2017-11-23 13:15:12.243  INFO 7 --- [           main] com.github.pires.hazelcast.Application   : Starting Application on hazelcast-1638707704-z1g6r with PID 7 (/bootstrapper.jar started by root in /)
2017-11-23 13:15:12.248  INFO 7 --- [           main] com.github.pires.hazelcast.Application   : No active profile set, falling back to default profiles: default
2017-11-23 13:15:12.290  INFO 7 --- [           main] s.c.a.AnnotationConfigApplicationContext : Refreshing org.springframework.context.annotation.AnnotationConfigApplicationContext@14514713: startup date [Thu Nov 23 13:15:12 GMT 2017]; root of context hierarchy
2017-11-23 13:15:13.081  INFO 7 --- [           main] o.s.j.e.a.AnnotationMBeanExporter        : Registering beans for JMX exposure on startup
2017-11-23 13:15:13.089  INFO 7 --- [           main] c.g.p.h.HazelcastDiscoveryController     : Asking k8s registry at https://kubernetes.default.svc.cluster.local..
2017-11-23 13:15:13.442  INFO 7 --- [           main] c.g.p.h.HazelcastDiscoveryController     : Found 2 pods running Hazelcast.
2017-11-23 13:15:13.512  INFO 7 --- [           main] com.hazelcast.instance.AddressPicker     : [LOCAL] [someGroup] [3.9] Interfaces is disabled, trying to pick one address from TCP-IP config addresses: [172.17.0.6, 172.17.0.7]
2017-11-23 13:15:13.513  INFO 7 --- [           main] com.hazelcast.instance.AddressPicker     : [LOCAL] [someGroup] [3.9] Prefer IPv4 stack is true.
2017-11-23 13:15:13.519  INFO 7 --- [           main] com.hazelcast.instance.AddressPicker     : [LOCAL] [someGroup] [3.9] Picked [172.17.0.7]:5701, using socket ServerSocket[addr=/0:0:0:0:0:0:0:0,localport=5701], bind any local is true
2017-11-23 13:15:13.538  INFO 7 --- [           main] com.hazelcast.system                     : [172.17.0.7]:5701 [someGroup] [3.9] Hazelcast 3.9 (20171023 - b29f549) starting at [172.17.0.7]:5701
2017-11-23 13:15:13.538  INFO 7 --- [           main] com.hazelcast.system                     : [172.17.0.7]:5701 [someGroup] [3.9] Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
2017-11-23 13:15:13.538  INFO 7 --- [           main] com.hazelcast.system                     : [172.17.0.7]:5701 [someGroup] [3.9] Configured Hazelcast Serialization version: 1
2017-11-23 13:15:13.797  INFO 7 --- [           main] c.h.s.i.o.impl.BackpressureRegulator     : [172.17.0.7]:5701 [someGroup] [3.9] Backpressure is disabled
2017-11-23 13:15:14.293  INFO 7 --- [           main] com.hazelcast.instance.Node              : [172.17.0.7]:5701 [someGroup] [3.9] Creating TcpIpJoiner
2017-11-23 13:15:14.428  INFO 7 --- [           main] c.h.s.i.o.impl.OperationExecutorImpl     : [172.17.0.7]:5701 [someGroup] [3.9] Starting 4 partition threads and 3 generic threads (1 dedicated for priority tasks)
2017-11-23 13:15:14.433  INFO 7 --- [           main] c.h.internal.diagnostics.Diagnostics     : [172.17.0.7]:5701 [someGroup] [3.9] Diagnostics disabled. To enable add -Dhazelcast.diagnostics.enabled=true to the JVM arguments.
2017-11-23 13:15:14.438  INFO 7 --- [           main] com.hazelcast.core.LifecycleService      : [172.17.0.7]:5701 [someGroup] [3.9] [172.17.0.7]:5701 is STARTING
2017-11-23 13:15:14.465  INFO 7 --- [cached.thread-3] com.hazelcast.nio.tcp.TcpIpConnector     : [172.17.0.7]:5701 [someGroup] [3.9] Connecting to /172.17.0.6:5701, timeout: 0, bind-any: true
2017-11-23 13:15:14.468  INFO 7 --- [cached.thread-3] c.h.nio.tcp.TcpIpConnectionManager       : [172.17.0.7]:5701 [someGroup] [3.9] Established socket connection between /172.17.0.7:59483 and /172.17.0.6:5701
2017-11-23 13:15:18.171  INFO 7 --- [thread-Acceptor] com.hazelcast.nio.tcp.TcpIpAcceptor      : [172.17.0.7]:5701 [someGroup] [3.9] Accepting socket connection from /172.17.0.8:35427
2017-11-23 13:15:18.172  INFO 7 --- [cached.thread-3] c.h.nio.tcp.TcpIpConnectionManager       : [172.17.0.7]:5701 [someGroup] [3.9] Established socket connection between /172.17.0.7:5701 and /172.17.0.8:35427
2017-11-23 13:15:21.888  INFO 7 --- [thread-Acceptor] com.hazelcast.nio.tcp.TcpIpAcceptor      : [172.17.0.7]:5701 [someGroup] [3.9] Accepting socket connection from /172.17.0.9:45315
2017-11-23 13:15:21.890  INFO 7 --- [cached.thread-2] c.h.nio.tcp.TcpIpConnectionManager       : [172.17.0.7]:5701 [someGroup] [3.9] Established socket connection between /172.17.0.7:5701 and /172.17.0.9:45315
2017-11-23 13:15:27.900  INFO 7 --- [ration.thread-1] com.hazelcast.system                     : [172.17.0.7]:5701 [someGroup] [3.9] Cluster version set to 3.9
2017-11-23 13:15:27.903  INFO 7 --- [ration.thread-1] c.h.internal.cluster.ClusterService      : [172.17.0.7]:5701 [someGroup] [3.9] 

Members {size:4, ver:2} [
	Member [172.17.0.6]:5701 - b9cc1fbb-fd27-4b0f-b6c6-9ba693c45e08
	Member [172.17.0.7]:5701 - bcbbbcb4-7fd8-4a19-a2ed-0a3440b30887 this
	Member [172.17.0.8]:5701 - 260d1057-d28e-40ff-9a0b-5ed4d23e5576
	Member [172.17.0.9]:5701 - 6a0450c7-e9ea-4b92-91ee-71462f4362e3
]

2017-11-23 13:15:29.497  INFO 7 --- [           main] com.hazelcast.core.LifecycleService      : [172.17.0.7]:5701 [someGroup] [3.9] [172.17.0.7]:5701 is STARTED
2017-11-23 13:15:29.498  INFO 7 --- [           main] com.github.pires.hazelcast.Application   : Started Application in 17.704 seconds (JVM running for 18.099)

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
INFO: hz.client_0 [someGroup] [3.9] Setting ClientConnection{alive=true, connectionId=1, channel=NioChannel{/172.17.0.10:44111->hazelcast/10.0.0.110:5701}, remoteEndpoint=[172.17.0.8]:5701, lastReadTime=2017-11-23 13:19:30.465, lastWriteTime=2017-11-23 13:19:30.462, closedTime=never, lastHeartbeatRequested=never, lastHeartbeatReceived=never, connected server version=3.9} as owner with principal ClientPrincipal{uuid='72e2102a-e64b-4138-8f6e-7160005533a8', ownerUuid='260d1057-d28e-40ff-9a0b-5ed4d23e5576'}
Nov 23, 2017 1:19:30 PM com.hazelcast.core.LifecycleService
INFO: hz.client_0 [someGroup] [3.9] HazelcastClient 3.9 (20171023 - b29f549) is CLIENT_CONNECTED
Nov 23, 2017 1:19:30 PM com.hazelcast.internal.diagnostics.Diagnostics
INFO: hz.client_0 [someGroup] [3.9] Diagnostics disabled. To enable add -Dhazelcast.diagnostics.enabled=true to the JVM arguments.
2017-11-23 13:19:30,503 [main           ] INFO  SpringCamelContext             - Apache Camel 2.21.0-SNAPSHOT (CamelContext: camel-1) is starting
2017-11-23 13:19:30,515 [main           ] INFO  ManagedManagementStrategy      - JMX is enabled
2017-11-23 13:19:30,601 [main           ] INFO  DefaultTypeConverter           - Type converters loaded (core: 193, classpath: 1)
2017-11-23 13:19:30,766 [main           ] INFO  SpringCamelContext             - StreamCaching is not in use. If using streams then its recommended to enable stream caching. See more details at http://camel.apache.org/stream-caching.html
Nov 23, 2017 1:19:30 PM com.hazelcast.client.connection.ClientConnectionManager
INFO: hz.client_0 [someGroup] [3.9] Authenticated with server [172.17.0.6]:5701, server version:3.9 Local address: /172.17.0.10:44473
Nov 23, 2017 1:19:30 PM com.hazelcast.client.connection.ClientConnectionManager
INFO: hz.client_0 [someGroup] [3.9] Authenticated with server [172.17.0.7]:5701, server version:3.9 Local address: /172.17.0.10:39823
Nov 23, 2017 1:19:30 PM com.hazelcast.client.connection.ClientConnectionManager
INFO: hz.client_0 [someGroup] [3.9] Authenticated with server [172.17.0.9]:5701, server version:3.9 Local address: /172.17.0.10:44799
2017-11-23 13:19:30,933 [main           ] INFO  SpringCamelContext             - Route: route1 started and consuming from: timer://foo?period=5000
2017-11-23 13:19:30,934 [main           ] INFO  SpringCamelContext             - Route: route2 started and consuming from: hazelcast-topic://foo
2017-11-23 13:19:30,935 [main           ] INFO  SpringCamelContext             - Total 2 routes, of which 2 are started
2017-11-23 13:19:30,938 [main           ] INFO  SpringCamelContext             - Apache Camel 2.21.0-SNAPSHOT (CamelContext: camel-1) started in 0.434 seconds
2017-11-23 13:19:30,941 [main           ] INFO  DefaultLifecycleProcessor      - Starting beans in phase 2147483646
2017-11-23 13:19:31,943 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-11-23 13:19:31,957 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2017-11-23 13:19:36,936 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-11-23 13:19:36,941 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2017-11-23 13:19:41,937 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-11-23 13:19:41,939 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2017-11-23 13:19:46,936 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-11-23 13:19:46,938 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2017-11-23 13:19:51,936 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-11-23 13:19:51,939 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2017-11-23 13:19:56,936 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-11-23 13:19:56,938 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2017-11-23 13:20:01,936 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-11-23 13:20:01,938 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2017-11-23 13:20:06,936 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-11-23 13:20:06,941 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received

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
