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
package org.apache.camel.builder.endpoint;

import java.util.Properties;

import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OptionalPropertyPlaceholderTest extends BaseEndpointDslTest {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testQueryOptionalNotPresent() throws Exception {
        context.addRoutes(new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(direct("start")).to(mock("result").retainFirst("{{?maxKeep}}").failFast(false));
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(2, getMockEndpoint("mock:result").getReceivedExchanges().size());
    }

    @Test
    public void testQueryOptionalPresent() throws Exception {
        Properties prop = new Properties();
        prop.put("maxKeep", "1");
        context.getPropertiesComponent().setInitialProperties(prop);

        context.addRoutes(new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(direct("start")).to(mock("result").retainFirst("{{?maxKeep}}").failFast(false));
            }
        });
        context.start();

        getMockEndpoint("mock:result?retainFirst=1").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(1, getMockEndpoint("mock:result?retainFirst=1").getReceivedExchanges().size());
    }

    @Test
    public void testPathOptionalNotPresent() throws Exception {
        context.addRoutes(new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(direct("start")).to(mock("res{{?whereTo}}ult").failFast(false));
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(2, getMockEndpoint("mock:result").getReceivedExchanges().size());
    }

    @Test
    public void testPathOptionalPresent() throws Exception {
        Properties prop = new Properties();
        prop.put("whereTo", "result");
        context.getPropertiesComponent().setInitialProperties(prop);

        context.addRoutes(new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(direct("start")).to(mock("{{?whereTo}}").failFast(false));
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(2, getMockEndpoint("mock:result").getReceivedExchanges().size());
    }

}
