# Camel Hazelcast route on Kubernetes cluster

This quickstart run in a Java standalone container, using Spring with Apache Camel (Hazelcast component).

This quickstart is based on the Kubernetes example here: https://github.com/kubernetes/kubernetes/tree/master/examples/storage/hazelcast

This example is based on:

- Minikube (Kubernetes version >= 1.5)
- Fabric8 Maven Plugin (version >= 3.2)

First thing you'll need to do is preparing the environment.

Once your Minikube node is up and running you'll need to run the following command.
In your src/main/resources/fabric8/ folder you'll find two yaml file. Run the following command using them:

```
kubectl create -f src/main/resources/fabric8/hazelcast-service.yaml
kubectl create -f src/main/resources/fabric8/hazelcast-deployment.yaml
```

To check the correct startup of the Hazelcast instance run the following command:

```
kubectl get deployment
NAME        DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
hazelcast   1         1         1            1           1m
```

and check the status of the pod

```
kubectl get pods
NAME                                        READY     STATUS             RESTARTS   AGE
hazelcast-414548760-fb5bh                   1/1       Running            0          29s
```

Now you can decide to scale-up your Hazelcast cluster

```
kubectl scale deployment hazelcast --replicas 4
```

and again check the status of your pods

```
kubectl get pods
NAME                                                  READY     STATUS    RESTARTS   AGE
hazelcast-414548760-37ktz                             1/1       Running   0          23m
hazelcast-414548760-fb5bh                             1/1       Running   0          1d
hazelcast-414548760-qntg7                             1/1       Running   0          24m
hazelcast-414548760-t38cp                             1/1       Running   0          23m
```

You can also take a look at the logs from the pods:

