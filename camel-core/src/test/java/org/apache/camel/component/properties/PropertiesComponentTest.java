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
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class PropertiesComponentTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testPropertiesComponent() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:{{cool.end}}");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testPropertiesComponentResult() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:mock:{{cool.result}}");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testPropertiesComponentMockMock() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:{{cool.mock}}:{{cool.mock}}");
            }
        });
        context.start();

        getMockEndpoint("mock:mock").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testPropertiesComponentConcat() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:cool.concat");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testPropertiesComponentLocationOverride() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:{{bar.end}}?locations=org/apache/camel/component/properties/bar.properties");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testPropertiesComponentLocationsOverride() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:bar.end?locations=org/apache/camel/component/properties/bar.properties");
                from("direct:cheese").to("properties:cheese.end?locations=org/apache/camel/component/properties/bar.properties,"
                        + "classpath:org/apache/camel/component/properties/cheese.properties");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:cheese").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:cheese", "Hello Cheese");

        assertMockEndpointsSatisfied();
    }

    public void testPropertiesComponentInvalidKey() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:{{foo.unknown}}");
            }
        });
        try {
            context.start();
            fail("Should throw exception");
        } catch (FailedToCreateRouteException e) {
            ResolveEndpointFailedException cause = assertIsInstanceOf(ResolveEndpointFailedException.class, e.getCause());
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, cause.getCause());
            assertEquals("Property with key [foo.unknown] not found in properties for uri: {{foo.unknown}}", iae.getMessage());
        }
    }

    public void testPropertiesComponentCircularReference() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:cool.a");
            }
        });
        try {
            context.start();
            fail("Should throw exception");
        } catch (FailedToCreateRouteException e) {
            ResolveEndpointFailedException cause = assertIsInstanceOf(ResolveEndpointFailedException.class, e.getCause());
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, cause.getCause());
            assertEquals("Circular reference detected with key [cool.a] in uri {{cool.a}}", iae.getMessage());
        }
    }

    public void testPropertiesComponentCacheDefault() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // properties component can also have {{ }} around but its not needed
                from("direct:start").to("properties:{{cool.end}}");
                from("direct:foo").to("properties:mock:{{cool.result}}");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:foo", "Hello Foo");

        assertMockEndpointsSatisfied();
    }

    public void testPropertiesComponentCacheDisabled() throws Exception {
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setCache(false);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:cool.end");
                from("direct:foo").to("properties:mock:{{cool.result}}");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:foo", "Hello Foo");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent("properties", new PropertiesComponent("classpath:org/apache/camel/component/properties/myproperties.properties"));
        return context;
    }

}
