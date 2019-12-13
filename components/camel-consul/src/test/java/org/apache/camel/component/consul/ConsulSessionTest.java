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
import java.util.UUID;

import com.orbitz.consul.model.session.ImmutableSession;
import com.orbitz.consul.model.session.SessionCreatedResponse;
import com.orbitz.consul.model.session.SessionInfo;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.consul.endpoint.ConsulSessionActions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConsulSessionTest extends ConsulTestSupport {

    @Test
    public void testServiceInstance() {
        final String name = UUID.randomUUID().toString();
        final int sessions = getConsul().sessionClient().listSessions().size();

        {
            List<SessionInfo> list = fluentTemplate().withHeader(ConsulConstants.CONSUL_ACTION, ConsulSessionActions.LIST).to("direct:consul").request(List.class);

            Assertions.assertEquals(sessions, list.size());
            Assertions.assertFalse(list.stream().anyMatch(s -> s.getName().isPresent() && s.getName().get().equals(name)));
        }

        SessionCreatedResponse res = fluentTemplate().withHeader(ConsulConstants.CONSUL_ACTION, ConsulSessionActions.CREATE).withBody(ImmutableSession.builder().name(name).build())
            .to("direct:consul").request(SessionCreatedResponse.class);

        Assertions.assertNotNull(res.getId());

        {
            List<SessionInfo> list = fluentTemplate().withHeader(ConsulConstants.CONSUL_ACTION, ConsulSessionActions.LIST).to("direct:consul").request(List.class);

            Assertions.assertEquals(sessions + 1, list.size());
            Assertions.assertTrue(list.stream().anyMatch(s -> s.getName().isPresent() && s.getName().get().equals(name)));
        }

        {
            fluentTemplate().withHeader(ConsulConstants.CONSUL_ACTION, ConsulSessionActions.DESTROY).withHeader(ConsulConstants.CONSUL_SESSION, res.getId()).to("direct:consul")
                .send();

            List<SessionInfo> list = fluentTemplate().withHeader(ConsulConstants.CONSUL_ACTION, ConsulSessionActions.LIST).to("direct:consul").request(List.class);

            Assertions.assertEquals(sessions, list.size());
            Assertions.assertFalse(list.stream().anyMatch(s -> s.getName().isPresent() && s.getName().get().equals(name)));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:consul").to("consul:session");
            }
        };
    }
}
