/*
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
package org.apache.camel.component.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A mongodb endpoint closes the client it created from connectionUriString or hosts when it stops, and never closes a
 * client it did not create.
 */
class MongoDbEndpointClientLifecycleTest extends CamelTestSupport {

    private static final String URI_ENDPOINT
            = "mongodb:dummy?connectionUriString=RAW(mongodb://localhost:27017)&database=test&collection=test&operation=findAll";
    private static final String HOSTS_ENDPOINT
            = "mongodb:dummy?hosts=localhost:27017&database=test&collection=test&operation=findAll";

    @Test
    void clientCreatedFromConnectionUriStringIsClosedWhenTheRouteIsRemoved() throws Exception {
        assertClientClosedWhenTheRouteIsRemoved(URI_ENDPOINT);
    }

    @Test
    void clientCreatedFromHostsIsClosedWhenTheRouteIsRemoved() throws Exception {
        assertClientClosedWhenTheRouteIsRemoved(HOSTS_ENDPOINT);
    }

    @Test
    void routeAddedAgainWithTheSameIdClosesTheClientOfTheReplacedRoute() throws Exception {
        addRoute("mongo", URI_ENDPOINT);
        MongoDbEndpoint first = context.getEndpoint(URI_ENDPOINT, MongoDbEndpoint.class);
        MongoClient firstClient = first.getMongoConnection();

        // as a route reload does: the route with the same id is stopped and removed, and the new one is added
        addRoute("mongo", URI_ENDPOINT);

        assertClosed(firstClient);
        MongoClient secondClient = context.getEndpoint(URI_ENDPOINT, MongoDbEndpoint.class).getMongoConnection();
        assertNotNull(secondClient);
        assertNotSame(firstClient, secondClient);
    }

    @Test
    void clientIsClosedWhenTheContextStops() throws Exception {
        addRoute("mongo", URI_ENDPOINT);
        MongoClient client = context.getEndpoint(URI_ENDPOINT, MongoDbEndpoint.class).getMongoConnection();

        context.stop();

        assertClosed(client);
    }

    @Test
    void endpointStartedAgainCreatesANewClient() {
        MongoDbEndpoint endpoint = context.getEndpoint(URI_ENDPOINT, MongoDbEndpoint.class);
        endpoint.initializeConnection();
        MongoClient firstClient = endpoint.getMongoConnection();
        MongoDatabase firstDatabase = endpoint.getMongoDatabase();

        endpoint.stop();
        assertClosed(firstClient);

        endpoint.start();
        MongoClient secondClient = endpoint.getMongoConnection();
        assertNotNull(secondClient);
        assertNotSame(firstClient, secondClient);
        // the database and collection the producer uses are taken from the new client
        assertNotSame(firstDatabase, endpoint.getMongoDatabase());

        endpoint.stop();
        assertClosed(secondClient);
    }

    @Test
    void clientsTheEndpointDidNotCreateAreNotClosed() throws Exception {
        MongoClient beanClient = mock(MongoClient.class, RETURNS_DEEP_STUBS);
        MongoClient optionClient = mock(MongoClient.class, RETURNS_DEEP_STUBS);
        MongoClient componentClient = mock(MongoClient.class, RETURNS_DEEP_STUBS);
        context.getRegistry().bind("beanClient", beanClient);
        context.getRegistry().bind("optionClient", optionClient);

        // added before the component has a client, which it would use ahead of the bean named by the path
        String beanUri = "mongodb:beanClient?database=test&collection=test&operation=findAll";
        addRoute("bean", beanUri);
        context.getComponent("mongodb", MongoDbComponent.class).setMongoConnection(componentClient);
        String optionUri = URI_ENDPOINT + "&mongoConnection=#optionClient";
        addRoute("option", optionUri);
        String componentUri = "mongodb:dummy?database=test&collection=test&operation=findAll";
        addRoute("component", componentUri);

        assertSame(beanClient, context.getEndpoint(beanUri, MongoDbEndpoint.class).getMongoConnection());
        assertSame(optionClient, context.getEndpoint(optionUri, MongoDbEndpoint.class).getMongoConnection());
        assertSame(componentClient, context.getEndpoint(componentUri, MongoDbEndpoint.class).getMongoConnection());
        for (String routeId : new String[] { "bean", "option", "component" }) {
            context.getRouteController().stopRoute(routeId);
            assertTrue(context.removeRoute(routeId));
        }
        context.stop();

        verify(beanClient, never()).close();
        verify(optionClient, never()).close();
        verify(componentClient, never()).close();
    }

    private void assertClientClosedWhenTheRouteIsRemoved(String uri) throws Exception {
        addRoute("mongo", uri);
        MongoDbEndpoint endpoint = context.getEndpoint(uri, MongoDbEndpoint.class);
        MongoClient client = endpoint.getMongoConnection();
        assertNotNull(client);

        context.getRouteController().stopRoute("mongo");
        assertTrue(context.removeRoute("mongo"));

        assertTrue(endpoint.isStopped());
        assertClosed(client);
    }

    private void addRoute(String routeId, String uri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:" + routeId).routeId(routeId).to(uri);
            }
        });
    }

    private static void assertClosed(MongoClient client) {
        // a closed client fails at once, before it looks for a server
        assertThrows(IllegalStateException.class, () -> client.listDatabaseNames().first());
    }
}
