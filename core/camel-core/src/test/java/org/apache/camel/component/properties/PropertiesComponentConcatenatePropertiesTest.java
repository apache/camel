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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PropertiesComponentConcatenatePropertiesTest extends ContextTestSupport {
    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("classpath:org/apache/camel/component/properties/concatenation.properties");
        return context;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        System.setProperty("environment", "junit");
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        System.clearProperty("environment");
        super.tearDown();
    }

    @Test
    public void testConcatPropertiesComponentDefault() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").setBody(simple("{{concat.property}}")).to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("file:dirname");

        template.sendBody("direct:start", "Test");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testWithoutConcatPropertiesComponentDefault() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").setBody(simple("{{property.complete}}")).to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("file:dirname");

        template.sendBody("direct:start", "Test");

        assertMockEndpointsSatisfied();
    }
}
