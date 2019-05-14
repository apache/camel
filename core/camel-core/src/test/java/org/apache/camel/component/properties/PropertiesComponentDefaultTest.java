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

import java.io.FileNotFoundException;
import java.io.IOError;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class PropertiesComponentDefaultTest extends ContextTestSupport {

    @Test
    public void testPropertiesComponentDefault() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:bar.end?locations=org/apache/camel/component/properties/bar.properties");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPropertiesComponentDefaultNoFileFound() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:bar.end?locations=org/apache/camel/component/properties/unknown.properties");
            }
        });
        try {
            context.start();
            fail("Should throw exception");
        } catch (Exception e) {
            ResolveEndpointFailedException cause = assertIsInstanceOf(ResolveEndpointFailedException.class, e.getCause());
            RuntimeCamelException rce = assertIsInstanceOf(RuntimeCamelException.class, cause.getCause());
            FileNotFoundException fnfe = assertIsInstanceOf(FileNotFoundException.class, rce.getCause());
            assertEquals("Properties file org/apache/camel/component/properties/unknown.properties not found in classpath", fnfe.getMessage());
        }
    }

    @Test
    public void testIgnoreMissingPropertyFilesOnClasspath() throws Exception {
        System.setProperty("bar.end", "mock:bar");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:bar.end?locations=org/apache/camel/component/properties/unknown.properties&ignoreMissingLocation=true");
            }
        });
        context.start();
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testIgnoreMissingPropertyFilesFromRegistry() throws Exception {
        System.setProperty("bar.end", "mock:bar");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:bar.end?locations=ref:unknown.properties&ignoreMissingLocation=true");
            }
        });
        context.start();
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testIgnoreMissingPropertyFilesFromFilePath() throws Exception {
        System.setProperty("bar.end", "mock:bar");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:bar.end?locations=file:unknown.properties&ignoreMissingLocation=true");
            }
        });
        context.start();
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testIgnoreMissingPropertySystemPropertyOnClasspath() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:bar.end?locations=${my.home}/unknown.properties,org/apache/camel/component/properties/bar.properties"
                        + "&ignoreMissingLocation=true");
            }
        });
        context.start();
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNotIgnoreMissingPropertySystemPropertyOnClasspath() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("properties:bar.end?locations=${my.home}/unknown.properties,org/apache/camel/component/properties/bar.properties"
                        + "&ignoreMissingLocation=false");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (Exception e) {
            assertEquals("Cannot find JVM system property with key: my.home", e.getCause().getCause().getMessage());
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}