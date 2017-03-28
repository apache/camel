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

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

public class PropertiesComponentDisableDefaultsTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testDisableDefaultValueResolution() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .transform().simple("{{p:message}}")
                    .to("mock:{{p:mockend}}");
            }
        });

        getMockEndpoint("mock:end").expectedMessageCount(1);
        getMockEndpoint("mock:end").expectedBodiesReceived("my message");

        context.start();

        template.sendBody("direct:start", null);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        Properties props = new Properties();
        props.put("p:mockend", "end");
        props.put("p:message", "my message");

        PropertiesComponent component = new PropertiesComponent();
        component.setDefaultFallbackEnabled(false);
        component.setInitialProperties(props);
        context.addComponent("properties", component);

        return context;
    }
}