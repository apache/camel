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
package org.apache.camel.example.cdi.properties;

import javax.enterprise.event.Observes;

import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.cdi.Uri;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.management.event.CamelContextStartingEvent;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.cdi.CamelCdiRunner;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(CamelCdiRunner.class)
public class CdiPropertiesTest {

    static void advice(@Observes CamelContextStartingEvent event,
                       ModelCamelContext context) throws Exception {
        // Add a mock endpoint to the end of the route
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                weaveAddLast().to("mock:outbound");
            }
        });
    }

    @Test
    public void testRouteMessage(@Uri("mock:outbound") MockEndpoint outbound) {
        assertThat("Exchange count is incorrect!",
            outbound.getExchanges(),
            hasSize(1));
        assertThat("Exchange body is incorrect!",
            outbound.getExchanges().get(0).getIn().getBody(String.class),
            is(equalTo("Hello")));
    }

    @Test
    public void testProperties(@ConfigProperty(name = "destination") String destination,
                               @ConfigProperty(name = "message") String message) {
        assertThat("Property 'destination' value is incorrect!",
            destination,
            is(equalTo("direct:hello")));
        assertThat("Property 'message' value is incorrect!",
            message,
            is(equalTo("Hello")));
    }
}