```
kubectl logs hazelcast-414548760-fb5bh
2017-09-12 12:06:17.638  INFO 7 --- [           main] com.github.pires.hazelcast.Application   : Starting Application on hazelcast-414548760-fb5bh with PID 7 (/bootstrapper.jar started by root in /)
2017-09-12 12:06:17.658  INFO 7 --- [           main] com.github.pires.hazelcast.Application   : No active profile set, falling back to default profiles: default
2017-09-12 12:06:17.712  INFO 7 --- [           main] s.c.a.AnnotationConfigApplicationContext : Refreshing org.springframework.context.annotation.AnnotationConfigApplicationContext@14514713: startup date [Tue Sep 12 12:06:17 GMT 2017]; root of context hierarchy
2017-09-12 12:06:18.663  INFO 7 --- [           main] o.s.j.e.a.AnnotationMBeanExporter        : Registering beans for JMX exposure on startup
2017-09-12 12:06:18.675  INFO 7 --- [           main] c.g.p.h.HazelcastDiscoveryController     : Asking k8s registry at https://kubernetes.default.svc.cluster.local..
2017-09-12 12:06:19.103  INFO 7 --- [           main] c.g.p.h.HazelcastDiscoveryController     : Found 1 pods running Hazelcast.
2017-09-12 12:06:19.169  INFO 7 --- [           main] c.h.instance.DefaultAddressPicker        : [LOCAL] [someGroup] [3.8.5] Interfaces is disabled, trying to pick one address from TCP-IP config addresses: [172.17.0.4]
2017-09-12 12:06:19.169  INFO 7 --- [           main] c.h.instance.DefaultAddressPicker        : [LOCAL] [someGroup] [3.8.5] Prefer IPv4 stack is true.
2017-09-12 12:06:19.176  INFO 7 --- [           main] c.h.instance.DefaultAddressPicker        : [LOCAL] [someGroup] [3.8.5] Picked [172.17.0.4]:5701, using socket ServerSocket[addr=/0:0:0:0:0:0:0:0,localport=5701], bind any local is true
2017-09-12 12:06:19.191  INFO 7 --- [           main] com.hazelcast.system                     : [172.17.0.4]:5701 [someGroup] [3.8.5] Hazelcast 3.8.5 (20170906 - e424927) starting at [172.17.0.4]:5701
2017-09-12 12:06:19.191  INFO 7 --- [           main] com.hazelcast.system                     : [172.17.0.4]:5701 [someGroup] [3.8.5] Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
2017-09-12 12:06:19.191  INFO 7 --- [           main] com.hazelcast.system                     : [172.17.0.4]:5701 [someGroup] [3.8.5] Configured Hazelcast Serialization version : 1
2017-09-12 12:06:19.423  INFO 7 --- [           main] c.h.s.i.o.impl.BackpressureRegulator     : [172.17.0.4]:5701 [someGroup] [3.8.5] Backpressure is disabled
2017-09-12 12:06:20.048  INFO 7 --- [           main] com.hazelcast.instance.Node              : [172.17.0.4]:5701 [someGroup] [3.8.5] Creating TcpIpJoiner
2017-09-12 12:06:20.242  INFO 7 --- [           main] c.h.s.i.o.impl.OperationExecutorImpl     : [172.17.0.4]:5701 [someGroup] [3.8.5] Starting 2 partition threads
2017-09-12 12:06:20.250  INFO 7 --- [           main] c.h.s.i.o.impl.OperationExecutorImpl     : [172.17.0.4]:5701 [someGroup] [3.8.5] Starting 3 generic threads (1 dedicated for priority tasks)
2017-09-12 12:06:20.270  INFO 7 --- [           main] com.hazelcast.core.LifecycleService      : [172.17.0.4]:5701 [someGroup] [3.8.5] [172.17.0.4]:5701 is STARTING
2017-09-12 12:06:20.296  INFO 7 --- [           main] com.hazelcast.system                     : [172.17.0.4]:5701 [someGroup] [3.8.5] Cluster version set to 3.8
2017-09-12 12:06:20.297  INFO 7 --- [           main] com.hazelcast.cluster.impl.TcpIpJoiner   : [172.17.0.4]:5701 [someGroup] [3.8.5] 


Members [1] {
	Member [172.17.0.4]:5701 - 929e9148-870d-4f43-ba1d-fcc8ff973ab3 this
}

2017-09-12 12:06:20.340  INFO 7 --- [           main] com.hazelcast.core.LifecycleService      : [172.17.0.4]:5701 [someGroup] [3.8.5] [172.17.0.4]:5701 is STARTED
2017-09-12 12:06:20.343  INFO 7 --- [           main] com.github.pires.hazelcast.Application   : Started Application in 3.325 seconds (JVM running for 3.747)
2017-09-12 12:15:05.855  INFO 7 --- [thread-Acceptor] c.h.nio.tcp.SocketAcceptorThread         : [172.17.0.4]:5701 [someGroup] [3.8.5] Accepting socket connection from /172.17.0.7:54699
2017-09-12 12:15:05.863  INFO 7 --- [cached.thread-3] c.h.nio.tcp.TcpIpConnectionManager       : [172.17.0.4]:5701 [someGroup] [3.8.5] Established socket connection between /172.17.0.4:5701 and /172.17.0.7:54699
2017-09-12 12:15:12.856  INFO 7 --- [ration.thread-1] c.h.internal.cluster.ClusterService      : [172.17.0.4]:5701 [someGroup] [3.8.5] 

Members [2] {
	Member [172.17.0.4]:5701 - 929e9148-870d-4f43-ba1d-fcc8ff973ab3 this
	Member [172.17.0.7]:5701 - 6d05a83b-2960-48b3-8dd2-63f6720115f5
}

2017-09-12 12:16:51.699  INFO 7 --- [thread-Acceptor] c.h.nio.tcp.SocketAcceptorThread         : [172.17.0.4]:5701 [someGroup] [3.8.5] Accepting socket connection from /172.17.0.8:48921
2017-09-12 12:16:51.736  INFO 7 --- [cached.thread-3] c.h.nio.tcp.TcpIpConnectionManager       : [172.17.0.4]:5701 [someGroup] [3.8.5] Established socket connection between /172.17.0.4:5701 and /172.17.0.8:48921
2017-09-12 12:16:56.258  INFO 7 --- [thread-Acceptor] c.h.nio.tcp.SocketAcceptorThread         : [172.17.0.4]:5701 [someGroup] [3.8.5] Accepting socket connection from /172.17.0.9:40459
2017-09-12 12:16:56.259  INFO 7 --- [cached.thread-4] c.h.nio.tcp.TcpIpConnectionManager       : [172.17.0.4]:5701 [someGroup] [3.8.5] Established socket connection between /172.17.0.4:5701 and /172.17.0.9:40459
2017-09-12 12:17:02.725  INFO 7 --- [ration.thread-0] c.h.internal.cluster.ClusterService      : [172.17.0.4]:5701 [someGroup] [3.8.5] 

Members [4] {
	Member [172.17.0.4]:5701 - 929e9148-870d-4f43-ba1d-fcc8ff973ab3 this
	Member [172.17.0.7]:5701 - 6d05a83b-2960-48b3-8dd2-63f6720115f5
	Member [172.17.0.8]:5701 - 50ddeb59-8f06-43c6-91a8-c36fd86f825c
	Member [172.17.0.9]:5701 - e79a9a26-971d-4b67-8a46-1a78fee94324
}

```

