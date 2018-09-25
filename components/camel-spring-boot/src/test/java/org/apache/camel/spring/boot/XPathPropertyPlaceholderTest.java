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
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
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
@SpringBootTest(
    classes = XPathPropertyPlaceholderTest.TestConfig.class,
    properties = {"foo = //greeting/text = 'Hello, world!'", "bar = //greeting/text = 'Bye, world!'"}
)
public class XPathPropertyPlaceholderTest {

    @Autowired
    private CamelContext context;

    @Autowired
    private ProducerTemplate template;

    @EndpointInject(uri = "mock:output-filter")
    private MockEndpoint mockOutputFilter;

    @EndpointInject(uri = "mock:output-choice")
    private MockEndpoint mockOutputChoice;

    @EndpointInject(uri = "mock:output-choice2")
    private MockEndpoint mockOutputChoice2;

    @Test
    public void testFilter() throws Exception {
        mockOutputFilter.expectedMessageCount(1);

        template.sendBody("direct:filter", "<greeting><text>Hello, world!</text></greeting>");

        mockOutputFilter.assertIsSatisfied();
    }

    @Test
    public void testChoice() throws Exception {
        mockOutputChoice.expectedMessageCount(1);

        template.sendBody("direct:choice", "<greeting><text>Bye, world!</text></greeting>");

        mockOutputChoice.assertIsSatisfied();
    }

    @Test
    public void testChoice2() throws Exception {
        mockOutputChoice2.expectedMessageCount(1);

        template.sendBody("direct:choice2", "<greeting><text>Bye, world!</text></greeting>");

        mockOutputChoice2.assertIsSatisfied();
    }

    @Configuration
    public static class TestConfig {
        @Bean
        public RouteBuilder routeBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:filter")
                        .filter().xpath("{{foo}}")
                            .log("Passed filter!")
                            .to("mock:output-filter");

                    from("direct:choice")
                        .choice()
                            .when(xpath("{{bar}}"))
                                .log("Passed choice!")
                                .to("mock:output-choice");

                    from("direct:choice2")
                        .choice()
                            .when().xpath("{{bar}}")
                                .log("Passed choice2!")
                                .to("mock:output-choice2");
                }
            };
        }
    }

}

