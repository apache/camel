/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.catalog.rest;

import java.io.Console;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.jaxrs.swagger.Swagger2Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main to start the Camel Catalog and Connector Catalog REST Api application
 * which runs standalone using an embedded CXF/Jetty server with embedded Swagger Doc and Swagger UI.
 */
public class CamelCatalogRestMain {

    private static final Logger LOGGER = LoggerFactory.getLogger(CamelCatalogRestMain.class);

    private Server server;
    private CamelCatalogRest catalog;
    private CamelConnectorCatalogRest connectorCatalog;
    private int port = 8080;

    public static void main(String[] args) {
        CamelCatalogRestMain me = new CamelCatalogRestMain();
        me.run();
    }

    public void run() {
        LOGGER.info("Starting ...");

        catalog = new CamelCatalogRest();
        connectorCatalog = new CamelConnectorCatalogRest();

        // setup Apache CXF REST server
        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
        sf.setResourceClasses(CamelCatalogRest.class, CamelConnectorCatalogRest.class);
        sf.setResourceProvider(CamelCatalogRest.class, new SingletonResourceProvider(catalog));
        sf.setResourceProvider(CamelConnectorCatalogRest.class, new SingletonResourceProvider(connectorCatalog));

        Swagger2Feature swagger = new Swagger2Feature();
        swagger.setBasePath("/");
        swagger.setScanAllResources(false);
        swagger.setPrettyPrint(true);
        swagger.setSupportSwaggerUi(true);
        swagger.setTitle("Camel Catalog and Connector Catalog REST Api");
        swagger.setDescription("REST Api for the Camel Catalog and Connector Catalog");
        swagger.setVersion(catalog.getCatalogVersion());
        swagger.setContact("Apache Camel");
        sf.getFeatures().add(swagger);

        // to use jackson for json
        sf.setProvider(JacksonJsonProvider.class);
        sf.setAddress("http://localhost:" + port);

        // create and start the CXF server (non blocking)
        server = sf.create();
        server.start();

        LOGGER.info("CamelCatalog REST Api started");
        LOGGER.info("");
        LOGGER.info("\tRest API base path: http://localhost:{}/camel-catalog", port);
        LOGGER.info("\tRest API version: http://localhost:{}/camel-catalog/catalogVersion", port);
        LOGGER.info("");
        LOGGER.info("CamelConnectorCatalog REST Api started");
        LOGGER.info("");
        LOGGER.info("\tRest API base path: http://localhost:{}/camel-connector-catalog", port);
        LOGGER.info("");
        LOGGER.info("\tSwagger Doc: http://localhost:{}/swagger.json", port);
        LOGGER.info("\tSwagger UI: http://localhost:{}/api-docs?url=/swagger.json", port);
        LOGGER.info("");

        LOGGER.info("Press Enter to stop");
        Console console = System.console();
        console.readLine();

        LOGGER.info("Stopping ...");

        server.stop();
        server.destroy();
        LOGGER.info("CamelCatalog REST Api stopped");
        System.exit(0);
    }

}
