Camel Catalog REST Application
==============================

This is a little standalone application which hosts the Camel Catalog and Connector Catalog REST API.

You can run the application using

    mvn compile exec:java

And then from a web browser you can access the REST API from the embedded Swagger UI

    http://localhost:8080/api-docs?url=/swagger.json
