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
package org.apache.camel.spring.boot;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultRoute;
import org.assertj.core.api.Condition;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
public class MixedJavaDslAndXmlTest {

    @Configuration
    @EnableAutoConfiguration
    @ImportResource("classpath:test-camel-context.xml")
    public static class JavaDslConfiguration {

        @Bean
        public RouteBuilder javaDsl() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("timer:project?period=1s").id("java").setBody().constant("Hello World from Java Route")
                        .log(">>> ${body}");
                }
            };
        }

    }

    @Autowired
    private CamelContext camel;

    @Test
    public void thereShouldBeTwoRoutesConfigured() {
        final List<Route> routes = camel.getRoutes();
        assertThat(routes).as("There should be two routes configured, one from Java DSL and one from XML").hasSize(3);
        final List<String> routeIds = routes.stream().map(Route::getId).collect(Collectors.toList());
        assertThat(routeIds).as("Should contain routes from Java DSL, XML and auto-loaded XML").containsOnly("java",
            "xml", "xmlAutoLoading");
        assertThat(routes).as("All routes should be started").are(new Condition<Route>() {
            @Override
            public boolean matches(final Route route) {
                return ((DefaultRoute) route).isStarted();
            }
        });
    }
}
