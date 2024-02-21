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

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.consul.endpoint.ConsulHealthActions;
import org.apache.camel.test.infra.consul.services.ConsulService;
import org.apache.camel.test.infra.consul.services.ConsulServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.AgentClient;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.model.agent.ImmutableRegistration;
import org.kiwiproject.consul.model.agent.Registration;
import org.kiwiproject.consul.model.health.ServiceHealth;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConsulHealthIT extends CamelTestSupport {
    /*
     NOTE: this one is not registered as extension because it requires a different lifecycle. It
     needs to be started much earlier than usual, so in this test we take care of handling it.
     */
    private ConsulService consulService = ConsulServiceFactory.createService();

    private AgentClient client;
    private List<Registration> registrations;
    private String service;

    public ConsulHealthIT() {
        consulService.initialize();
    }

    @BindToRegistry("consul")
    public ConsulComponent getConsulComponent() {
        ConsulComponent component = new ConsulComponent();
        component.getConfiguration().setUrl(consulService.getConsulUrl());
        return component;
    }

    protected Consul getConsul() {
        return Consul.builder().withUrl(consulService.getConsulUrl()).build();
    }

    @Override
    public void doPreSetup() throws Exception {
        super.doPreSetup();

        SecureRandom random = new SecureRandom();

        this.service = UUID.randomUUID().toString();
        this.client = getConsul().agentClient();
        this.registrations = Arrays
                .asList(ImmutableRegistration.builder().id(UUID.randomUUID().toString()).name(this.service).address("127.0.0.1")
                        .port(random.nextInt(10000)).build(),
                        ImmutableRegistration.builder().id(UUID.randomUUID().toString()).name(this.service).address("127.0.0.1")
                                .port(random.nextInt(10000)).build());

        this.registrations.forEach(client::register);
    }

    @Override
    public void doPostTearDown() throws Exception {
        super.doPostTearDown();

        registrations.forEach(r -> client.deregister(r.getId()));
    }

    // *************************************************************************
    // Test
    // *************************************************************************

    @Test
    public void testServiceInstance() {
        List<ServiceHealth> ref = getConsul().healthClient().getAllServiceInstances(this.service).getResponse();
        List<ServiceHealth> res
                = fluentTemplate().withHeader(ConsulConstants.CONSUL_ACTION, ConsulHealthActions.SERVICE_INSTANCES)
                        .withHeader(ConsulConstants.CONSUL_SERVICE, this.service).to("direct:consul").request(List.class);

        Assertions.assertEquals(2, ref.size());
        Assertions.assertEquals(2, res.size());
        Assertions.assertEquals(ref, res);

        assertTrue(registrations.stream()
                .anyMatch(r -> r.getPort().isPresent() && r.getPort().get() == res.get(0).getService().getPort()
                        && r.getId().equalsIgnoreCase(res.get(0).getService().getId())));
        assertTrue(registrations.stream()
                .anyMatch(r -> r.getPort().isPresent() && r.getPort().get() == res.get(1).getService().getPort()
                        && r.getId().equalsIgnoreCase(res.get(1).getService().getId())));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:consul").to("consul:health");
            }
        };
    }
}
