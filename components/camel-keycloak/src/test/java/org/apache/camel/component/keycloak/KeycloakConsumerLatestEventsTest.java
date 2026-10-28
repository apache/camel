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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.AdminEventRepresentation;
import org.keycloak.representations.idm.EventRepresentation;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Keycloak returns the events of the latest timestamp again on the next poll, so the consumer must remember that it
 * routed them when it moves its checkpoint to that timestamp.
 */
class KeycloakConsumerLatestEventsTest extends CamelTestSupport {

    @BindToRegistry("keycloakClient")
    private final Keycloak keycloakClient = Mockito.mock(Keycloak.class);

    private final RealmResource realmResource = Mockito.mock(RealmResource.class);

    // the events in the realm, the most recent first as Keycloak returns them
    private final List<EventRepresentation> events = new ArrayList<>();
    private final List<AdminEventRepresentation> adminEvents = new ArrayList<>();

    @Override
    protected void doPreSetup() {
        when(keycloakClient.realm(anyString())).thenReturn(realmResource);
        when(realmResource.getEvents(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenAnswer(invocation -> new ArrayList<>(events));
        when(realmResource.getAdminEvents(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenAnswer(invocation -> new ArrayList<>(adminEvents));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("keycloak:events?keycloakClient=#keycloakClient&realm=test-realm&eventType=events"
                     + "&startScheduler=false")
                        .routeId("events")
                        .to("mock:events");

                from("keycloak:admin-events?keycloakClient=#keycloakClient&realm=test-realm&eventType=admin-events"
                     + "&startScheduler=false")
                        .routeId("admin-events")
                        .to("mock:admin-events");
            }
        };
    }

    @Test
    void routesEachEventOnce() throws Exception {
        events.add(event(2000, "LOGOUT", "user-456"));
        events.add(event(1000, "LOGIN", "user-123"));

        assertEquals(2, poll("events"));
        assertEquals(0, poll("events"), "the events of the first poll must not be routed again");

        // a new event, and a second one at the same time as the previous latest event
        events.add(0, event(3000, "LOGIN", "user-789"));
        events.add(1, event(2000, "LOGIN", "user-000"));
        assertEquals(2, poll("events"));
        assertEquals(0, poll("events"), "the events of the second poll must not be routed again");

        MockEndpoint mock = getMockEndpoint("mock:events");
        mock.expectedMessageCount(4);
        mock.assertIsSatisfied();
        assertEquals(List.of("user-456", "user-123", "user-789", "user-000"), userIds(mock));
    }

    @Test
    void routesEachAdminEventOnce() throws Exception {
        adminEvents.add(adminEvent(2000, "UPDATE", "users/2"));
        adminEvents.add(adminEvent(1000, "CREATE", "users/1"));

        assertEquals(2, poll("admin-events"));
        assertEquals(0, poll("admin-events"), "the admin events of the first poll must not be routed again");

        // a new admin event, and a second one at the same time as the previous latest admin event
        adminEvents.add(0, adminEvent(3000, "DELETE", "users/1"));
        adminEvents.add(1, adminEvent(2000, "CREATE", "users/3"));
        assertEquals(2, poll("admin-events"));
        assertEquals(0, poll("admin-events"), "the admin events of the second poll must not be routed again");

        MockEndpoint mock = getMockEndpoint("mock:admin-events");
        mock.expectedMessageCount(4);
        mock.assertIsSatisfied();
        assertEquals(List.of("users/2", "users/1", "users/1", "users/3"), resourcePaths(mock));
    }

    private int poll(String routeId) throws Exception {
        return ((KeycloakConsumer) context.getRoute(routeId).getConsumer()).poll();
    }

    private static List<String> userIds(MockEndpoint mock) {
        List<String> answer = new ArrayList<>();
        for (Exchange exchange : mock.getReceivedExchanges()) {
            answer.add(exchange.getIn().getBody(EventRepresentation.class).getUserId());
        }
        return answer;
    }

    private static List<String> resourcePaths(MockEndpoint mock) {
        List<String> answer = new ArrayList<>();
        for (Exchange exchange : mock.getReceivedExchanges()) {
            answer.add(exchange.getIn().getBody(AdminEventRepresentation.class).getResourcePath());
        }
        return answer;
    }

    private static EventRepresentation event(long time, String type, String userId) {
        EventRepresentation event = new EventRepresentation();
        event.setTime(time);
        event.setType(type);
        event.setUserId(userId);
        return event;
    }

    private static AdminEventRepresentation adminEvent(long time, String operationType, String resourcePath) {
        AdminEventRepresentation event = new AdminEventRepresentation();
        event.setTime(time);
        event.setOperationType(operationType);
        event.setResourceType("USER");
        event.setResourcePath(resourcePath);
        return event;
    }
}
