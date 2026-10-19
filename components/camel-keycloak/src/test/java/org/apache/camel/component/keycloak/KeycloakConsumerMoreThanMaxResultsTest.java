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
 * More events than maxResults between two polls: Keycloak returns the most recent events first, so the events that do
 * not fit into the first maxResults are the older ones, and they must still be routed.
 */
class KeycloakConsumerMoreThanMaxResultsTest extends CamelTestSupport {

    @BindToRegistry("keycloakClient")
    private final Keycloak keycloakClient = Mockito.mock(Keycloak.class);

    private final RealmResource realmResource = Mockito.mock(RealmResource.class);

    // the events in the realm, the most recent first as Keycloak returns them
    private final List<EventRepresentation> events = new ArrayList<>();
    private final List<AdminEventRepresentation> adminEvents = new ArrayList<>();

    @Override
    protected void doPreSetup() {
        when(keycloakClient.realm(anyString())).thenReturn(realmResource);
        // honour first and max like Keycloak does
        when(realmResource.getEvents(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenAnswer(invocation -> page(events, invocation.getArgument(6), invocation.getArgument(7)));
        when(realmResource.getAdminEvents(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenAnswer(invocation -> page(adminEvents, invocation.getArgument(8), invocation.getArgument(9)));
    }

    private static <T> List<T> page(List<T> all, Integer first, Integer max) {
        int from = Math.min(first != null ? first : 0, all.size());
        int to = max != null ? Math.min(from + max, all.size()) : all.size();
        return new ArrayList<>(all.subList(from, to));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("keycloak:events?keycloakClient=#keycloakClient&realm=test-realm&eventType=events"
                     + "&maxResults=2&startScheduler=false")
                        .routeId("events")
                        .to("mock:events");

                from("keycloak:admin-events?keycloakClient=#keycloakClient&realm=test-realm&eventType=admin-events"
                     + "&maxResults=2&startScheduler=false")
                        .routeId("admin-events")
                        .to("mock:admin-events");
            }
        };
    }

    @Test
    void routesTheEventsBeyondMaxResults() throws Exception {
        events.add(event(1000, "user-1"));
        assertEquals(1, poll("events"));

        // three new events between two polls, one more than maxResults
        events.add(0, event(2000, "user-2"));
        events.add(0, event(3000, "user-3"));
        events.add(0, event(4000, "user-4"));
        poll("events");
        poll("events");

        MockEndpoint mock = getMockEndpoint("mock:events");
        mock.expectedMessageCount(4);
        mock.assertIsSatisfied();
        List<String> userIds = new ArrayList<>();
        for (Exchange exchange : mock.getReceivedExchanges()) {
            userIds.add(exchange.getIn().getBody(EventRepresentation.class).getUserId());
        }
        assertEquals(List.of("user-1", "user-2", "user-3", "user-4"), userIds.stream().sorted().toList());
    }

    @Test
    void routesTheAdminEventsBeyondMaxResults() throws Exception {
        adminEvents.add(adminEvent(1000, "users/1"));
        assertEquals(1, poll("admin-events"));

        // three new admin events between two polls, one more than maxResults
        adminEvents.add(0, adminEvent(2000, "users/2"));
        adminEvents.add(0, adminEvent(3000, "users/3"));
        adminEvents.add(0, adminEvent(4000, "users/4"));
        poll("admin-events");
        poll("admin-events");

        MockEndpoint mock = getMockEndpoint("mock:admin-events");
        mock.expectedMessageCount(4);
        mock.assertIsSatisfied();
        List<String> paths = new ArrayList<>();
        for (Exchange exchange : mock.getReceivedExchanges()) {
            paths.add(exchange.getIn().getBody(AdminEventRepresentation.class).getResourcePath());
        }
        assertEquals(List.of("users/1", "users/2", "users/3", "users/4"), paths.stream().sorted().toList());
    }

    @Test
    void newEventsArrivingWhilePagingAreRoutedOnce() throws Exception {
        events.add(event(1000, "user-1"));
        assertEquals(1, poll("events"));

        events.add(0, event(2000, "user-2"));
        events.add(0, event(3000, "user-3"));
        events.add(0, event(4000, "user-4"));
        events.add(0, event(5000, "user-5"));
        events.add(0, event(6000, "user-6"));
        // a new event arrives after the first page was read: the next pages are shifted by one
        int[] calls = { 0 };
        when(realmResource.getEvents(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    if (calls[0]++ == 1) {
                        events.add(0, event(7000, "user-7"));
                    }
                    return page(events, invocation.getArgument(6), invocation.getArgument(7));
                });
        poll("events");
        poll("events");
        poll("events");

        MockEndpoint mock = getMockEndpoint("mock:events");
        mock.expectedMessageCount(7);
        mock.assertIsSatisfied();
        List<String> userIds = new ArrayList<>();
        for (Exchange exchange : mock.getReceivedExchanges()) {
            userIds.add(exchange.getIn().getBody(EventRepresentation.class).getUserId());
        }
        assertEquals(List.of("user-1", "user-2", "user-3", "user-4", "user-5", "user-6", "user-7"),
                userIds.stream().sorted().toList());
    }

    private int poll(String routeId) throws Exception {
        return ((KeycloakConsumer) context.getRoute(routeId).getConsumer()).poll();
    }

    private static EventRepresentation event(long time, String userId) {
        EventRepresentation event = new EventRepresentation();
        event.setTime(time);
        event.setType("LOGIN");
        event.setUserId(userId);
        return event;
    }

    private static AdminEventRepresentation adminEvent(long time, String resourcePath) {
        AdminEventRepresentation event = new AdminEventRepresentation();
        event.setTime(time);
        event.setOperationType("CREATE");
        event.setResourceType("USER");
        event.setResourcePath(resourcePath);
        return event;
    }
}
