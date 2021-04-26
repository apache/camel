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
package org.apache.camel.impl;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

/**
 * Unit test of the string data format.
 */
public class DataFormatPropertyPlaceholderTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        Properties prop = new Properties();
        prop.put("myDataformat", "reverse");
        context.getPropertiesComponent().setInitialProperties(prop);
        return context;
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry registry = super.createRegistry();
        registry.bind("reverse", new RefDataFormatTest.MyReverseDataFormat());
        return registry;
    }

    @Test
    public void testMarshalRef() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("CBA");

        template.sendBody("direct:a", "ABC");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUnmarshalRef() throws Exception {
        getMockEndpoint("mock:b").expectedBodiesReceived("ABC");

        template.sendBody("direct:b", "CBA");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").marshal("{{myDataformat}}").to("mock:a");

                from("direct:b").unmarshal("{{myDataformat}}").to("mock:b");
            }
        };
    }

}
