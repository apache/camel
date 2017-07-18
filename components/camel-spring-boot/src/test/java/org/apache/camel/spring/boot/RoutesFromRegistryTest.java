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
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.RouteDefinition;
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

import static org.apache.camel.model.RouteDefinitionHelper.from;

@DirtiesContext
@RunWith(SpringRunner.class)
@EnableAutoConfiguration
@SpringBootTest(
    classes = RoutesFromRegistryTest.TestConfiguration.class
)
public class RoutesFromRegistryTest {
    @Autowired
    private CamelContext context;
    @Autowired
    private ProducerTemplate template;

    @Test
    public void testRoutes() throws Exception {
        Route start = context.getRoute("start");
        Assert.assertNotNull(start);
        Assert.assertEquals("start", start.getId());

        Route begin = context.getRoute("begin");
        Assert.assertNotNull(begin);
        Assert.assertEquals("begin", begin.getId());

        context.getEndpoint("mock:stop", MockEndpoint.class).expectedMessageCount(1);
        context.getEndpoint("mock:stop", MockEndpoint.class).expectedBodiesReceived("start");
        context.getEndpoint("mock:end", MockEndpoint.class).expectedMessageCount(1);
        context.getEndpoint("mock:end", MockEndpoint.class).expectedBodiesReceived("begin");

        template.sendBody("direct:start", "trats");
        template.sendBody("direct:begin", "nigeb");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Configuration
    public static class TestConfiguration {
        @Bean
        public RouteDefinition start() {
            return from("direct:start")
                .transform()
                    .body(String.class, b -> new StringBuilder(b).reverse().toString())
                .to("mock:stop");
        }
        @Bean
        public RouteDefinition begin() {
            return from("direct:begin")
                .transform()
                    .body(String.class, b -> new StringBuilder(b).reverse().toString())
                .to("mock:end");
        }
    }
}
