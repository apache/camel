# Camel Hazelcast route on Kubernetes cluster

This quickstart run in a Java standalone container, using Spring with Apache Camel (Hazelcast component).

This quickstart is based on the Kubernetes example here: https://github.com/kubernetes/kubernetes/tree/master/examples/storage/hazelcast

This example is based on:

- Minikube (Kubernetes version >= 1.5)
- Fabric8 Maven Plugin (version >= 3.2)

First thing you'll need to do is preparing the environment.

Once your Minikube node is up and running you'll need to run the following command.
In your src/main/resource/fabric8/ folder you'll find two yaml file. Run the following command using them:

```
kubectl create -f src/main/resource/fabric8/hazelcast-service.yaml
kubectl create -f src/main/resource/fabric8/hazelcast-deployment.yaml
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
NAME                                        READY     STATUS             RESTARTS   AGE
hazelcast-3980717115-n1snd                  1/1       Running            0          9s
hazelcast-3980717115-x730g                  1/1       Running            0          3m
```

You can also take a look at the logs from the pods:

```
kubectl logs hazelcast-3980717115-n1snd
2017-03-10 09:57:20.204  INFO 7 --- [           main] com.github.pires.hazelcast.Application   : Starting Application on hazelcast-3980717115-n1snd with PID 7 (/bootstrapper.jar started by root in /)
2017-03-10 09:57:20.211  INFO 7 --- [           main] com.github.pires.hazelcast.Application   : No active profile set, falling back to default profiles: default
2017-03-10 09:57:20.283  INFO 7 --- [           main] s.c.a.AnnotationConfigApplicationContext : Refreshing org.springframework.context.annotation.AnnotationConfigApplicationContext@14514713: startup date [Fri Mar 10 09:57:20 GMT 2017]; root of context hierarchy
2017-03-10 09:57:21.005  INFO 7 --- [           main] o.s.j.e.a.AnnotationMBeanExporter        : Registering beans for JMX exposure on startup
2017-03-10 09:57:21.013  INFO 7 --- [           main] c.g.p.h.HazelcastDiscoveryController     : Asking k8s registry at https://kubernetes.default.svc.cluster.local..
2017-03-10 09:57:21.428  INFO 7 --- [           main] c.g.p.h.HazelcastDiscoveryController     : Found 2 pods running Hazelcast.
2017-03-10 09:57:21.476  INFO 7 --- [           main] c.h.instance.DefaultAddressPicker        : [LOCAL] [someGroup] [3.7.5] Interfaces is disabled, trying to pick one address from TCP-IP config addresses: [172.17.0.4, 172.17.0.5]
2017-03-10 09:57:21.476  INFO 7 --- [           main] c.h.instance.DefaultAddressPicker        : [LOCAL] [someGroup] [3.7.5] Prefer IPv4 stack is true.
2017-03-10 09:57:21.481  INFO 7 --- [           main] c.h.instance.DefaultAddressPicker        : [LOCAL] [someGroup] [3.7.5] Picked [172.17.0.5]:5701, using socket ServerSocket[addr=/0:0:0:0:0:0:0:0,localport=5701], bind any local is true
2017-03-10 09:57:21.497  INFO 7 --- [           main] com.hazelcast.system                     : [172.17.0.5]:5701 [someGroup] [3.7.5] Hazelcast 3.7.5 (20170124 - 111f332) starting at [172.17.0.5]:5701
2017-03-10 09:57:21.497  INFO 7 --- [           main] com.hazelcast.system                     : [172.17.0.5]:5701 [someGroup] [3.7.5] Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
2017-03-10 09:57:21.497  INFO 7 --- [           main] com.hazelcast.system                     : [172.17.0.5]:5701 [someGroup] [3.7.5] Configured Hazelcast Serialization version : 1
2017-03-10 09:57:21.623  INFO 7 --- [           main] c.h.s.i.o.impl.BackpressureRegulator     : [172.17.0.5]:5701 [someGroup] [3.7.5] Backpressure is disabled
2017-03-10 09:57:21.977  INFO 7 --- [           main] com.hazelcast.instance.Node              : [172.17.0.5]:5701 [someGroup] [3.7.5] Creating TcpIpJoiner
2017-03-10 09:57:22.106  INFO 7 --- [           main] c.h.s.i.o.impl.OperationExecutorImpl     : [172.17.0.5]:5701 [someGroup] [3.7.5] Starting 2 partition threads
2017-03-10 09:57:22.108  INFO 7 --- [           main] c.h.s.i.o.impl.OperationExecutorImpl     : [172.17.0.5]:5701 [someGroup] [3.7.5] Starting 3 generic threads (1 dedicated for priority tasks)
2017-03-10 09:57:22.119  INFO 7 --- [           main] com.hazelcast.core.LifecycleService      : [172.17.0.5]:5701 [someGroup] [3.7.5] [172.17.0.5]:5701 is STARTING
2017-03-10 09:57:22.120  INFO 7 --- [           main] c.h.n.t.n.NonBlockingIOThreadingModel    : [172.17.0.5]:5701 [someGroup] [3.7.5] TcpIpConnectionManager configured with Non Blocking IO-threading model: 3 input threads and 3 output threads
2017-03-10 09:57:22.142  INFO 7 --- [cached.thread-3] c.hazelcast.nio.tcp.InitConnectionTask   : [172.17.0.5]:5701 [someGroup] [3.7.5] Connecting to /172.17.0.4:5701, timeout: 0, bind-any: true
2017-03-10 09:57:22.151  INFO 7 --- [cached.thread-3] c.h.nio.tcp.TcpIpConnectionManager       : [172.17.0.5]:5701 [someGroup] [3.7.5] Established socket connection between /172.17.0.5:45191 and /172.17.0.4:5701
2017-03-10 09:57:29.164  INFO 7 --- [ration.thread-0] c.h.internal.cluster.ClusterService      : [172.17.0.5]:5701 [someGroup] [3.7.5] 

Members [2] {
	Member [172.17.0.4]:5701 - efef1987-16aa-4bdf-b178-274047d08ae0
	Member [172.17.0.5]:5701 - ed6192b3-ef76-46df-b642-68d34aea4461 this
}

2017-03-10 09:57:31.173  INFO 7 --- [           main] com.hazelcast.core.LifecycleService      : [172.17.0.5]:5701 [someGroup] [3.7.5] [172.17.0.5]:5701 is STARTED
2017-03-10 09:57:31.180  INFO 7 --- [           main] com.github.pires.hazelcast.Application   : Started Application in 11.411 seconds (JVM running for 11.908)
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
2017-03-10 10:05:24,115 [main           ] INFO  SpringCamelContext             - Route: route2 started and consuming from: hazelcast://topic:foo
2017-03-10 10:05:24,116 [main           ] INFO  SpringCamelContext             - Total 2 routes, of which 2 are started.
2017-03-10 10:05:24,119 [main           ] INFO  SpringCamelContext             - Apache Camel 2.19.0-SNAPSHOT (CamelContext: camel-1) started in 0.265 seconds
Mar 10, 2017 10:05:24 AM com.hazelcast.client.connection.ClientConnectionManager
INFO: hz.client_0 [someGroup] [3.8] Authenticated with server [172.17.0.4]:5701, server version:3.7.5 Local address: /172.17.0.7:46948
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
