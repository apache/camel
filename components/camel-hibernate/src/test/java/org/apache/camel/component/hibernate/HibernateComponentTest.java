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
package org.apache.camel.component.hibernate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HibernateComponentTest extends CamelTestSupport {

    private EntityManagerFactory entityManagerFactory;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeEach
    public void cleanDatabase() {
        if (template != null) {
            template.sendBodyAndHeader(
                    "direct:query",
                    null,
                    HibernateConstants.HIBERNATE_QUERY,
                    "DELETE FROM Item");
        }
    }

    @AfterEach
    public void tearDownPersistence() throws Exception {
        if (context != null) {
            for (Route route : context.getRoutes()) {
                context.getRouteController().stopRoute(route.getRouteId());
            }
        }
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        entityManagerFactory = Persistence.createEntityManagerFactory("camel-hibernate-tests");

        // Bind consumer default query parameter to Registry
        camelContext.getRegistry().bind("consumerParams", Map.of("name", "Laptop"));

        // Configure HibernateComponent with EntityManagerFactory and register it
        HibernateComponent component = new HibernateComponent();
        component.setEntityManagerFactory(entityManagerFactory);
        camelContext.addComponent("hibernate", component);

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route for persist producer
                from("direct:persist")
                        .to("hibernate:org.apache.camel.component.hibernate.Item?usePersist=true");

                // Route for query operations
                from("direct:query")
                        .to("hibernate:org.apache.camel.component.hibernate.Item");

                // Route for named query execution
                from("direct:namedQuery")
                        .to("hibernate:org.apache.camel.component.hibernate.Item?namedQuery=Item.findAll&maximumResults=1");

                // Route for parameterized HQL query
                from("direct:parameterizedQuery")
                        .to("hibernate:org.apache.camel.component.hibernate.Item"
                            + "?query=SELECT i FROM Item i WHERE i.name = :name");

                // Base Polling Consumer (autoStartup=false to prevent background race conditions)
                from("hibernate:org.apache.camel.component.hibernate.Item"
                     + "?query=SELECT i FROM Item i"
                     + "&consumeDelete=true"
                     + "&initialDelay=10"
                     + "&delay=100")
                        .routeId("base-polling-consumer")
                        .autoStartup(false)
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testPersistAndQueryAndConsume() throws Exception {
        Item item1 = new Item("Laptop", 1200.00);
        Item item2 = new Item("Phone", 800.00);

        template.sendBody("direct:persist", item1);
        template.sendBody("direct:persist", item2);

        List<?> results = template.requestBodyAndHeader("direct:query", null,
                HibernateConstants.HIBERNATE_QUERY, "SELECT i FROM Item i ORDER BY i.name", List.class);

        assertNotNull(results);
        assertEquals(2, results.size());

        List<?> maxResults = template.requestBody("direct:namedQuery", null, List.class);
        assertNotNull(maxResults);
        assertEquals(1, maxResults.size());

        context.getRouteController().startRoute("base-polling-consumer");

        mockResult.expectedMinimumMessageCount(2);
        mockResult.assertIsSatisfied(5000);

        List<?> remaining = template.requestBodyAndHeader("direct:query", null,
                HibernateConstants.HIBERNATE_QUERY, "SELECT i FROM Item i", List.class);
        assertEquals(0, remaining.size());
    }

    @Test
    public void testProducerPersistsCollection() throws Exception {
        Item item1 = new Item("Keyboard", 100.00);
        Item item2 = new Item("Monitor", 400.00);

        template.sendBody("direct:persist", List.of(item1, item2));

        List<?> results = queryAllItems();

        assertEquals(2, results.size());
    }

    @Test
    public void testProducerTransactionRollsBackWhenEntityOperationFails() {
        Item validItem = new Item("Valid Item", 100.00);
        List<Object> items = List.of(validItem, new Object());

        assertThrows(Exception.class, () -> template.requestBody("direct:persist", items));

        List<?> remaining = queryAllItems();

        assertEquals(0, remaining.size(),
                "A failure while processing a collection must roll back the complete transaction");
    }

    @Test
    public void testProducerTransactionRollsBackWhenQueryFails() {
        assertThrows(Exception.class, () -> template.requestBodyAndHeader(
                "direct:query",
                null,
                HibernateConstants.HIBERNATE_QUERY,
                "SELECT i FROM ItemThatDoesNotExist i"));

        List<?> remaining = queryAllItems();

        assertEquals(0, remaining.size());
    }

    @Test
    public void testProducerQueryParameters() throws Exception {
        template.sendBody("direct:persist", new Item("Laptop", 1200.00));
        template.sendBody("direct:persist", new Item("Phone", 800.00));

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("name", "Laptop");

        List<?> results = template.requestBodyAndHeaders(
                "direct:parameterizedQuery",
                null,
                Map.of(HibernateConstants.HIBERNATE_PARAMETERS, parameters),
                List.class);

        assertNotNull(results);
        assertEquals(1, results.size());

        Item result = (Item) results.get(0);
        assertEquals("Laptop", result.getName());
        assertEquals(1200.00, result.getPrice());
    }

    @Test
    public void testProducerReturnsOriginalEntityBody() {
        Item item = new Item("Tablet", 600.00);

        Item result = template.requestBody("direct:persist", item, Item.class);

        assertNotNull(result);
        assertEquals(item, result);
    }

    @Test
    public void testProducerQueryReturnsList() throws Exception {
        template.sendBody("direct:persist", new Item("Laptop", 1200.00));
        template.sendBody("direct:persist", new Item("Phone", 800.00));

        List<?> results = template.requestBodyAndHeader(
                "direct:query",
                null,
                HibernateConstants.HIBERNATE_QUERY,
                "SELECT i FROM Item i ORDER BY i.price DESC",
                List.class);

        assertNotNull(results);
        assertEquals(2, results.size());

        Item first = (Item) results.get(0);
        Item second = (Item) results.get(1);

        assertEquals("Laptop", first.getName());
        assertEquals("Phone", second.getName());
    }

    @Test
    public void testProducerMaximumResults() throws Exception {
        template.sendBody("direct:persist", new Item("Laptop", 1200.00));
        template.sendBody("direct:persist", new Item("Phone", 800.00));

        List<?> results = template.requestBodyAndHeader(
                "direct:namedQuery",
                null,
                HibernateConstants.HIBERNATE_PARAMETERS,
                Map.of(),
                List.class);

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    public void testNamedQueryFindByName() throws Exception {
        template.sendBody("direct:persist", new Item("Laptop", 1200.00));
        template.sendBody("direct:persist", new Item("Phone", 800.00));

        List<?> results = template.requestBodyAndHeaders(
                "direct:query",
                null,
                Map.of(
                        HibernateConstants.HIBERNATE_QUERY,
                        "SELECT i FROM Item i WHERE i.name = :name",
                        HibernateConstants.HIBERNATE_PARAMETERS,
                        Map.of("name", "Phone")),
                List.class);

        assertNotNull(results);
        assertEquals(1, results.size());

        Item result = (Item) results.get(0);
        assertEquals("Phone", result.getName());
    }

    @Test
    public void testConsumerDeletesConsumedEntities() throws Exception {
        template.sendBody("direct:persist", new Item("Keyboard", 100.00));
        template.sendBody("direct:persist", new Item("Monitor", 400.00));

        context.getRouteController().startRoute("base-polling-consumer");

        mockResult.expectedMinimumMessageCount(2);
        mockResult.assertIsSatisfied(5000);

        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(5))
                .pollInterval(java.time.Duration.ofMillis(50))
                .until(() -> queryAllItems().isEmpty());

        assertEquals(0, queryAllItems().size());
    }

    @Test
    public void testConsumerProcessesEntityBody() throws Exception {
        template.sendBody("direct:persist", new Item("Consumer Item", 250.00));

        context.getRouteController().startRoute("base-polling-consumer");

        mockResult.expectedMessageCount(1);
        mockResult.message(0).body().isInstanceOf(Item.class);

        mockResult.assertIsSatisfied(5000);

        Item consumed = mockResult.getExchanges().get(0).getIn().getBody(Item.class);

        assertNotNull(consumed);
        assertEquals("Consumer Item", consumed.getName());
        assertEquals(250.00, consumed.getPrice());
    }

    @Test
    public void testConsumerMaximumResults() throws Exception {
        template.sendBody("direct:persist", new Item("One", 1.00));
        template.sendBody("direct:persist", new Item("Two", 2.00));
        template.sendBody("direct:persist", new Item("Three", 3.00));

        MockEndpoint limitedConsumer = getMockEndpoint("mock:limitedConsumer");
        limitedConsumer.expectedMessageCount(1);

        String routeId = "dynamic-limited-consumer";

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("hibernate:org.apache.camel.component.hibernate.Item"
                         + "?query=SELECT i FROM Item i ORDER BY i.id"
                         + "&maximumResults=1"
                         + "&consumeDelete=true"
                         + "&initialDelay=10"
                         + "&delay=100")
                            .routeId(routeId)
                            .to("mock:limitedConsumer");
                }
            });

            limitedConsumer.assertIsSatisfied(5000);

            org.awaitility.Awaitility.await()
                    .atMost(java.time.Duration.ofSeconds(5))
                    .pollInterval(java.time.Duration.ofMillis(50))
                    .until(() -> queryAllItems().size() == 2);

            List<?> remaining = queryAllItems();

            assertEquals(2, remaining.size());
        } finally {
            context.getRouteController().stopRoute(routeId);
            context.removeRoute(routeId);
        }
    }

    @Test
    public void testConsumerQueryParameters() throws Exception {
        template.sendBody("direct:persist", new Item("Laptop", 1200.00));
        template.sendBody("direct:persist", new Item("Phone", 800.00));

        MockEndpoint parameterizedConsumer = getMockEndpoint("mock:parameterizedConsumer");
        parameterizedConsumer.expectedMessageCount(1);
        parameterizedConsumer.message(0).body().isInstanceOf(Item.class);

        String routeId = "dynamic-parameterized-consumer";

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("hibernate:org.apache.camel.component.hibernate.Item"
                         + "?query=SELECT i FROM Item i WHERE i.name = :name"
                         + "&parameters=#consumerParams"
                         + "&consumeDelete=true"
                         + "&initialDelay=10"
                         + "&delay=100")
                            .routeId(routeId)
                            .to("mock:parameterizedConsumer");
                }
            });

            parameterizedConsumer.assertIsSatisfied(5000);

            Item consumed = parameterizedConsumer.getExchanges().get(0).getIn().getBody(Item.class);

            assertNotNull(consumed);
            assertEquals("Laptop", consumed.getName());

            org.awaitility.Awaitility.await()
                    .atMost(java.time.Duration.ofSeconds(5))
                    .pollInterval(java.time.Duration.ofMillis(50))
                    .until(() -> queryAllItems().size() == 1);

            List<?> remaining = queryAllItems();

            assertEquals(1, remaining.size());
            assertEquals("Phone", ((Item) remaining.get(0)).getName());
        } finally {
            context.getRouteController().stopRoute(routeId);
            context.removeRoute(routeId);
        }
    }

    private List<?> queryAllItems() {
        return template.requestBodyAndHeader(
                "direct:query",
                null,
                HibernateConstants.HIBERNATE_QUERY,
                "SELECT i FROM Item i",
                List.class);
    }
}
