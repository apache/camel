# Camel Cassandraql route on Kubernetes cluster

This quickstart run in a Java standalone container, using Spring with Apache Camel (Cassandraql component).

This example is based on:

- Minikube 0.21.0 (Kubernetes version >= 1.7) 
- Fabric8 Maven Plugin (version >= 3.5)

First thing you'll need to do is preparing the environment.

Don't forget to use a bit more memory for your Minikube for running this example:

```
$ minikube start --memory 5120 --cpus=4
```

Once your Minikube node is up and running you'll need to run the following command.
In your src/main/resource/fabric8/ folder you'll find two yaml file. Run the following command using them:

```
$ kubectl create -f src/main/resources/fabric8/cassandra-service.yaml
$ kubectl create -f src/main/resources/fabric8/cassandra-statefulset.yaml
```

To check the correct startup of the cluster run the following command:

```
$ kubectl get statefulsets
NAME        DESIRED   CURRENT   AGE
cassandra   2         2         2h
```

and check the status of the pods

```
$ kubectl get pods
NAME                                       READY     STATUS    RESTARTS   AGE
cassandra-0                                1/1       Running   0          2h
cassandra-1                                1/1       Running   0          2h
```

You can also verify the health of your cluster by running

```
$ kubectl exec <pod_name> -it nodetool status
Datacenter: DC1-K8Demo
======================
Status=Up/Down
|/ State=Normal/Leaving/Joining/Moving
--  Address     Load       Tokens       Owns (effective)  Host ID                               Rack
UN  172.17.0.4  212.14 KiB  32           53.1%             9bf81ccd-4aa1-451b-b56e-c16c5ee04836  Rack1-K8Demo
UN  172.17.0.6  170.08 KiB  32           46.9%             69cc6f60-9ccf-439d-a298-b79b643c1586  Rack1-K8Demo
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
2017-08-06 10:43:52,209 [main           ] INFO  ClassPathXmlApplicationContext - Refreshing org.springframework.context.support.ClassPathXmlApplicationContext@1068e947: startup date [Sun Aug 06 10:43:52 UTC 2017]; root of context hierarchy
2017-08-06 10:43:52,244 [main           ] INFO  XmlBeanDefinitionReader        - Loading XML bean definitions from class path resource [META-INF/spring/camel-context.xml]
2017-08-06 10:43:53,425 [main           ] INFO  GuavaCompatibility             - Detected Guava >= 19 in the classpath, using modern compatibility layer
2017-08-06 10:43:53,564 [main           ] INFO  ClockFactory                   - Using native clock to generate timestamps.
2017-08-06 10:43:53,639 [main           ] INFO  NettyUtil                      - Did not find Netty's native epoll transport in the classpath, defaulting to NIO.
2017-08-06 10:43:54,054 [main           ] INFO  DCAwareRoundRobinPolicy        - Using data-center name 'DC1-K8Demo' for DCAwareRoundRobinPolicy (if this is incorrect, please provide the correct datacenter name with DCAwareRoundRobinPolicy constructor)
2017-08-06 10:43:54,056 [main           ] INFO  Cluster                        - New Cassandra host cassandra/172.17.0.2:9042 added
2017-08-06 10:43:54,056 [main           ] INFO  Cluster                        - New Cassandra host cassandra/172.17.0.4:9042 added
2017-08-06 10:43:56,845 [main           ] INFO  SpringCamelContext             - Apache Camel 2.20.0-SNAPSHOT (CamelContext: camel-1) is starting
2017-08-06 10:43:56,846 [main           ] INFO  ManagedManagementStrategy      - JMX is enabled
2017-08-06 10:43:57,105 [main           ] INFO  DefaultTypeConverter           - Type converters loaded (core: 192, classpath: 1)
2017-08-06 10:43:57,225 [main           ] INFO  SpringCamelContext             - StreamCaching is not in use. If using streams then its recommended to enable stream caching. See more details at http://camel.apache.org/stream-caching.html
2017-08-06 10:43:57,230 [main           ] INFO  ClockFactory                   - Using native clock to generate timestamps.
2017-08-06 10:43:57,918 [main           ] INFO  DCAwareRoundRobinPolicy        - Using data-center name 'DC1-K8Demo' for DCAwareRoundRobinPolicy (if this is incorrect, please provide the correct datacenter name with DCAwareRoundRobinPolicy constructor)
2017-08-06 10:43:57,920 [main           ] INFO  Cluster                        - New Cassandra host cassandra/172.17.0.2:9042 added
2017-08-06 10:43:57,920 [main           ] INFO  Cluster                        - New Cassandra host cassandra/172.17.0.4:9042 added
2017-08-06 10:43:58,488 [main           ] INFO  SpringCamelContext             - Route: cassandra-route started and consuming from: timer://foo?period=5000
2017-08-06 10:43:58,489 [main           ] INFO  SpringCamelContext             - Total 1 routes, of which 1 are started.
2017-08-06 10:43:58,489 [main           ] INFO  SpringCamelContext             - Apache Camel 2.20.0-SNAPSHOT (CamelContext: camel-1) started in 1.645 seconds
2017-08-06 10:43:58,492 [main           ] INFO  DefaultLifecycleProcessor      - Starting beans in phase 2147483646
2017-08-06 10:43:59,586 [2 - timer://foo] INFO  cassandra-route                - Query result set [Row[1, oscerd]]
2017-08-06 10:44:04,575 [2 - timer://foo] INFO  cassandra-route                - Query result set [Row[1, oscerd]]
2017-08-06 10:44:09,577 [2 - timer://foo] INFO  cassandra-route                - Query result set [Row[1, oscerd]]
```

### Cleanup

Run following to undeploy the application and cassandra nodes
```
$ mvn -Pkubernetes-install fabric8:undeploy
$ kubectl create -f src/main/resources/fabric8/cassandra-service.yaml
$ kubectl create -f src/main/resources/fabric8/cassandra-statefulset.yaml
```

Make sure no pod is running
```
$ kubectl get pods
No resources found.
```