### Building and running

Navigate to the project folder and the example can be built with

    mvn clean -Pkubernetes-install fabric8:deploy

When the example runs in fabric8, you can use the Kubectl command tool to inspect the status

To list all the running pods:

    kubectl get pods

Then find the name of the pod that runs this quickstart, and output the logs from the running pods with:

    kubectl logs <name of pod>

and you should see something like this:

```
INFO: hz.client_0 [someGroup] [3.8.5] HazelcastClient 3.8.5 (20170906 - e424927) is CLIENT_CONNECTED
2017-09-12 12:42:22,764 [main           ] INFO  SpringCamelContext             - Apache Camel 2.20.0-SNAPSHOT (CamelContext: camel-1) is starting
2017-09-12 12:42:22,765 [main           ] INFO  ManagedManagementStrategy      - JMX is enabled
2017-09-12 12:42:22,880 [main           ] INFO  DefaultTypeConverter           - Type converters loaded (core: 192, classpath: 1)
2017-09-12 12:42:23,012 [main           ] INFO  SpringCamelContext             - StreamCaching is not in use. If using streams then its recommended to enable stream caching. See more details at http://camel.apache.org/stream-caching.html
Sep 12, 2017 12:42:23 PM com.hazelcast.client.connection.ClientConnectionManager
INFO: hz.client_0 [someGroup] [3.8.5] Authenticated with server [172.17.0.9]:5701, server version:3.8.5 Local address: /172.17.0.10:37452
Sep 12, 2017 12:42:23 PM com.hazelcast.client.connection.ClientConnectionManager
INFO: hz.client_0 [someGroup] [3.8.5] Authenticated with server [172.17.0.7]:5701, server version:3.8.5 Local address: /172.17.0.10:34884
Sep 12, 2017 12:42:23 PM com.hazelcast.client.connection.ClientConnectionManager
INFO: hz.client_0 [someGroup] [3.8.5] Authenticated with server [172.17.0.4]:5701, server version:3.8.5 Local address: /172.17.0.10:45126
2017-09-12 12:42:25,250 [main           ] INFO  SpringCamelContext             - Route: route1 started and consuming from: timer://foo?period=5000
2017-09-12 12:42:25,251 [main           ] INFO  SpringCamelContext             - Route: route2 started and consuming from: hazelcast-topic://foo
2017-09-12 12:42:25,258 [main           ] INFO  SpringCamelContext             - Total 2 routes, of which 2 are started
2017-09-12 12:42:25,260 [main           ] INFO  SpringCamelContext             - Apache Camel 2.20.0-SNAPSHOT (CamelContext: camel-1) started in 2.495 seconds
2017-09-12 12:42:25,264 [main           ] INFO  DefaultLifecycleProcessor      - Starting beans in phase 2147483646
2017-09-12 12:42:26,264 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-09-12 12:42:26,335 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2017-09-12 12:42:31,258 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-09-12 12:42:31,265 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2017-09-12 12:42:36,258 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-09-12 12:42:36,260 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2017-09-12 12:42:41,258 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-09-12 12:42:41,260 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2017-09-12 12:42:46,259 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-09-12 12:42:46,261 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2017-09-12 12:42:51,260 [2 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-09-12 12:42:51,261 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received

```
