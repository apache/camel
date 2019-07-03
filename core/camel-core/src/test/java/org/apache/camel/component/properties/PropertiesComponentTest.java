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
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class PropertiesComponentTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
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

    @Test
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
    
    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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
        } catch (Exception e) {
            ResolveEndpointFailedException cause = assertIsInstanceOf(ResolveEndpointFailedException.class, e.getCause());
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, cause.getCause());
            assertEquals("Property with key [foo.unknown] not found in properties from text: {{foo.unknown}}", iae.getMessage());
        }
    }

    @Test
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
        } catch (Exception e) {
            ResolveEndpointFailedException cause = assertIsInstanceOf(ResolveEndpointFailedException.class, e.getCause());
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, cause.getCause());
            assertEquals("Circular reference detected with key [cool.a] from text: {{cool.a}}", iae.getMessage());
        }
    }

    @Test
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

    @Test
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
    
    @Test
    public void testCache() throws Exception {
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        assertTrue(pc.isCache());
        assertNotNull(pc);

        for (int i = 0; i < 2000; i++) {
            String uri = pc.parseUri("{{cool.mock}}:" + i);
            assertEquals("mock:" + i, uri);
        }
    }

    @Test
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

    @Test
    public void testQuotedPrefix() throws Exception {
        assertEquals("mock", context.resolvePropertyPlaceholders("{{cool.mock}}"));
        assertEquals("'{{' + something + '}}'", context.resolvePropertyPlaceholders("'{{' + something + '}}'"));
        assertEquals("\"{{\" + something + \"}}\"", context.resolvePropertyPlaceholders("\"{{\" + something + \"}}\""));
        assertEquals("mock'", context.resolvePropertyPlaceholders("{{cool.mock}}'"));
        assertEquals("mock\"", context.resolvePropertyPlaceholders("{{cool.mock}}\""));
        assertEquals("'mock", context.resolvePropertyPlaceholders("'{{cool.mock}}"));
        assertEquals("\"mock", context.resolvePropertyPlaceholders("\"{{cool.mock}}"));
    }

    @Test
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

    @Test
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

    @Test
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
        } catch (Exception e) {
            assertEquals("Property with key [beer] not found in properties from text: mock:{{beer}}", e.getCause().getMessage());
        }

        System.clearProperty("cool.result");
        System.clearProperty("beer");
    }

    @Test
    public void testPropertiesComponentEnvOverride() throws Exception {
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setEnvironmentVariableMode(PropertiesComponent.ENVIRONMENT_VARIABLES_MODE_OVERRIDE);
        pc.setLocation("org/apache/camel/component/properties/env.properties");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").to("mock:{{FOO_SERVICE_HOST}}");
            }
        });
        context.start();

        getMockEndpoint("mock:hello").expectedMessageCount(0);
        getMockEndpoint("mock:myserver").expectedMessageCount(1);

        template.sendBody("direct:foo", "Hello Foo");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPropertiesComponentEnvOverrideIfDash() throws Exception {
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setEnvironmentVariableMode(PropertiesComponent.ENVIRONMENT_VARIABLES_MODE_OVERRIDE);
        pc.setLocation("org/apache/camel/component/properties/env.properties");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // will fallback and lookup as FOO_SERVICE_HOST
                from("direct:foo").to("mock:{{FOO-SERVICE_host}}");
            }
        });
        context.start();

        getMockEndpoint("mock:hello").expectedMessageCount(0);
        getMockEndpoint("mock:myserver").expectedMessageCount(1);

        template.sendBody("direct:foo", "Hello Foo");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPropertiesComponentEnvFallback() throws Exception {
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setEnvironmentVariableMode(PropertiesComponent.ENVIRONMENT_VARIABLES_MODE_FALLBACK);
        pc.setLocation("org/apache/camel/component/properties/env.properties");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").to("mock:{{FOO_SERVICE_PORT}}");
            }
        });
        context.start();

        getMockEndpoint("mock:8081").expectedMessageCount(1);
        getMockEndpoint("mock:hello").expectedMessageCount(0);
        getMockEndpoint("mock:myserver").expectedMessageCount(0);

        template.sendBody("direct:foo", "Hello Foo");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPropertiesComponentEnvNever() throws Exception {
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setEnvironmentVariableMode(PropertiesComponent.ENVIRONMENT_VARIABLES_MODE_NEVER);
        pc.setLocation("org/apache/camel/component/properties/env.properties");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").to("mock:{{UNKNOWN}}");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (Exception e) {
            assertEquals("Property with key [UNKNOWN] not found in properties from text: mock:{{UNKNOWN}}", e.getCause().getMessage());
        }
    }

    @Test
    public void testPropertiesComponentEnvFallbackJvmOverride() throws Exception {
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setEnvironmentVariableMode(PropertiesComponent.ENVIRONMENT_VARIABLES_MODE_FALLBACK);
        pc.setLocation("org/apache/camel/component/properties/env.properties");

        // lets override the OS environment variable by setting a JVM system property
        System.setProperty("FOO_SERVICE_PORT", "hello");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").to("mock:{{FOO_SERVICE_PORT}}");
            }
        });
        context.start();

        getMockEndpoint("mock:8081").expectedMessageCount(0);
        getMockEndpoint("mock:hello").expectedMessageCount(1);
        getMockEndpoint("mock:myserver").expectedMessageCount(0);

        template.sendBody("direct:foo", "Hello Foo");

        assertMockEndpointsSatisfied();

        System.clearProperty("FOO_SERVICE_PORT");
    }


    @Test
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
        PropertiesComponent pc = new PropertiesComponent();
        pc.setLocation("classpath:org/apache/camel/component/properties/myproperties.properties");
        context.addComponent("properties", pc);
        return context;
    }

}
