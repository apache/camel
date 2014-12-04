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
package org.apache.camel.spring.javaconfig;

import org.apache.camel.EndpointInject;
import org.apache.camel.Handler;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringDelegatingTestContextLoader;
import org.apache.camel.test.spring.CamelSpringJUnit4ClassRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@RunWith(CamelSpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {BeanJavaConfigTest.ContextConfig.class}, loader = CamelSpringDelegatingTestContextLoader.class)
public class BeanJavaConfigTest extends AbstractJUnit4SpringContextTests {

    @EndpointInject(uri = "mock:end")
    protected MockEndpoint endpoint;

    @EndpointInject(uri = "mock:error")
    protected MockEndpoint errorEndpoint;

    @Produce(uri = "direct:start")
    protected ProducerTemplate producer;

    @Test
    @DirtiesContext
    public void testRouteUsingBean() throws Exception {
        endpoint.expectedMessageCount(1);
        endpoint.message(0).body().isEqualTo("Hello World");
        producer.sendBody("World");
        endpoint.assertIsSatisfied();
    }

    public static class SomeBean {
        @Handler
        public String someMethod(String body) {
            return "Hello " + body;
        }
    }

    @Configuration
    public static class ContextConfig extends SingleRouteCamelConfiguration {
        @Bean
        @Override
        public RouteBuilder route() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start")
                            .errorHandler(deadLetterChannel("mock:end"))
                            .bean(new SomeBean())
                            .to("mock:end");
                }
            };
        }
    }

}