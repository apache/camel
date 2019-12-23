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
package org.apache.camel.issues;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class PropertiesAvailableEverywhereTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        final Properties properties = new Properties();
        properties.put("foo", "bar");

        camelContext.getRegistry().bind("myProp", properties);
        camelContext.getPropertiesComponent().addLocation("ref:myProp");

        return camelContext;
    }

    @Test
    public void testPropertiesInPredicates() throws Exception {
        getMockEndpoint("mock:header-ok").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:choice-ok").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:direct-ok").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:ko").expectedMessageCount(0);

        template.sendBody("direct:header-start", "Hello Camel");
        template.sendBody("direct:choice-start", "Hello Camel");
        template.sendBody("direct:direct-start", "Hello Camel");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Properties in headers
                from("direct:header-start").setHeader("foo", simple("{{foo}}")).choice().when(simple("${header.foo} == 'bar'")).to("mock:header-ok").otherwise().to("mock:ko");

                // Properties in choices
                from("direct:choice-start").choice().when(simple("'{{foo}}' == 'bar'")).to("mock:choice-ok").otherwise().to("mock:ko");

                // Properties in URI
                from("direct:direct-start").to("direct:direct-{{foo}}");
                from("direct:direct-bar").to("mock:direct-ok");
            }
        };
    }
}
