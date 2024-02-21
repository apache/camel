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

import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.consul.endpoint.ConsulAgentActions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.model.agent.ImmutableRegistration;
import org.kiwiproject.consul.model.health.Service;

public class ConsulAgentIT extends ConsulTestSupport {

    public final String serviceId = generateRandomString();

    @Test
    public void testRegisterDeregister() {
        Map<String, Service> beforeRegistration = getConsul().agentClient()
                .getServices();
        Assertions.assertTrue(beforeRegistration.isEmpty());

        fluentTemplate().withHeader(ConsulConstants.CONSUL_ACTION, ConsulAgentActions.REGISTER)
                .withBody(ImmutableRegistration.builder()
                        .id(serviceId)
                        .name("foo")
                        .address("localhost")
                        .port(80)
                        .build())
                .to("direct:consul")
                .request();

        Map<String, Service> afterRegistration = getConsul().agentClient()
                .getServices();
        Assertions.assertEquals(1, afterRegistration.size());
        Assertions.assertNotNull(afterRegistration.get(serviceId));

        fluentTemplate().withHeader(ConsulConstants.CONSUL_ACTION, ConsulAgentActions.DEREGISTER)
                .withHeader(ConsulConstants.CONSUL_SERVICE_ID, serviceId)
                .to("direct:consul")
                .request();

        Map<String, Service> afterDeregistration = getConsul().agentClient()
                .getServices();
        Assertions.assertTrue(afterDeregistration.isEmpty());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:consul").to("consul:agent");

                from("direct:register")
                        .setBody().constant(ImmutableRegistration.builder()
                                .id(serviceId)
                                .name("foo")
                                .address("localhost")
                                .port(80)
                                .build())
                        .setHeader(ConsulConstants.CONSUL_ACTION, constant(ConsulAgentActions.REGISTER))
                        .to("consul:agent?action=REGISTER");
            }
        };
    }
}
