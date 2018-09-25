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
package org.apache.camel.component.properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class PropertiesComponentInterceptFromTest extends ContextTestSupport {

    @Test
    public void testPropertiesComponentInterceptFrom() throws Exception {
        getMockEndpoint("mock:bar").expectedBodiesReceived("World");
        getMockEndpoint("mock:cool").expectedBodiesReceived("Bye Camel");

        template.sendBody("direct:bar", "World");
        template.sendBody("direct:cool", "Camel");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        PropertiesComponent pc = new PropertiesComponent();
        pc.setLocation("classpath:org/apache/camel/component/properties/myproperties.properties");
        context.addComponent("properties", pc);

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptFrom("{{cool.start}}")
                    .transform().simple("Bye ${body}");

                from("direct:cool")
                    .to("mock:cool");

                from("direct:bar")
                    .to("mock:bar");
            }
        };
    }
}