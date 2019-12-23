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
package org.apache.camel.spring.javaconfig.test;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.javaconfig.SingleRouteCamelConfiguration;
import org.apache.camel.test.spring.CamelSpringDelegatingTestContextLoader;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.apache.camel.test.spring.MockEndpoints;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

/**
 * Test for CamelSpringDelegatingTestContextLoader.
 */
//START SNIPPET: example
// tag::example[]
@RunWith(CamelSpringRunner.class)
@ContextConfiguration(
        classes = {CamelSpringDelegatingTestContextLoaderTest.TestConfig.class},
        // Since Camel 2.11.0 
        loader = CamelSpringDelegatingTestContextLoader.class
    )
@MockEndpoints
public class CamelSpringDelegatingTestContextLoaderTest {
    @EndpointInject("mock:direct:end")
    protected MockEndpoint endEndpoint;

    @EndpointInject("mock:direct:error")
    protected MockEndpoint errorEndpoint;

    @Produce("direct:test")
    protected ProducerTemplate testProducer;

    @Configuration
    public static class TestConfig extends SingleRouteCamelConfiguration {
        @Bean
        @Override
        public RouteBuilder route() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:test").errorHandler(deadLetterChannel("direct:error")).to("direct:end");

                    from("direct:error").log("Received message on direct:error endpoint.");

                    from("direct:end").log("Received message on direct:end endpoint.");
                }
            };
        }
    }

    @Test
    public void testRoute() throws InterruptedException {
        endEndpoint.expectedMessageCount(1);
        errorEndpoint.expectedMessageCount(0);

        testProducer.sendBody("<name>test</name>");

        endEndpoint.assertIsSatisfied();
        errorEndpoint.assertIsSatisfied();
    }
}
// end::example[]
//END SNIPPET: example

