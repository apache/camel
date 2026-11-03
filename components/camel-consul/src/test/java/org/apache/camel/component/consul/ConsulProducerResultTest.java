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
package org.apache.camel.component.consul;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.component.consul.endpoint.ConsulAgentActions;
import org.apache.camel.component.consul.endpoint.ConsulCatalogActions;
import org.apache.camel.component.consul.endpoint.ConsulEventActions;
import org.apache.camel.component.consul.endpoint.ConsulKeyValueActions;
import org.apache.camel.component.consul.endpoint.ConsulPreparedQueryActions;
import org.apache.camel.component.consul.endpoint.ConsulSessionActions;
import org.apache.camel.component.consul.endpoint.ConsulStatusActions;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.AgentClient;
import org.kiwiproject.consul.CatalogClient;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.EventClient;
import org.kiwiproject.consul.KeyValueClient;
import org.kiwiproject.consul.PreparedQueryClient;
import org.kiwiproject.consul.SessionClient;
import org.kiwiproject.consul.StatusClient;
import org.kiwiproject.consul.model.agent.Registration;
import org.kiwiproject.consul.model.catalog.CatalogRegistration;
import org.kiwiproject.consul.model.event.Event;
import org.kiwiproject.consul.model.health.Service;
import org.kiwiproject.consul.model.query.StoredQuery;
import org.kiwiproject.consul.model.session.SessionInfo;
import org.kiwiproject.consul.option.EventOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The producers set the response of the Consul client as the body, without the Optional of the client, and the outcome
 * in the CamelConsulResult header.
 */
public class ConsulProducerResultTest extends CamelTestSupport {

    private final EventClient eventClient = mock(EventClient.class);
    private final KeyValueClient keyValueClient = mock(KeyValueClient.class);
    private final SessionClient sessionClient = mock(SessionClient.class);
    private final PreparedQueryClient preparedQueryClient = mock(PreparedQueryClient.class);
    private final AgentClient agentClient = mock(AgentClient.class);
    private final StatusClient statusClient = mock(StatusClient.class);
    private final CatalogClient catalogClient = mock(CatalogClient.class);

    @BindToRegistry("consul")
    public Consul consul() {
        Consul consul = mock(Consul.class);
        when(consul.eventClient()).thenReturn(eventClient);
        when(consul.keyValueClient()).thenReturn(keyValueClient);
        when(consul.sessionClient()).thenReturn(sessionClient);
        when(consul.preparedQueryClient()).thenReturn(preparedQueryClient);
        when(consul.agentClient()).thenReturn(agentClient);
        when(consul.statusClient()).thenReturn(statusClient);
        when(consul.catalogClient()).thenReturn(catalogClient);
        return consul;
    }

    @Test
    public void testFireWithoutBody() {
        Event event = mock(Event.class);
        when(eventClient.fireEvent(eq("my-event"), any(EventOptions.class))).thenReturn(event);

        Exchange out = send("consul:event?consulClient=#consul&key=my-event&action=" + ConsulEventActions.FIRE, null);

        // no payload: the client must not be called with a null payload, which fails in okhttp
        verify(eventClient, never()).fireEvent(anyString(), any(EventOptions.class), isNull());
        assertSame(event, out.getMessage().getBody());
        assertEquals(true, out.getMessage().getHeader(ConsulConstants.CONSUL_RESULT));
    }

    @Test
    public void testFireWithBody() {
        Event event = mock(Event.class);
        when(eventClient.fireEvent(eq("my-event"), any(EventOptions.class), eq("hello"))).thenReturn(event);

        Exchange out = send("consul:event?consulClient=#consul&key=my-event&action=" + ConsulEventActions.FIRE, "hello");

        assertSame(event, out.getMessage().getBody());
        assertEquals(true, out.getMessage().getHeader(ConsulConstants.CONSUL_RESULT));
    }

    @Test
    public void testGetSessions() {
        when(keyValueClient.getSession("locked")).thenReturn(Optional.of("session-1"));
        when(keyValueClient.getSession("free")).thenReturn(Optional.empty());

        Exchange out = send("consul:kv?consulClient=#consul&key=locked&action=" + ConsulKeyValueActions.GET_SESSIONS,
                "input");
        assertEquals("session-1", out.getMessage().getBody());
        assertEquals(true, out.getMessage().getHeader(ConsulConstants.CONSUL_RESULT));

        out = send("consul:kv?consulClient=#consul&key=free&action=" + ConsulKeyValueActions.GET_SESSIONS, "input");
        assertEquals("input", out.getMessage().getBody());
        assertEquals(false, out.getMessage().getHeader(ConsulConstants.CONSUL_RESULT));
    }

