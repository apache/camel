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
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.apache.camel.Ordered;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.LoadablePropertiesSource;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PropertiesComponentPropertiesSourceTest {
    @Test
    public void testPropertiesSourceFromRegistry() {
        CamelContext context = new DefaultCamelContext();
        context.getRegistry().bind("my-ps-1", new PropertiesPropertiesSource("ps1", "my-key-1", "my-val-1"));
        context.getRegistry().bind("my-ps-2", new PropertiesPropertiesSource("ps2", "my-key-2", "my-val-2"));

        assertThat(context.resolvePropertyPlaceholders("{{my-key-1}}")).isEqualTo("my-val-1");
        assertThat(context.resolvePropertyPlaceholders("{{my-key-2}}")).isEqualTo("my-val-2");
    }

    @Test
    public void testOrderedPropertiesSources() {
        CamelContext context = new DefaultCamelContext();
        context.getRegistry().bind("my-ps-1", new PropertiesPropertiesSource(Ordered.HIGHEST, "ps1", "shared", "v1", "my-key-1", "my-val-1"));
        context.getRegistry().bind("my-ps-2", new PropertiesPropertiesSource(Ordered.LOWEST, "ps2", "shared", "v2", "my-key-2", "my-val-2"));

        Properties properties = context.getPropertiesComponent().loadProperties();

        assertThat(properties.get("my-key-1")).isEqualTo("my-val-1");
        assertThat(properties.get("my-key-2")).isEqualTo("my-val-2");
        assertThat(properties.get("shared")).isEqualTo("v1");
    }

    @Test
    public void testFilteredPropertiesSources() {
        Properties initial = new Properties();
        initial.put("initial-1", "initial-val-1");
        initial.put("initial-2", "initial-val-2");
        initial.put("my-key-2", "initial-val-2");

        CamelContext context = new DefaultCamelContext();
        context.getRegistry().bind("my-ps-1", new PropertiesPropertiesSource("ps1", "my-key-1", "my-val-1"));
        context.getRegistry().bind("my-ps-2", new PropertiesPropertiesSource("ps2", "my-key-2", "my-val-2"));

        context.getPropertiesComponent().setInitialProperties(initial);

        Properties properties = context.getPropertiesComponent().loadProperties(k -> k.endsWith("-2"));

        assertThat(properties).hasSize(2);
        assertThat(properties.get("initial-2")).isEqualTo("initial-val-2");
        assertThat(properties.get("my-key-2")).isEqualTo("my-val-2");
    }

    @Test
    public void testDisablePropertiesSourceDiscovery() {

        CamelContext context = new DefaultCamelContext();
        PropertiesComponent pc = (PropertiesComponent) context.getPropertiesComponent();
        pc.setAutoDiscoverPropertiesSources(false);

        context.getRegistry().bind("my-ps-1", new PropertiesPropertiesSource("ps1", "my-key-1", "my-val-1"));
        context.getRegistry().bind("my-ps-2", new PropertiesPropertiesSource("ps2", "my-key-2", "my-val-2"));

        assertThatThrownBy(() -> context.resolvePropertyPlaceholders("{{my-key-1}}")).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Property with key [my-key-1] not found in properties from text: {{my-key-1}}");

        assertThatThrownBy(() -> context.resolvePropertyPlaceholders("{{my-key-2}}")).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Property with key [my-key-2] not found in properties from text: {{my-key-2}}");
    }

    private static final class PropertiesPropertiesSource extends Properties implements LoadablePropertiesSource, Ordered {
        private final String name;
        private final int order;

        public PropertiesPropertiesSource(String name, String... kv) {
            this(Ordered.LOWEST, name, kv);
        }

        public PropertiesPropertiesSource(int order, String name, String... kv) {
            assert kv.length % 2 == 0;

            this.name = name;
            this.order = order;

            for (int i = 0; i < kv.length; i += 2) {
                super.setProperty(kv[i], kv[i + 1]);
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public Properties loadProperties() {
            return this;
        }

        @Override
        public Properties loadProperties(Predicate<String> filter) {
            Properties props = new Properties();

            for (String name : stringPropertyNames()) {
                if (filter.test(name)) {
                    props.put(name, get(name));
                }
            }

            return props;
        }
    }
}
