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
package org.apache.camel.component.ribbon.cloud;

import java.util.Optional;

import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.PropertyInject;
import org.apache.camel.Route;
import org.apache.camel.impl.cloud.DefaultServiceCallProcessor;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DirtiesContext
public abstract class SpringRibbonServiceCallRouteTest extends CamelSpringTestSupport {

    @PropertyInject("firstPort")
    private String firstPort;

    @PropertyInject("secondPort")
    private String secondPort;

    @Test
    public void testServiceCall() throws Exception {
        getMockEndpoint("mock:{{firstPort}}").expectedMessageCount(1);
        getMockEndpoint("mock:{{secondPort}}").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(2);

        String out = template.requestBody("direct:start", null, String.class);
        String out2 = template.requestBody("direct:start", null, String.class);
        assertEquals(secondPort, out);
        assertEquals(firstPort, out2);

        assertMockEndpointsSatisfied();
    }

    // ************************************
    // Helpers
    // ************************************

    protected DefaultServiceCallProcessor findServiceCallProcessor() {
        Route route = context().getRoute("scall");

        assertNotNull(route, "ServiceCall Route should be present");

        return findServiceCallProcessor(route.navigate())
                .orElseThrow(() -> new IllegalStateException("Unable to find a ServiceCallProcessor"));
    }

    protected Optional<DefaultServiceCallProcessor> findServiceCallProcessor(Navigate<Processor> navigate) {
        for (Processor processor : navigate.next()) {
            if (processor instanceof DefaultServiceCallProcessor) {
                return Optional.ofNullable((DefaultServiceCallProcessor) processor);
            }

            if (processor instanceof Navigate) {
                return findServiceCallProcessor((Navigate<Processor>) processor);
            }
        }

        return Optional.empty();
    }
}
