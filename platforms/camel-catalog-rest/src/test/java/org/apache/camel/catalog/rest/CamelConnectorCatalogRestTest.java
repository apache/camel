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
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static io.restassured.RestAssured.given;

public class CamelConnectorCatalogRestTest {

    private Server server;
    private CamelConnectorCatalogRest catalog;
    private int port;

    @Before
    public void setup() {
        catalog = new CamelConnectorCatalogRest();

        port = AvailablePortFinder.getNextAvailable(9000);

        // setup Apache CXF REST server
        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
        sf.setResourceClasses(CamelConnectorCatalogRest.class);
        sf.setResourceProvider(CamelConnectorCatalogRest.class, new SingletonResourceProvider(catalog));
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
    public void testEmptyFindConnectors() throws Exception {
        given().
            baseUri("http://localhost:" + port).
            accept("application/json").
            when().
                get("/camel-connector-catalog/findConnector?latestVersionOnly=false").
            then().
                body(Matchers.hasToString("[]"));
    }

    @Test
    public void testFindConnectors() throws Exception {
        catalog.getCatalog().addConnector("org.apache.camel", "myfoo-connector", "2.19.0", "MyFoo", "my-foo", "org.myfoo.connector.MyFooComponent",
            "Something cool", "foo,timer", null, null, null);

        given().
            baseUri("http://localhost:" + port).
            accept("application/json").
            when().
                get("/camel-connector-catalog/findConnector?latestVersionOnly=false").
            then().
                body(Matchers.containsString("MyFoo"));

    }

}
