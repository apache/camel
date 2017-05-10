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
hazelcast-3980717115-x730g                  1/1       Running            0          29s
```

Now you can decide to scale-up your Hazelcast cluster

```
kubectl scale deployment hazelcast --replicas 2
```

and again check the status of your pods

```
kubectl get pods
NAME                                           READY     STATUS    RESTARTS   AGE
hazelcast-4195412960-0tl3w                     1/1       Running   0          7s
hazelcast-4195412960-mgqtk                     1/1       Running   0          2m
```

You can also take a look at the logs from the pods:

```
kubectl logs hazelcast-3980717115-n1snd
2017-03-15 09:42:45.046  INFO 7 --- [           main] com.github.pires.hazelcast.Application   : Starting Application on hazelcast-4195412960-0tl3w with PID 7 (/bootstrapper.jar started by root in /)
2017-03-15 09:42:45.060  INFO 7 --- [           main] com.github.pires.hazelcast.Application   : No active profile set, falling back to default profiles: default
2017-03-15 09:42:45.128  INFO 7 --- [           main] s.c.a.AnnotationConfigApplicationContext : Refreshing org.springframework.context.annotation.AnnotationConfigApplicationContext@14514713: startup date [Wed Mar 15 09:42:45 GMT 2017]; root of context hierarchy
2017-03-15 09:42:45.989  INFO 7 --- [           main] o.s.j.e.a.AnnotationMBeanExporter        : Registering beans for JMX exposure on startup
2017-03-15 09:42:46.001  INFO 7 --- [           main] c.g.p.h.HazelcastDiscoveryController     : Asking k8s registry at https://kubernetes.default.svc.cluster.local..
2017-03-15 09:42:46.376  INFO 7 --- [           main] c.g.p.h.HazelcastDiscoveryController     : Found 2 pods running Hazelcast.
2017-03-15 09:42:46.458  INFO 7 --- [           main] c.h.instance.DefaultAddressPicker        : [LOCAL] [someGroup] [3.8] Interfaces is disabled, trying to pick one address from TCP-IP config addresses: [172.17.0.6, 172.17.0.2]
2017-03-15 09:42:46.458  INFO 7 --- [           main] c.h.instance.DefaultAddressPicker        : [LOCAL] [someGroup] [3.8] Prefer IPv4 stack is true.
2017-03-15 09:42:46.464  INFO 7 --- [           main] c.h.instance.DefaultAddressPicker        : [LOCAL] [someGroup] [3.8] Picked [172.17.0.6]:5701, using socket ServerSocket[addr=/0:0:0:0:0:0:0:0,localport=5701], bind any local is true
2017-03-15 09:42:46.484  INFO 7 --- [           main] com.hazelcast.system                     : [172.17.0.6]:5701 [someGroup] [3.8] Hazelcast 3.8 (20170217 - d7998b4) starting at [172.17.0.6]:5701
2017-03-15 09:42:46.484  INFO 7 --- [           main] com.hazelcast.system                     : [172.17.0.6]:5701 [someGroup] [3.8] Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
2017-03-15 09:42:46.485  INFO 7 --- [           main] com.hazelcast.system                     : [172.17.0.6]:5701 [someGroup] [3.8] Configured Hazelcast Serialization version : 1
2017-03-15 09:42:46.679  INFO 7 --- [           main] c.h.s.i.o.impl.BackpressureRegulator     : [172.17.0.6]:5701 [someGroup] [3.8] Backpressure is disabled
2017-03-15 09:42:47.069  INFO 7 --- [           main] com.hazelcast.instance.Node              : [172.17.0.6]:5701 [someGroup] [3.8] Creating TcpIpJoiner
2017-03-15 09:42:47.182  INFO 7 --- [           main] c.h.s.i.o.impl.OperationExecutorImpl     : [172.17.0.6]:5701 [someGroup] [3.8] Starting 2 partition threads
2017-03-15 09:42:47.189  INFO 7 --- [           main] c.h.s.i.o.impl.OperationExecutorImpl     : [172.17.0.6]:5701 [someGroup] [3.8] Starting 3 generic threads (1 dedicated for priority tasks)
2017-03-15 09:42:47.197  INFO 7 --- [           main] com.hazelcast.core.LifecycleService      : [172.17.0.6]:5701 [someGroup] [3.8] [172.17.0.6]:5701 is STARTING
2017-03-15 09:42:47.253  INFO 7 --- [cached.thread-3] c.hazelcast.nio.tcp.InitConnectionTask   : [172.17.0.6]:5701 [someGroup] [3.8] Connecting to /172.17.0.2:5701, timeout: 0, bind-any: true
2017-03-15 09:42:47.262  INFO 7 --- [cached.thread-3] c.h.nio.tcp.TcpIpConnectionManager       : [172.17.0.6]:5701 [someGroup] [3.8] Established socket connection between /172.17.0.6:58073 and /172.17.0.2:5701
2017-03-15 09:42:54.260  INFO 7 --- [ration.thread-0] com.hazelcast.system                     : [172.17.0.6]:5701 [someGroup] [3.8] Cluster version set to 3.8
2017-03-15 09:42:54.262  INFO 7 --- [ration.thread-0] c.h.internal.cluster.ClusterService      : [172.17.0.6]:5701 [someGroup] [3.8] 

