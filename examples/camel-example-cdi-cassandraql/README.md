# Cassandraql Example - CDI

### Introduction

This example illustrates the integration between Camel, CDI and Cassandra.

The example get the list of pods from a Kubernetes cluster and print name and status of each one of the pods returned.

The `camel-cdi`, `camel-core` and `camel-cassandraql` components are used in this example.
The example assumes you have a running Cassandra Cluster in your environment. We will use Docker to spin up this cluster.
As first step we will need to run a single node cluster:

```
$ docker run --name master_node -dt oscerd/cassandra
$ docker run --name node1 -d -e SEED="$(docker inspect --format='{{ .NetworkSettings.IPAddress }}' master_node)" oscerd/cassandra
$ docker run --name node2 -d -e SEED="$(docker inspect --format='{{ .NetworkSettings.IPAddress }}' master_node)" oscerd/cassandra
```

We now have three nodes in our cluster.

```
$ docker exec -ti master_node /opt/cassandra/bin/nodetool status
Datacenter: datacenter1
=======================
Status=Up/Down
|/ State=Normal/Leaving/Joining/Moving
--  Address     Load       Tokens       Owns (effective)  Host ID                               Rack
UN  172.17.0.3  102.67 KiB  256          65.9%             1a985c48-33a1-44aa-b7e9-f1a3620a6482  rack1
UN  172.17.0.2  107.64 KiB  256          68.2%             da54ce5e-6433-4ea0-b2c3-fbc6c63ea955  rack1
UN  172.17.0.4  15.42 KiB  256          65.8%             0f2ba25a-37b0-4f27-a10a-d9a44655396a  rack1
```

