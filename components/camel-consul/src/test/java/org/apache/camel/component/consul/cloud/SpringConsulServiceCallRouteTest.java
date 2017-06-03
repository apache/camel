/**
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

package org.apache.camel.component.consul.cloud;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.orbitz.consul.AgentClient;
import com.orbitz.consul.Consul;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.impl.cloud.DefaultServiceCallProcessor;
import org.apache.camel.processor.ChoiceProcessor;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Assert;
import org.junit.Test;

public abstract class SpringConsulServiceCallRouteTest extends CamelSpringTestSupport {
    private AgentClient client;
    private List<Registration> registrations;

    // *************************************************************************
    // Setup / tear down
    // *************************************************************************

    @Override
    public void doPreSetup() throws Exception {
        this.client = Consul.builder().build().agentClient();
        this.registrations = Arrays.asList(
            ImmutableRegistration.builder()
                .id("service-1-1")
                .name("http-service-1")
                .address("127.0.0.1")
                .port(9011)
                .build(),
            ImmutableRegistration.builder()
                .id("service-1-2")
                .name("http-service-1")
                .address("127.0.0.1")
                .port(9012)
                .build(),
            ImmutableRegistration.builder()
                .id("service-1-3")
                .name("http-service-1")
                .address("127.0.0.1")
                .port(9013)
                .build(),
            ImmutableRegistration.builder()
                .id("service-2-1")
                .name("http-service-2")
                .address("127.0.0.1")
                .port(9021)
                .build(),
            ImmutableRegistration.builder()
                .id("service-2-2")
                .name("http-service-2")
                .address("127.0.0.1")
                .port(9022)
                .build(),
            ImmutableRegistration.builder()
                .id("service-2-3")
                .name("http-service-2")
                .address("127.0.0.1")
                .port(9023)
                .build()
        );

        this.registrations.forEach(client::register);
        super.doPreSetup();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        registrations.forEach(r -> client.deregister(r.getId()));
    }

    // *************************************************************************
    // Test
    // *************************************************************************

    @Test
    public void testServiceCall() throws Exception {
        getMockEndpoint("mock:result-1").expectedMessageCount(2);
        getMockEndpoint("mock:result-1").expectedBodiesReceivedInAnyOrder("service-1 9012", "service-1 9013");
        getMockEndpoint("mock:result-2").expectedMessageCount(2);
        getMockEndpoint("mock:result-2").expectedBodiesReceivedInAnyOrder("service-2 9021", "service-2 9023");

        template.sendBody("direct:start", "service-1");
        template.sendBody("direct:start", "service-1");
        template.sendBody("direct:start", "service-2");
        template.sendBody("direct:start", "service-2");

        assertMockEndpointsSatisfied();
    }

    // ************************************
    // Helpers
    // ************************************

    protected List<DefaultServiceCallProcessor> findServiceCallProcessors() {
        Route route = context().getRoute("scall");

        Assert.assertNotNull("ServiceCall Route should be present", route);

        return findServiceCallProcessors(new ArrayList<>(), route.navigate());
    }

    protected List<DefaultServiceCallProcessor> findServiceCallProcessors(List<DefaultServiceCallProcessor> processors, Navigate<Processor> navigate) {
        for (Processor processor : navigate.next()) {
            if (processor instanceof DefaultServiceCallProcessor) {
                processors.add((DefaultServiceCallProcessor)processor);
            }
            if (processor instanceof ChoiceProcessor) {
                for (FilterProcessor filter : ((ChoiceProcessor) processor).getFilters()) {
                    findServiceCallProcessors(processors, filter);
                }
            } else if (processor instanceof Navigate) {
                return findServiceCallProcessors(processors, (Navigate<Processor>)processor);
            }
        }

        return processors;
    }
}