    @Test
    public void testRenewSession() {
        SessionInfo info = mock(SessionInfo.class);
        when(sessionClient.renewSession(isNull(), eq("session-1"))).thenReturn(Optional.of(info));
        when(sessionClient.renewSession(isNull(), eq("session-2"))).thenReturn(Optional.empty());

        Exchange out = send("consul:session?consulClient=#consul&action=" + ConsulSessionActions.RENEW, "session-1");
        assertSame(info, out.getMessage().getBody());
        assertEquals(true, out.getMessage().getHeader(ConsulConstants.CONSUL_RESULT));

        out = send("consul:session?consulClient=#consul&action=" + ConsulSessionActions.RENEW, "session-2");
        assertEquals("session-2", out.getMessage().getBody());
        assertEquals(false, out.getMessage().getHeader(ConsulConstants.CONSUL_RESULT));
    }

    @Test
    public void testGetPreparedQuery() {
        StoredQuery query = mock(StoredQuery.class);
        when(preparedQueryClient.getPreparedQuery("query-1")).thenReturn(Optional.of(query));
        when(preparedQueryClient.getPreparedQuery("query-2")).thenReturn(Optional.empty());

        Exchange out = send("consul:preparedQuery?consulClient=#consul&action=" + ConsulPreparedQueryActions.GET,
                "query-1");
        assertSame(query, out.getMessage().getBody());
        assertEquals(true, out.getMessage().getHeader(ConsulConstants.CONSUL_RESULT));

        out = send("consul:preparedQuery?consulClient=#consul&action=" + ConsulPreparedQueryActions.GET, "query-2");
        assertEquals("query-2", out.getMessage().getBody());
        assertEquals(false, out.getMessage().getHeader(ConsulConstants.CONSUL_RESULT));
    }

    @Test
    public void testAgentActions() {
        Map<String, Service> services = Map.of("foo", mock(Service.class));
        when(agentClient.getServices()).thenReturn(services);

        Exchange out = send("consul:agent?consulClient=#consul&action=" + ConsulAgentActions.SERVICES, null);
        assertSame(services, out.getMessage().getBody());
        assertEquals(true, out.getMessage().getHeader(ConsulConstants.CONSUL_RESULT));

        Registration registration = mock(Registration.class);
        out = send("consul:agent?consulClient=#consul&action=" + ConsulAgentActions.REGISTER, registration);
        verify(agentClient).register(registration);
        assertSame(registration, out.getMessage().getBody());
        assertEquals(true, out.getMessage().getHeader(ConsulConstants.CONSUL_RESULT));

        out = template.request("consul:agent?consulClient=#consul&action=" + ConsulAgentActions.DEREGISTER,
                e -> e.getMessage().setHeader(ConsulConstants.CONSUL_SERVICE_ID, "foo-1"));
        verify(agentClient).deregister(eq("foo-1"), any());
        assertEquals(true, out.getMessage().getHeader(ConsulConstants.CONSUL_RESULT));
    }

    @Test
    public void testStatusActions() {
        when(statusClient.getLeader()).thenReturn("10.0.0.1:8300");
        when(statusClient.getPeers()).thenReturn(List.of("10.0.0.1:8300"));

        Exchange out = send("consul:status?consulClient=#consul&action=" + ConsulStatusActions.LEADER, null);
        assertEquals("10.0.0.1:8300", out.getMessage().getBody());
        assertEquals(true, out.getMessage().getHeader(ConsulConstants.CONSUL_RESULT));

        out = send("consul:status?consulClient=#consul&action=" + ConsulStatusActions.PEERS, null);
        assertEquals(List.of("10.0.0.1:8300"), out.getMessage().getBody());
        assertEquals(true, out.getMessage().getHeader(ConsulConstants.CONSUL_RESULT));
    }

    @Test
    public void testCatalogRegister() {
        CatalogRegistration registration = mock(CatalogRegistration.class);

        Exchange out = send("consul:catalog?consulClient=#consul&action=" + ConsulCatalogActions.REGISTER, registration);

        verify(catalogClient).register(registration);
        assertEquals(true, out.getMessage().getHeader(ConsulConstants.CONSUL_RESULT));
    }

    private Exchange send(String uri, Object body) {
        Exchange out = template.request(uri, e -> e.getMessage().setBody(body));
        if (out.getException() != null) {
            throw new AssertionError("The exchange failed", out.getException());
        }
        return out;
    }
}
