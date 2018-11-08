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
import org.junit.Test;

/**
 * @version 
 */
public class PropertiesComponentOverridePropertiesTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testPropertiesComponentEndpoint() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("{{hey}}")
                    .to("mock:{{cool.result}}");
            }
        });
        context.start();

        getMockEndpoint("mock:extra").expectedMessageCount(1);
        getMockEndpoint("mock:hey").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        PropertiesComponent pc = new PropertiesComponent();
        pc.setLocation("classpath:org/apache/camel/component/properties/myproperties.properties");
        context.addComponent("properties", pc);

        Properties extra = new Properties();
        extra.put("cool.result", "extra");
        extra.put("hey", "mock:hey");
        pc.setOverrideProperties(extra);

        return context;
    }

}