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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

public class KeycloakConsumerTest extends CamelTestSupport {

    @BindToRegistry("keycloakClient")
    private Keycloak keycloakClient = Mockito.mock(Keycloak.class);

    private RealmResource realmResource = Mockito.mock(RealmResource.class);

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("keycloak:events?keycloakClient=#keycloakClient&realm=test-realm&eventType=events")
                        .to("mock:events");

                from("keycloak:admin-events?keycloakClient=#keycloakClient&realm=test-realm&eventType=admin-events")
                        .to("mock:admin-events");
            }
        };
    }

    @Test
    public void testConsumeEvents() throws Exception {
        // Setup mock data
        List<EventRepresentation> events = new ArrayList<>();
        EventRepresentation event1 = new EventRepresentation();
        event1.setTime(System.currentTimeMillis());
        event1.setType("LOGIN");
        event1.setUserId("user-123");
        events.add(event1);

        EventRepresentation event2 = new EventRepresentation();
        event2.setTime(System.currentTimeMillis() + 1000);
        event2.setType("LOGOUT");
        event2.setUserId("user-456");
        events.add(event2);

        when(keycloakClient.realm(anyString())).thenReturn(realmResource);
        when(realmResource.getEvents(
                any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(events);

        MockEndpoint mock = getMockEndpoint("mock:events");
        mock.expectedMinimumMessageCount(2);
        mock.expectedMessagesMatches(exchange -> {
            Object body = exchange.getIn().getBody();
            return body instanceof EventRepresentation;
        });

        // Wait for consumer to poll
        Thread.sleep(2000);

        MockEndpoint.assertIsSatisfied(context);

        // Verify headers
        assertEquals("event",
                mock.getExchanges().get(0).getIn().getHeader(KeycloakConstants.EVENT_TYPE));
    }

    @Test
    public void testConsumeAdminEvents() throws Exception {
        // Setup mock data
        List<AdminEventRepresentation> adminEvents = new ArrayList<>();
        AdminEventRepresentation adminEvent1 = new AdminEventRepresentation();
        adminEvent1.setTime(System.currentTimeMillis());
        adminEvent1.setOperationType("CREATE");
        adminEvent1.setResourceType("USER");
        adminEvents.add(adminEvent1);

        AdminEventRepresentation adminEvent2 = new AdminEventRepresentation();
        adminEvent2.setTime(System.currentTimeMillis() + 1000);
        adminEvent2.setOperationType("UPDATE");
        adminEvent2.setResourceType("ROLE");
        adminEvents.add(adminEvent2);

        when(keycloakClient.realm(anyString())).thenReturn(realmResource);
        when(realmResource.getAdminEvents(
                any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(adminEvents);

        MockEndpoint mock = getMockEndpoint("mock:admin-events");
        mock.expectedMinimumMessageCount(2);
        mock.expectedMessagesMatches(exchange -> {
            Object body = exchange.getIn().getBody();
            return body instanceof AdminEventRepresentation;
        });

        // Wait for consumer to poll
        Thread.sleep(2000);

        MockEndpoint.assertIsSatisfied(context);

        // Verify headers
        assertEquals("admin-event",
                mock.getExchanges().get(0).getIn().getHeader(KeycloakConstants.EVENT_TYPE));
    }
}
