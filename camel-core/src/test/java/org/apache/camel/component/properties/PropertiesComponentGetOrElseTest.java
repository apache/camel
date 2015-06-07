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

/**
 * @version 
 */
public class PropertiesComponentGetOrElseTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testPropertiesComponentFoundKey() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:{{cool.end:mock:wrong}}");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testPropertiesComponentUseDefaultValue() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:{{unknown:mock:result}}");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testPropertiesComponentSimpleLanguage() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .transform().simple("Hi ${body} do you think ${properties:cool.name} rocks?");
            }
        });
        context.start();

        String reply = template.requestBody("direct:start", "Claus", String.class);
        assertEquals("Hi Claus do you think Camel rocks?", reply);
    }

    public void testPropertiesComponentSimpleLanguageUsingDefaultValue() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .transform().simple("Hi ${body} do you think ${properties:unknown:Beer} rocks?");
            }
        });
        context.start();

        String reply = template.requestBody("direct:start", "Claus", String.class);
        assertEquals("Hi Claus do you think Beer rocks?", reply);
    }


    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent("properties", new PropertiesComponent("classpath:org/apache/camel/component/properties/myproperties.properties"));
        return context;
    }

}
