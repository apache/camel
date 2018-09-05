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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@DirtiesContext
@RunWith(SpringRunner.class)
@EnableAutoConfiguration
@SpringBootTest(classes = CustomCamelCamelConfigurationTest.class)
public class CustomCamelCamelConfigurationTest extends Assert {

    @Configuration
    static class Config {

        @Bean
        RouteBuilder routeBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:foo").routeId("foo").noAutoStartup()
                            .to("mock:result");
                }
            };
        }

        @Bean
        public CamelContextConfiguration myConfiguration() {
            return new CamelContextConfiguration() {

                @Override
                public void beforeApplicationStart(CamelContext camelContext) {
                    // noop
                }

                @Override
                public void afterApplicationStart(CamelContext camelContext) {
                    // lets start the route
                    try {
                        camelContext.startRoute("foo");
                    } catch (Exception e) {
                        // ignore
                    }
                }
            };
        }
    }

    @Autowired
    CamelContextConfiguration custom;

    @Autowired
    CamelContext camelContext;

    @Test
    public void shouldCustom() throws Exception {
        assertNotNull(custom);

        // should be started now from the custom camel configuration
        assertTrue(camelContext.getRouteStatus("foo").isStarted());
    }

}