From your local [Apache Cassandra](http://cassandra.apache.org/) directory run the `cqlsh` command:

```
<LOCAL_CASSANDRA_HOME>/bin/cqlsh $(docker inspect --format='{{ .NetworkSettings.IPAddress }}' master_node)
```

You should see the Cqlsh prompt

```
Connected to Test Cluster at 172.17.0.2:9042.
[cqlsh 5.0.1 | Cassandra 3.6 | CQL spec 3.4.2 | Native protocol v4]
Use HELP for help.
cqlsh>
```

Let's create a namespace `test` with a table `users`

```
create keyspace test with replication = {'class':'SimpleStrategy', 'replication_factor':3};
use test;
create table users ( id int primary key, name text );
insert into users (id,name) values (1, 'oscerd');
quit;
```

run a simple query to check everything works:

```
cqlsh> use test;
cqlsh:test> select * from users;

 id | name
----+--------
  1 | oscerd

(1 rows)
cqlsh:test> 
```

Remember to edit the apache-deltaspike.properties file to add the correct addresses of the different nodes running in Docker.

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

When the Camel application runs, you should see the following result:
```
2016-07-24 15:33:50,812 [cdi.Main.main()] INFO  Version                        - WELD-000900: 2.3.5 (Final)
Jul 24, 2016 3:33:50 PM org.apache.deltaspike.core.impl.config.EnvironmentPropertyConfigSourceProvider <init>
INFO: Custom config found by DeltaSpike. Name: 'META-INF/apache-deltaspike.properties', URL: 'file:/home/oscerd/workspace/apache-camel/camel/examples/camel-example-cdi-cassandraql/target/classes/META-INF/apache-deltaspike.properties'
Jul 24, 2016 3:33:50 PM org.apache.deltaspike.core.util.ProjectStageProducer initProjectStage
INFO: Computed the following DeltaSpike ProjectStage: Production
2016-07-24 15:33:51,064 [cdi.Main.main()] INFO  Bootstrap                      - WELD-000101: Transactional services not available. Injection of @Inject UserTransaction not available. Transactional observers will be invoked synchronously.
2016-07-24 15:33:51,170 [cdi.Main.main()] INFO  Event                          - WELD-000411: Observer method [BackedAnnotatedMethod] protected org.apache.deltaspike.core.impl.message.MessageBundleExtension.detectInterfaces(@Observes ProcessAnnotatedType) receives events for all annotated types. Consider restricting events using @WithAnnotations or a generic type with bounds.
2016-07-24 15:33:51,174 [cdi.Main.main()] INFO  Event                          - WELD-000411: Observer method [BackedAnnotatedMethod] protected org.apache.deltaspike.core.impl.interceptor.GlobalInterceptorExtension.promoteInterceptors(@Observes ProcessAnnotatedType, BeanManager) receives events for all annotated types. Consider restricting events using @WithAnnotations or a generic type with bounds.
2016-07-24 15:33:51,189 [cdi.Main.main()] INFO  Event                          - WELD-000411: Observer method [BackedAnnotatedMethod] private org.apache.camel.cdi.CdiCamelExtension.processAnnotatedType(@Observes ProcessAnnotatedType<?>) receives events for all annotated types. Consider restricting events using @WithAnnotations or a generic type with bounds.
2016-07-24 15:33:51,195 [cdi.Main.main()] INFO  Event                          - WELD-000411: Observer method [BackedAnnotatedMethod] protected org.apache.deltaspike.core.impl.exclude.extension.ExcludeExtension.vetoBeans(@Observes ProcessAnnotatedType, BeanManager) receives events for all annotated types. Consider restricting events using @WithAnnotations or a generic type with bounds.
2016-07-24 15:33:51,491 [cdi.Main.main()] WARN  Validator                      - WELD-001478: Interceptor class org.apache.deltaspike.core.impl.throttling.ThrottledInterceptor is enabled for the application and for the bean archive /home/oscerd/.m2/repository/org/apache/deltaspike/core/deltaspike-core-impl/1.7.1/deltaspike-core-impl-1.7.1.jar. It will only be invoked in the @Priority part of the chain.
2016-07-24 15:33:51,491 [cdi.Main.main()] WARN  Validator                      - WELD-001478: Interceptor class org.apache.deltaspike.core.impl.lock.LockedInterceptor is enabled for the application and for the bean archive /home/oscerd/.m2/repository/org/apache/deltaspike/core/deltaspike-core-impl/1.7.1/deltaspike-core-impl-1.7.1.jar. It will only be invoked in the @Priority part of the chain.
2016-07-24 15:33:51,491 [cdi.Main.main()] WARN  Validator                      - WELD-001478: Interceptor class org.apache.deltaspike.core.impl.future.FutureableInterceptor is enabled for the application and for the bean archive /home/oscerd/.m2/repository/org/apache/deltaspike/core/deltaspike-core-impl/1.7.1/deltaspike-core-impl-1.7.1.jar. It will only be invoked in the @Priority part of the chain.
2016-07-24 15:33:52,244 [cdi.Main.main()] INFO  CdiCamelExtension              - Camel CDI is starting Camel context [camel-example-cassandraql-cdi]
2016-07-24 15:33:52,245 [cdi.Main.main()] INFO  DefaultCamelContext            - Apache Camel 2.18.0 (CamelContext: camel-example-cassandraql-cdi) is starting
2016-07-24 15:33:52,246 [cdi.Main.main()] INFO  ManagedManagementStrategy      - JMX is enabled
2016-07-24 15:33:52,352 [cdi.Main.main()] INFO  DefaultTypeConverter           - Loaded 189 type converters
2016-07-24 15:33:52,367 [cdi.Main.main()] INFO  DefaultRuntimeEndpointRegistry - Runtime endpoint registry is in extended mode gathering usage statistics of all incoming and outgoing endpoints (cache limit: 1000)
2016-07-24 15:33:52,465 [cdi.Main.main()] INFO  DefaultCamelContext            - StreamCaching is not in use. If using streams then its recommended to enable stream caching. See more details at http://camel.apache.org/stream-caching.html
2016-07-24 15:33:52,547 [cdi.Main.main()] INFO  NettyUtil                      - Did not find Netty's native epoll transport in the classpath, defaulting to NIO.
2016-07-24 15:33:52,789 [cdi.Main.main()] INFO  DCAwareRoundRobinPolicy        - Using data-center name 'datacenter1' for DCAwareRoundRobinPolicy (if this is incorrect, please provide the correct datacenter name with DCAwareRoundRobinPolicy constructor)
2016-07-24 15:33:52,790 [cdi.Main.main()] INFO  Cluster                        - New Cassandra host /172.17.0.3:9042 added
2016-07-24 15:33:52,791 [cdi.Main.main()] INFO  Cluster                        - New Cassandra host /172.17.0.2:9042 added
2016-07-24 15:33:52,791 [cdi.Main.main()] INFO  Cluster                        - New Cassandra host /172.17.0.4:9042 added
2016-07-24 15:33:52,914 [cdi.Main.main()] INFO  DCAwareRoundRobinPolicy        - Using data-center name 'datacenter1' for DCAwareRoundRobinPolicy (if this is incorrect, please provide the correct datacenter name with DCAwareRoundRobinPolicy constructor)
2016-07-24 15:33:52,914 [cdi.Main.main()] INFO  Cluster                        - New Cassandra host /172.17.0.3:9042 added
2016-07-24 15:33:52,914 [cdi.Main.main()] INFO  Cluster                        - New Cassandra host /172.17.0.2:9042 added
2016-07-24 15:33:52,914 [cdi.Main.main()] INFO  Cluster                        - New Cassandra host /172.17.0.4:9042 added
2016-07-24 15:33:52,985 [cdi.Main.main()] INFO  DefaultCamelContext            - Route: route1 started and consuming from: timer://stream?repeatCount=1
2016-07-24 15:33:52,986 [cdi.Main.main()] INFO  DefaultCamelContext            - Total 1 routes, of which 1 are started.
2016-07-24 15:33:52,987 [cdi.Main.main()] INFO  DefaultCamelContext            - Apache Camel 2.18.0 (CamelContext: camel-example-cassandraql-cdi) started in 0.742 seconds
2016-07-24 15:33:53,018 [cdi.Main.main()] INFO  Bootstrap                      - WELD-ENV-002003: Weld SE container STATIC_INSTANCE initialized
2016-07-24 15:33:54,041 [ timer://stream] INFO  route1                         - Result from query [Row[1, oscerd]]

```

The Camel application can be stopped pressing <kbd>ctrl</kbd>+<kbd>c</kbd> in the shell.

If you run the query again you should see a `davsclaus` entry too:

```
cqlsh> use test;
cqlsh:test> select * from users;

 id | name
----+-----------
  1 |    oscerd
  2 | davsclaus

(2 rows)
cqlsh:test> 
```

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may have. Enjoy!

The Camel riders!
