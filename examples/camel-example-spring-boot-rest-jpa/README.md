# Spring Boot REST DSL / JPA Example

### Introduction

This example demonstrates how to use JPA and Spring Data along with Camel's REST DSL
to expose a RESTful API that performs CRUD operations on a database.

It generates orders for books referenced in database at a regular pace.
Orders are processed asynchronously by another Camel route. Books available
in database as well as the status of the generated orders can be retrieved via
the REST API.

It relies on Swagger to expose the API documentation of the REST service.

### Build

You can build this example using:

```sh
$ mvn package
```

### Run

You can run this example with Maven using:

```sh
$ mvn spring-boot:run
```

Alternatively, you can also run this example using the executable JAR:

```sh
$ java -jar -Dspring.profiles.active=dev target/camel-example-spring-boot-rest-jpa-${project.version}.jar
```

This uses an embedded in-memory HSQLDB database. You can use the default
Spring Boot profile in case you have a MySQL server available for you to test.

When the Camel application runs, you should see the following messages
being logged to the console, e.g.:

```
2016-09-02 09:54:29.702  INFO 27253 --- [mer://new-order] generate-order : Inserted new order 1
2016-09-02 09:54:31.597  INFO 27253 --- [mer://new-order] generate-order : Inserted new order 2
2016-09-02 09:54:33.596  INFO 27253 --- [mer://new-order] generate-order : Inserted new order 3
2016-09-02 09:54:34.637  INFO 27253 --- [rts.camel.Order] process-order  : Processed order #id 1 with 7 copies of the «Camel in Action» book
2016-09-02 09:54:34.641  INFO 27253 --- [rts.camel.Order] process-order  : Processed order #id 2 with 4 copies of the «Camel in Action» book
2016-09-02 09:54:34.645  INFO 27253 --- [rts.camel.Order] process-order  : Processed order #id 3 with 1 copies of the «ActiveMQ in Action» book
2016-09-02 09:54:35.597  INFO 27253 --- [mer://new-order] generate-order : Inserted new order 4
2016-09-02 09:54:37.601  INFO 27253 --- [mer://new-order] generate-order : Inserted new order 5
2016-09-02 09:54:39.605  INFO 27253 --- [mer://new-order] generate-order : Inserted new order 6
2016-09-02 09:54:39.668  INFO 27253 --- [rts.camel.Order] process-order  : Processed order #id 4 with 7 copies of the «Camel in Action» book
2016-09-02 09:54:39.671  INFO 27253 --- [rts.camel.Order] process-order  : Processed order #id 5 with 1 copies of the «ActiveMQ in Action» book
2016-09-02 09:54:39.674  INFO 27253 --- [rts.camel.Order] process-order  : Processed order #id 6 with 4 copies of the «Camel in Action» book
```

You can then access the REST API directly from your Web browser, e.g.:

- <http://localhost:8080/camel-rest-jpa/books>
- <http://localhost:8080/camel-rest-jpa/books/order/1>

The Camel application can be stopped pressing <kbd>ctrl</kbd>+<kbd>c</kbd> in the shell.

### Swagger API

The example provides API documentation of the service using Swagger using
the _context-path_ `camel-rest-jpa/api-doc`. You can access the API documentation
from your Web browser at <http://localhost:8080/camel-rest-jpa/api-doc>.

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may have. Enjoy!

The Camel riders!