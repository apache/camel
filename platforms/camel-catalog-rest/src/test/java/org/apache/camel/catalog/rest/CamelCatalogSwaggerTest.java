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

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.jaxrs.swagger.Swagger2Feature;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static io.restassured.RestAssured.given;

public class CamelCatalogSwaggerTest {

    private Server server;
    private CamelCatalogRest catalog;
    private int port;

    @Before
    public void setup() {
        catalog = new CamelCatalogRest();

        port = AvailablePortFinder.getNextAvailable(9000);

        // setup Apache CXF REST server
        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
        sf.setResourceClasses(CamelCatalogRest.class);
        sf.setResourceProvider(CamelCatalogRest.class, new SingletonResourceProvider(catalog));

        Swagger2Feature swagger = new Swagger2Feature();
        swagger.setBasePath("/");
        swagger.setScanAllResources(false);
        swagger.setPrettyPrint(true);
        swagger.setSupportSwaggerUi(true);
        swagger.setTitle("Camel Catalog REST Api");
        swagger.setDescription("REST Api for the Camel Catalog");
        swagger.setVersion(catalog.getCatalogVersion());
        swagger.setContact("Apache Camel");
        sf.getFeatures().add(swagger);

        // to use jackson for json
        sf.setProvider(JacksonJsonProvider.class);
        sf.setAddress("http://localhost:" + port);

        // create and start the CXF server (non blocking)
        server = sf.create();
        server.start();
    }

    @After
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testSwagger() throws Exception {
        given().
            baseUri("http://localhost:" + port).
            when().
                get("/swagger.json").
            then().
                body("paths./camel-catalog/catalogVersion.get.summary", Matchers.is("The version of this Camel Catalog"));

        // System.out.println("Swagger UI: http://localhost:9000/api-docs?url=/swagger.json");
    }
}
