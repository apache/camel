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
package org.apache.camel.processor;

import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bar.BarComponent;
import org.apache.camel.component.bar.BarConstants;
import org.apache.camel.support.component.EndpointUriFactorySupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ToDynamicSendDynamicAwareTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("myFactory", new BarEndpointUriFactory());
        return context;
    }

    @Test
    public void testToDynamic() throws Exception {
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello Camel ordered beer", "Hello World ordered wine");
        // the post-processor should remove the header
        getMockEndpoint("mock:bar").allMessages().header(BarConstants.DRINK).isNull();

        template.sendBodyAndHeader("direct:start", "Hello Camel", "drink", "beer");
        template.sendBodyAndHeader("direct:start", "Hello World", "drink", "wine");

        assertMockEndpointsSatisfied();

        // there should only be a bar:order endpoint
        boolean found = context.getEndpointRegistry().containsKey("bar://order");
        assertTrue(found, "There should only be one bar endpoint");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("bar", new BarComponent());

                from("direct:start").toD("bar:order?drink=${header.drink}").to("mock:bar");
            }
        };
    }

    private static class BarEndpointUriFactory extends EndpointUriFactorySupport {

        @Override
        public boolean isEnabled(String scheme) {
            return "bar".equals(scheme);
        }

        @Override
        public String buildUri(String scheme, Map<String, Object> properties, boolean encode) throws URISyntaxException {
            // not in use for this test
            return null;
        }

        @Override
        public Set<String> propertyNames() {
            Set<String> answer = new HashSet<>();
            answer.add("name");
            answer.add("drink");
            return answer;
        }

        @Override
        public Set<String> secretPropertyNames() {
            return null;
        }

        @Override
        public Set<String> multiValuePrefixes() {
            return null;
        }

        @Override
        public boolean isLenientProperties() {
            return false;
        }
    }
}
