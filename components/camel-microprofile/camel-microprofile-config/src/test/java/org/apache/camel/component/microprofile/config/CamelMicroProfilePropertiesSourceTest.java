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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesSource;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.assertj.core.api.Assertions;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CamelMicroProfilePropertiesSourceTest extends CamelTestSupport {

    private Config config;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        // setup MPC
        Properties prop = new Properties();
        prop.put("start", "direct:start");
        prop.put("hi", "World");
        prop.put("my-mock", "result");
        prop.put("empty", "");
        prop.put("%non-active-profile.test-non-active-profile", "should not see this");
        prop.put("%profileA.test-profile-a", "Profile A");
        prop.put("%profileB.test-profile-b", "Profile B");

        // create PMC config source and register it so we can use it for testing
        PropertiesConfigSource pcs = new PropertiesConfigSource(prop, "my-smallrye-config");
        config = new SmallRyeConfigBuilder()
                .withProfile("profileA")
                .withProfile("profileB")
                .withSources(pcs)
                .build();

        ConfigProviderResolver.instance().registerConfig(config, CamelMicroProfilePropertiesSourceTest.class.getClassLoader());

        return super.createCamelContext();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        ConfigProviderResolver.instance().releaseConfig(config);
        super.tearDown();
    }

    @Override
    protected void bindToRegistry(Registry registry) {
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
    public void testLoadAll() {
        PropertiesComponent pc = context.getPropertiesComponent();
        Properties properties = pc.loadProperties();

        Assertions.assertThat(properties.get("start")).isEqualTo("direct:start");
        Assertions.assertThat(properties.get("hi")).isEqualTo("World");
        Assertions.assertThat(properties.get("my-mock")).isEqualTo("result");
        Assertions.assertThat(properties.get("empty")).isNull();
        Assertions.assertThat(properties.get("test-non-active-profile")).isNull();
        Assertions.assertThat(properties.get("test-profile-a")).isEqualTo("Profile A");
        Assertions.assertThat(properties.get("test-profile-b")).isEqualTo("Profile B");
    }

    @Test
    public void testLoadFiltered() {
        PropertiesComponent pc = context.getPropertiesComponent();
        Properties properties = pc.loadProperties(k -> k.matches("^start$|.*mock$|.*-profile.*"));

        Assertions.assertThat(properties).hasSize(4);
        Assertions.assertThat(properties.get("start")).isEqualTo("direct:start");
        Assertions.assertThat(properties.get("my-mock")).isEqualTo("result");
        Assertions.assertThat(properties.get("test-non-active-profile")).isNull();
        Assertions.assertThat(properties.get("test-profile-a")).isEqualTo("Profile A");
        Assertions.assertThat(properties.get("test-profile-b")).isEqualTo("Profile B");
    }

    @Test
    public void testMicroProfileConfig() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World from Camel");

        template.sendBody("direct:start", context.resolvePropertyPlaceholders("Hello {{hi}} from {{who}}"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testActiveConfigProfiles() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Profile A :: Profile B");

        template.sendBody("direct:start", context.resolvePropertyPlaceholders("{{test-profile-a}} :: {{test-profile-b}}"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInactiveConfigProfiles() throws Exception {
        assertThatThrownBy(() -> {
            template.sendBody("direct:start", context.resolvePropertyPlaceholders("{{test-non-active-profile}}"));
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Property with key [test-non-active-profile] not found");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("{{start}}")
                        .to("mock:{{my-mock}}");
            }
        };
    }
}