Members [2] {
	Member [172.17.0.2]:5701 - 170f6924-7888-442a-9875-ad4d25659a8a
	Member [172.17.0.6]:5701 - b1b82bfa-86c2-4931-af57-325c10c03b3b this
}

2017-03-15 09:42:56.285  INFO 7 --- [           main] com.hazelcast.core.LifecycleService      : [172.17.0.6]:5701 [someGroup] [3.8] [172.17.0.6]:5701 is STARTED
2017-03-15 09:42:56.287  INFO 7 --- [           main] com.github.pires.hazelcast.Application   : Started Application in 11.831 seconds (JVM running for 12.219)
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
INFO: hz.client_0 [someGroup] [3.8] HazelcastClient 3.8 (20170217 - d7998b4) is CLIENT_CONNECTED
2017-03-10 10:05:23,851 [main           ] INFO  SpringCamelContext             - Apache Camel 2.19.0-SNAPSHOT (CamelContext: camel-1) is starting
2017-03-10 10:05:23,852 [main           ] INFO  ManagedManagementStrategy      - JMX is enabled
2017-03-10 10:05:23,975 [main           ] INFO  DefaultTypeConverter           - Loaded 192 type converters
2017-03-10 10:05:23,995 [main           ] INFO  DefaultRuntimeEndpointRegistry - Runtime endpoint registry is in extended mode gathering usage statistics of all incoming and outgoing endpoints (cache limit: 1000)
2017-03-10 10:05:24,061 [main           ] INFO  SpringCamelContext             - StreamCaching is not in use. If using streams then its recommended to enable stream caching. See more details at http://camel.apache.org/stream-caching.html
2017-03-10 10:05:24,114 [main           ] INFO  SpringCamelContext             - Route: route1 started and consuming from: timer://foo?period=5000
2017-03-10 10:05:24,115 [main           ] INFO  SpringCamelContext             - Route: route2 started and consuming from: hazelcast-topic://foo
2017-03-10 10:05:24,116 [main           ] INFO  SpringCamelContext             - Total 2 routes, of which 2 are started.
2017-03-10 10:05:24,119 [main           ] INFO  SpringCamelContext             - Apache Camel 2.19.0-SNAPSHOT (CamelContext: camel-1) started in 0.265 seconds
Mar 10, 2017 10:05:24 AM com.hazelcast.client.connection.ClientConnectionManager
INFO: hz.client_0 [someGroup] [3.8] Authenticated with server [172.17.0.4]:5701, server version:3.8 Local address: /172.17.0.7:46948
2017-03-10 10:05:25,125 [0 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-03-10 10:05:25,139 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2017-03-10 10:05:30,120 [0 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-03-10 10:05:30,127 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2017-03-10 10:05:35,116 [0 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-03-10 10:05:35,118 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2017-03-10 10:05:40,117 [0 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-03-10 10:05:40,118 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2017-03-10 10:05:45,117 [0 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-03-10 10:05:45,119 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
2017-03-10 10:05:50,117 [0 - timer://foo] INFO  route1                         - Producer side: Sending data to Hazelcast topic..
2017-03-10 10:05:50,118 [lient_0.event-3] INFO  route2                         - Consumer side: Detected following action: received
```
