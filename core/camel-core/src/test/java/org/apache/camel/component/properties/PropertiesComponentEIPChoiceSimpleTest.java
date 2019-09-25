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
package org.apache.camel.component.properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class PropertiesComponentEIPChoiceSimpleTest extends ContextTestSupport {

    @Test
    public void testChoice() throws Exception {
        getMockEndpoint("mock:camel").expectedBodiesReceived("Hello Camel", "Hi Camel");
        getMockEndpoint("mock:other").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", context.resolvePropertyPlaceholders("Hello {{cool.name}}"));
        template.sendBody("direct:start", context.resolvePropertyPlaceholders("Hi {{cool.name}}"));
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").choice().when(simple("${body} contains '{{cool.name}}'")).to("mock:camel").otherwise().to("mock:other");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("classpath:org/apache/camel/component/properties/myproperties.properties");
        return context;
    }

}
