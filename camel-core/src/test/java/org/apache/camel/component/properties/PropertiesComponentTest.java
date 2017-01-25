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

    public void testPropertiesComponentTwo() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:{{cool.end}}");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();
    }
    
    public void testPropertiesComponentCustomTokens() throws Exception {
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setPrefixToken("[[");
        pc.setSuffixToken("]]");
        
        assertEquals("[[", pc.getPrefixToken());
        assertEquals("]]", pc.getSuffixToken());
        
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:[[cool.end]]");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
        
        pc.setPrefixToken(null);
        pc.setSuffixToken(null);
        
        assertEquals(PropertiesComponent.DEFAULT_PREFIX_TOKEN, pc.getPrefixToken());
        assertEquals(PropertiesComponent.DEFAULT_SUFFIX_TOKEN, pc.getSuffixToken());
    }

    public void testPropertiesComponentTemplate() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:cool").to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("{{cool.start}}", "Hello World");
        template.sendBody("{{cool.start}}", "Bye World");

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
            assertEquals("Property with key [foo.unknown] not found in properties from text: {{foo.unknown}}", iae.getMessage());
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
            assertEquals("Circular reference detected with key [cool.a] from text: {{cool.a}}", iae.getMessage());
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
    
    public void testPropertiesComponentPropertyPrefix() throws Exception {
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setPropertyPrefix("cool.");
        
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:end");
                from("direct:foo").to("properties:mock:{{result}}");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:foo", "Hello Foo");

        assertMockEndpointsSatisfied();
    }

    public void testPropertiesComponentParameterizedPropertyPrefix() throws Exception {
        System.setProperty("myPrefix", "cool");
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setPropertyPrefix("${myPrefix}.");
        pc.setPropertySuffix(".xx");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:end");
                from("direct:foo").to("properties:mock:{{result}}");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:foo", "Hello Foo");

        assertMockEndpointsSatisfied();

        System.clearProperty("myPrefix");
    }

    public void testPropertiesComponentPropertyPrefixFallbackDefault() throws Exception {
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setPropertyPrefix("cool.");
        
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:cool.end");
                from("direct:foo").to("properties:mock:{{result}}");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:foo", "Hello Foo");

        assertMockEndpointsSatisfied();
    }
    
    public void testPropertiesComponentPropertyPrefixFallbackDefaultNotFound() throws Exception {
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setPropertyPrefix("cool.");
        
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:doesnotexist");
            }
        });
        
        try {
            context.start();
            
            fail("Should throw exception");
        } catch (FailedToCreateRouteException e) {
            ResolveEndpointFailedException cause = assertIsInstanceOf(ResolveEndpointFailedException.class, e.getCause());
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, cause.getCause());
            assertEquals("Property with key [cool.doesnotexist] (and original key [doesnotexist]) not found in properties from text: {{doesnotexist}}", iae.getMessage());
        }
    }
    
    public void testPropertiesComponentPropertyPrefixFallbackFalse() throws Exception {
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setPropertyPrefix("cool.");
        pc.setFallbackToUnaugmentedProperty(false);
        
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:cool.end");
                from("direct:foo").to("properties:mock:{{result}}");
            }
        });
        
        try {
            context.start();
            
            fail("Should throw exception");
        } catch (FailedToCreateRouteException e) {
            ResolveEndpointFailedException cause = assertIsInstanceOf(ResolveEndpointFailedException.class, e.getCause());
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, cause.getCause());
            assertEquals("Property with key [cool.cool.end] not found in properties from text: {{cool.end}}", iae.getMessage());
        }
    }
    
    public void testPropertiesComponentPropertySuffix() throws Exception {
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setPropertySuffix(".end");
        
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:cool");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }
    
    public void testPropertiesComponentPropertySuffixFallbackDefault() throws Exception {
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setPropertySuffix(".end");
        
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:cool.end");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }
    
    public void testPropertiesComponentPropertySuffixFallbackFalse() throws Exception {
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setPropertySuffix(".end");
        pc.setFallbackToUnaugmentedProperty(false);
        
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:cool.end");
            }
        });
        
        try {
            context.start();
            
            fail("Should throw exception");
        } catch (FailedToCreateRouteException e) {
            ResolveEndpointFailedException cause = assertIsInstanceOf(ResolveEndpointFailedException.class, e.getCause());
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, cause.getCause());
            assertEquals("Property with key [cool.end.end] not found in properties from text: {{cool.end}}", iae.getMessage());
        }
    }

    public void testJvmSystemPropertyNotFound() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start").to("properties:xxx?locations=foo/${xxx}");
                }
            });
            context.start();
            fail("Should thrown an exception");
        } catch (FailedToCreateRouteException e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause().getCause());
            assertEquals("Cannot find JVM system property with key: xxx", cause.getMessage());
        }
    }

    public void testCache() throws Exception {
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        assertTrue(pc.isCache());
        assertNotNull(pc);

        for (int i = 0; i < 2000; i++) {
            String uri = pc.parseUri("{{cool.mock}}:" + i);
            assertEquals("mock:" + i, uri);
        }
    }

    public void testCacheRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .setBody(simple("${properties:cool.mock}${body}"))
                    .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(2000);

        for (int i = 0; i < 2000; i++) {
            template.sendBody("direct:start", i);
        }

        assertMockEndpointsSatisfied();
    }

    public void testQuotedPrefix() throws Exception {
        assertEquals("mock", context.resolvePropertyPlaceholders("{{cool.mock}}"));
        assertEquals("'{{' + something + '}}'", context.resolvePropertyPlaceholders("'{{' + something + '}}'"));
        assertEquals("\"{{\" + something + \"}}\"", context.resolvePropertyPlaceholders("\"{{\" + something + \"}}\""));
        assertEquals("mock'", context.resolvePropertyPlaceholders("{{cool.mock}}'"));
        assertEquals("mock\"", context.resolvePropertyPlaceholders("{{cool.mock}}\""));
        assertEquals("'mock", context.resolvePropertyPlaceholders("'{{cool.mock}}"));
        assertEquals("\"mock", context.resolvePropertyPlaceholders("\"{{cool.mock}}"));
    }

    public void testPropertiesComponentOverride() throws Exception {
        System.setProperty("cool.result", "bar");
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setSystemPropertiesMode(PropertiesComponent.SYSTEM_PROPERTIES_MODE_OVERRIDE);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").to("mock:{{cool.result}}");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        template.sendBody("direct:foo", "Hello Foo");

        assertMockEndpointsSatisfied();

        System.clearProperty("cool.result");
    }

    public void testPropertiesComponentFallback() throws Exception {
        System.setProperty("cool.result", "bar");
        System.setProperty("beer", "Carlsberg");
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setSystemPropertiesMode(PropertiesComponent.SYSTEM_PROPERTIES_MODE_FALLBACK);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").to("mock:{{beer}}").to("mock:{{cool.result}}");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(0);
        getMockEndpoint("mock:Carlsberg").expectedMessageCount(1);

        template.sendBody("direct:foo", "Hello Foo");

        assertMockEndpointsSatisfied();

        System.clearProperty("cool.result");
        System.clearProperty("beer");
    }

    public void testPropertiesComponentNever() throws Exception {
        System.setProperty("cool.result", "bar");
        System.setProperty("beer", "Carlsberg");
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setSystemPropertiesMode(PropertiesComponent.SYSTEM_PROPERTIES_MODE_NEVER);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").to("mock:{{beer}}").to("mock:{{cool.result}}");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (FailedToCreateRouteException e) {
            assertEquals("Property with key [beer] not found in properties from text: mock:{{beer}}", e.getCause().getMessage());
        }

        System.clearProperty("cool.result");
        System.clearProperty("beer");
    }

    public void testCamelProperties() throws Exception {
        context.getGlobalOptions().put("foo", "Hello {{cool.name}}");
        context.getGlobalOptions().put("bar", "cool.name");

        context.start();

        assertEquals("Hello Camel", context.getGlobalOptions().get("foo"));
        assertEquals("cool.name", context.getGlobalOptions().get("bar"));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent("properties", new PropertiesComponent("classpath:org/apache/camel/component/properties/myproperties.properties"));
        return context;
    }

}
