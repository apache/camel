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
import org.apache.camel.NonManagedService;
import org.apache.camel.StaticService;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesReload;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.OrderedLocationProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that a context reload re-applies the Camel options whose value is a property placeholder, which is what allows
 * a rotated secret to reach a component option that was resolved once at bootstrap.
 */
public class ContextReloadComponentPropertiesTest extends ContextTestSupport {

    private final MyPropertiesReload reload = new MyPropertiesReload();

    @Test
    public void testPlaceholderOptionsAreReApplied() {
        ContextReloadStrategy crs = context.hasService(ContextReloadStrategy.class);
        assertThat(crs).isNotNull();
        crs.onReload("ContextReloadComponentPropertiesTest");

        assertThat(reload.getCounter()).isOne();
        Properties reloaded = reload.getProperties();
        assertThat(reloaded).isNotNull();

        // only camel options whose value is a placeholder are re-applied, as they are the only ones
        // whose resolved value can change while the raw configuration stays the same
        assertThat(reloaded.stringPropertyNames())
                .containsExactlyInAnyOrder("camel.component.dummy.secret", "camel.component.dummy.token");
        assertThat(reloaded.getProperty("camel.component.dummy.secret")).isEqualTo("{{hello}}");
    }

    @Test
    public void testPropertiesAreOrderedLocationProperties() {
        ContextReloadStrategy crs = context.hasService(ContextReloadStrategy.class);
        crs.onReload("ContextReloadComponentPropertiesTest");

        // MainPropertiesReload only acts on OrderedLocationProperties, so the contract must be kept
        assertThat(reload.getProperties()).isInstanceOf(OrderedLocationProperties.class);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        Properties prop = new Properties();
        prop.setProperty("hello", "Hello World");
        // placeholder based camel options: must be re-applied
        prop.setProperty("camel.component.dummy.secret", "{{hello}}");
        prop.setProperty("camel.component.dummy.token", "prefix-{{hello}}-suffix");
        // plain camel option: cannot change, must be filtered out
        prop.setProperty("camel.component.dummy.plain", "no-placeholder");
        // placeholder, but not a camel option: not Camel configuration, must be filtered out
        prop.setProperty("myapp.password", "{{hello}}");

        PropertiesComponent pc = context.getPropertiesComponent();
        pc.setInitialProperties(prop);

        context.addService(reload);
        context.addService(new DefaultContextReloadStrategy());

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("mock:result");
            }
        };
    }

    private static class MyPropertiesReload extends ServiceSupport
            implements PropertiesReload, StaticService, NonManagedService {

        private int counter;
        private Properties properties;

        @Override
        public void onReload(String name, Properties properties) {
            this.counter++;
            this.properties = properties;
        }

        int getCounter() {
            return counter;
        }

        Properties getProperties() {
            return properties;
        }
    }
}
