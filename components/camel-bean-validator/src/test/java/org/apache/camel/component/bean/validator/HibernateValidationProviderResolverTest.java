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
package org.apache.camel.component.bean.validator;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class HibernateValidationProviderResolverTest extends CamelTestSupport {

    // Routing fixtures

    @EndpointInject(uri = "mock:test")
    MockEndpoint mockEndpoint;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(BeanValidationException.class).to(mockEndpoint);

                from("direct:test").
                    to("bean-validator://ValidationProviderResolverTest?validationProviderResolver=#myValidationProviderResolver");
            }
        };
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("myValidationProviderResolver", new HibernateValidationProviderResolver());
        return registry;
    }

    // Tests

    @Test
    public void shouldResolveHibernateValidationProviderResolver() throws InterruptedException {
        // Given
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.message(0).body().isInstanceOf(CarWithAnnotations.class);
        CarWithAnnotations carWithNullFields = new CarWithAnnotations(null, null);

        // When
        sendBody("direct:test", carWithNullFields);

        // Then
        assertMockEndpointsSatisfied();
    }

}
