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

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The local properties (such as the parameters of a route template when creating a route) are only for the current
 * thread.
 */
public class PropertiesComponentLocalPropertiesThreadTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testLocalPropertiesPerThread() throws Exception {
        context.getPropertiesComponent().addInitialProperty("name", "global");
        context.start();

        Properties local = new Properties();
        local.setProperty("name", "local");
        PropertiesComponent pc = (PropertiesComponent) context.getPropertiesComponent();
        pc.setLocalProperties(local);
        try {
            assertEquals("local", context.resolvePropertyPlaceholders("{{name}}"));
            // another thread does not see the local properties of this thread
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                String other = executor.submit(() -> context.resolvePropertyPlaceholders("{{name}}")).get();
                assertEquals("global", other);
            } finally {
                executor.shutdownNow();
            }
        } finally {
            pc.setLocalProperties(null);
        }
        assertNull(pc.getLocalProperties());
        assertEquals("global", context.resolvePropertyPlaceholders("{{name}}"));
    }

    @Test
    public void testAddRouteKeepsOuterLocalProperties() throws Exception {
        context.start();

        Properties outer = new Properties();
        outer.setProperty("name", "outer");
        PropertiesComponent pc = (PropertiesComponent) context.getPropertiesComponent();
        pc.setLocalProperties(outer);
        try {
            // adding a route that is not from a route template must not remove the local properties
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").to("mock:result");
                }
            });
            assertSame(outer, pc.getLocalProperties());
        } finally {
            pc.setLocalProperties(null);
        }
    }
}
