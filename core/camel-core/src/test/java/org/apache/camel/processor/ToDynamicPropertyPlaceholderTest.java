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

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class ToDynamicPropertyPlaceholderTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        Properties prop = new Properties();
        prop.setProperty("foo", "${header.myHeader}");
        context.getPropertiesComponent().setInitialProperties(prop);

        return context;
    }

    @Test
    public void testToDynamic() throws Exception {
        getMockEndpoint("mock:cheese").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:cake").expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("direct:start", "Hello Camel", "myHeader", "cheese");
        template.sendBodyAndHeader("direct:start", "Hello World", "myHeader", "cake");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testToDynamicNoHeader() throws Exception {
        try {
            template.sendBody("direct:start", "Hello Camel");
            fail("Should throw exception");
        } catch (Exception e) {
            ResolveEndpointFailedException ref = assertIsInstanceOf(ResolveEndpointFailedException.class, e.getCause());
            assertEquals("mock:", ref.getUri());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").toD("mock:{{foo}}");
            }
        };
    }
}
