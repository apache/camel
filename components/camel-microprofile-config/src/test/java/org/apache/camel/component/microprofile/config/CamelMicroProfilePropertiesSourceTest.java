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
package org.apache.camel.component.microprofile.config;

import java.util.Properties;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesSource;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.assertj.core.api.Assertions;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.Test;

public class CamelMicroProfilePropertiesSourceTest extends CamelTestSupport {

    private Config config;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        // setup MPC
        Properties prop = new Properties();
        prop.put("start", "direct:start");
        prop.put("hi", "World");
        prop.put("my-mock", "result");

        // create PMC config source and register it so we can use it for testing
        PropertiesConfigSource pcs = new PropertiesConfigSource(prop, "my-smallrye-config");
        config = new SmallRyeConfigBuilder().withSources(pcs).build();

        ConfigProviderResolver.instance().registerConfig(config, CamelMicroProfilePropertiesSourceTest.class.getClassLoader());

        return super.createCamelContext();
    }

    @Override
    public void tearDown() throws Exception {
        ConfigProviderResolver.instance().releaseConfig(config);
        super.tearDown();
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        Properties prop = new Properties();
        prop.put("who", "Camel");

        registry.bind("ps", new PropertiesSource() {
            @Override
            public String getName() {
                return "ps";
            }

            @Override
            public String getProperty(String name) {
                return prop.getProperty(name);
            }
        });
    }

    @Test
    public void testLoadAll() throws Exception {
        PropertiesComponent pc = context.getPropertiesComponent();
        Properties properties = pc.loadProperties();

        Assertions.assertThat(properties.get("start")).isEqualTo("direct:start");
        Assertions.assertThat(properties.get("hi")).isEqualTo("World");
        Assertions.assertThat(properties.get("my-mock")).isEqualTo("result");
    }

    @Test
    public void testLoadFiltered() throws Exception {
        PropertiesComponent pc = context.getPropertiesComponent();
        Properties properties = pc.loadProperties(k -> k.length() > 2);

        Assertions.assertThat(properties).hasSize(2);
        Assertions.assertThat(properties.get("start")).isEqualTo("direct:start");
        Assertions.assertThat(properties.get("my-mock")).isEqualTo("result");
    }

    @Test
    public void testMicroProfileConfig() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World from Camel");

        template.sendBody("direct:start", context.resolvePropertyPlaceholders("Hello {{hi}} from {{who}}"));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("{{start}}")
                        .to("mock:{{my-mock}}");
            }
        };
    }
}
