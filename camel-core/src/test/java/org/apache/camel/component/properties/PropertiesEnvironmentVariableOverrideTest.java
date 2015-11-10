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
public class PropertiesEnvironmentVariableOverrideTest extends ContextTestSupport {

    
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testPropertiesComponentCacheDisabled() throws Exception {
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setCache(false);
        
        System.setProperty("cool.end", "mock:override");
        System.setProperty("cool.result", "override");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:cool.end");
                from("direct:foo").to("properties:mock:{{cool.result}}");
            }
        });
        context.start();

        getMockEndpoint("mock:override").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:foo", "Hello Foo");

        System.clearProperty("cool.end");
        System.clearProperty("cool.result");
        
        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        PropertiesComponent pc = new PropertiesComponent();
        pc.setLocations(new String[]{"classpath:org/apache/camel/component/properties/myproperties.properties"});
        context.addComponent("properties", pc);

        return context;
    }

}
