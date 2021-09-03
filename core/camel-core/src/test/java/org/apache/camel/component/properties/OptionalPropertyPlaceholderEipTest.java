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

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class OptionalPropertyPlaceholderEipTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testQueryOptionalNotPresent() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .split(body()).delimiter("{{?myDelim}}")
                        .to("mock:line");
            }
        });
        context.start();

        getMockEndpoint("mock:line").expectedMessageCount(2);
        template.sendBody("direct:start", "A,B");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testQueryOptionalPresent() throws Exception {
        Properties prop = new Properties();
        prop.put("myDelim", ";");
        context.getPropertiesComponent().setInitialProperties(prop);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .split(body()).delimiter("{{?myDelim}}")
                        .to("mock:line");
            }
        });
        context.start();

        getMockEndpoint("mock:line").expectedMessageCount(1);
        template.sendBody("direct:start", "A,B");
        assertMockEndpointsSatisfied();

        resetMocks();

        getMockEndpoint("mock:line").expectedMessageCount(3);
        template.sendBody("direct:start", "A;B;C");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("classpath:org/apache/camel/component/properties/myproperties.properties");
        return context;
    }

}
