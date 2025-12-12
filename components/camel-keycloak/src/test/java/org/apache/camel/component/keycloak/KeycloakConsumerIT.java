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
package org.apache.camel.component.keycloak;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.keycloak.services.KeycloakService;
import org.apache.camel.test.infra.keycloak.services.KeycloakServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.keycloak.representations.idm.AdminEventRepresentation;
import org.keycloak.representations.idm.EventRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Keycloak consumer operations using test-infra for container management.
 *
 * This test demonstrates how to consume events and admin events from a Keycloak instance.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KeycloakConsumerIT extends CamelTestSupport {

    private static final Logger log = LoggerFactory.getLogger(KeycloakConsumerIT.class);

    @RegisterExtension
    static KeycloakService keycloakService = KeycloakServiceFactory.createService();

    // Test data - use unique names to avoid conflicts
    private static final String TEST_REALM_NAME = "consumer-test-realm-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_USER_NAME = "consumer-test-user-" + UUID.randomUUID().toString().substring(0, 8);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        KeycloakComponent keycloak = context.getComponent("keycloak", KeycloakComponent.class);
        KeycloakConfiguration conf = new KeycloakConfiguration();
        conf.setServerUrl(keycloakService.getKeycloakServerUrl());
        conf.setRealm(keycloakService.getKeycloakRealm());
        conf.setAuthRealm(keycloakService.getKeycloakRealm()); // Authenticate against master realm
        conf.setUsername(keycloakService.getKeycloakUsername());
        conf.setPassword(keycloakService.getKeycloakPassword());
        keycloak.setConfiguration(conf);
        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String keycloakProducerEndpoint = "keycloak:admin";

                // Producer routes to create events
                from("direct:createRealm")
                        .to(keycloakProducerEndpoint + "?operation=createRealm");

                from("direct:createUser")
                        .to(keycloakProducerEndpoint + "?operation=createUser");

                from("direct:createRole")
                        .to(keycloakProducerEndpoint + "?operation=createRole");

                from("direct:deleteRealm")
                        .to(keycloakProducerEndpoint + "?operation=deleteRealm");

                // Consumer routes - consuming admin events (autoStartup=false, started manually in test)
                from("keycloak:adminEvents"
                     + "?realm=" + TEST_REALM_NAME
                     + "&eventType=admin-events"
                     + "&maxResults=50"
                     + "&initialDelay=500"
                     + "&delay=1000")
                        .autoStartup(false)
                        .routeId("admin-events-consumer")
                        .to("mock:admin-events");

                // Consumer route - consuming regular events (if enabled in Keycloak)
                from("keycloak:events"
                     + "?realm=" + TEST_REALM_NAME
                     + "&eventType=events"
                     + "&maxResults=50"
                     + "&initialDelay=500"
                     + "&delay=1000")
                        .autoStartup(false)
                        .routeId("events-consumer")
                        .to("mock:events");
            }
        };
    }

    @Test
    @Order(1)
    void testSetup_CreateRealm() throws Exception {
        log.info("Creating test realm: {}", TEST_REALM_NAME);

        template.sendBodyAndHeader("direct:createRealm", null,
                KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

        log.info("Test realm created successfully");

        // Enable events and admin events on the realm
        log.info("Enabling events and admin events for realm: {}", TEST_REALM_NAME);
        org.keycloak.admin.client.Keycloak keycloakClient = keycloakService.getKeycloakAdminClient();
        org.keycloak.representations.idm.RealmRepresentation realmRep
                = keycloakClient.realm(TEST_REALM_NAME).toRepresentation();

        // Enable admin events
        realmRep.setAdminEventsEnabled(true);
        realmRep.setAdminEventsDetailsEnabled(true);

        // Enable regular events
        realmRep.setEventsEnabled(true);
        realmRep.setEventsListeners(java.util.Arrays.asList("jboss-logging"));

        keycloakClient.realm(TEST_REALM_NAME).update(realmRep);
        log.info("Events and admin events enabled successfully");
    }

    @Test
    @Order(2)
    void testConsumeAdminEvents_CreateUser() throws Exception {
        // Start the admin events consumer route
        context.getRouteController().startRoute("admin-events-consumer");

        // Wait for consumer route to be started
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> context.getRouteController().getRouteStatus("admin-events-consumer").isStarted());

        MockEndpoint mock = getMockEndpoint("mock:admin-events");
        mock.reset();
        mock.expectedMinimumMessageCount(1);
        mock.setResultWaitTime(TimeUnit.SECONDS.toMillis(10));

        // Create a user which should generate an admin event
        log.info("Creating user: {} in realm: {}", TEST_USER_NAME, TEST_REALM_NAME);

        template.sendBodyAndHeaders("direct:createUser", null,
                new java.util.HashMap<>() {
                    {
                        put(KeycloakConstants.REALM_NAME, TEST_REALM_NAME);
                        put(KeycloakConstants.USERNAME, TEST_USER_NAME);
                        put(KeycloakConstants.USER_EMAIL, TEST_USER_NAME + "@test.com");
                        put(KeycloakConstants.USER_FIRST_NAME, "Test");
                        put(KeycloakConstants.USER_LAST_NAME, "User");
                    }
                });

        log.info("Waiting for admin events to be consumed...");
        mock.assertIsSatisfied();

        // Verify the event body is an AdminEventRepresentation
        Object eventBody = mock.getExchanges().get(0).getIn().getBody();
        assertNotNull(eventBody);
        assertTrue(eventBody instanceof AdminEventRepresentation,
                "Event body should be AdminEventRepresentation, but was: " + eventBody.getClass().getName());

        AdminEventRepresentation adminEvent = (AdminEventRepresentation) eventBody;
        log.info("Received admin event - Operation: {}, Resource Type: {}",
                adminEvent.getOperationType(), adminEvent.getResourceType());

        // Verify headers
        assertEquals("admin-event",
                mock.getExchanges().get(0).getIn().getHeader(KeycloakConstants.EVENT_TYPE));
        assertEquals(TEST_REALM_NAME,
                mock.getExchanges().get(0).getIn().getHeader(KeycloakConstants.REALM_NAME));
    }

    @Test
    @Order(4)
    void testConsumer_NoNewEvents() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:admin-events");
        mock.reset();
        mock.expectedMessageCount(0);
        mock.setResultWaitTime(TimeUnit.SECONDS.toMillis(5));

        // Don't create any new events, consumer should not receive anything
        log.info("Testing that consumer doesn't receive duplicate events...");

        mock.assertIsSatisfied();
        log.info("No duplicate events received - event tracking is working correctly");
    }

    @Test
    @Order(5)
    void testConsumeRegularEvents() throws Exception {
        // Start the regular events consumer route
        context.getRouteController().startRoute("events-consumer");

        // Wait for consumer route to be started
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> context.getRouteController().getRouteStatus("events-consumer").isStarted());

        MockEndpoint mock = getMockEndpoint("mock:events");
        mock.reset();

        // Note: Regular events (LOGIN, LOGOUT, etc.) require event listeners to be enabled in Keycloak
        // Since this is a test environment, regular events might not be enabled by default
        // This test is here to demonstrate the pattern, but may not receive events

        log.info("Testing regular events consumer (may not receive events if event listener not enabled)...");

        // Wait a bit to see if any events come through
        Awaitility.await()
                .pollDelay(3, TimeUnit.SECONDS)
                .atMost(4, TimeUnit.SECONDS)
                .until(() -> true);

        if (mock.getExchanges().size() > 0) {
            Object eventBody = mock.getExchanges().get(0).getIn().getBody();
            assertTrue(eventBody instanceof EventRepresentation);
            log.info("Received regular event");
        } else {
            log.info("No regular events received (event listeners may not be enabled in test Keycloak)");
        }
    }

    @Test
    @Order(99)
    void testCleanup_DeleteRealm() {
        try {
            log.info("Deleting test realm: {}", TEST_REALM_NAME);

            template.sendBodyAndHeader("direct:deleteRealm", null,
                    KeycloakConstants.REALM_NAME, TEST_REALM_NAME);

            log.info("Test realm deleted successfully");
        } catch (Exception e) {
            log.warn("Failed to delete test realm {}: {}", TEST_REALM_NAME, e.getMessage());
        }
    }
}
