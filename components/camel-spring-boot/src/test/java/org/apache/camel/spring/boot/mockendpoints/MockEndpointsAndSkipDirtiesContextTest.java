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
package org.apache.camel.spring.boot.mockendpoints;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.camel.test.spring.CamelSpringBootRunner;
import org.apache.camel.test.spring.MockEndpointsAndSkip;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;

@RunWith(CamelSpringBootRunner.class)
@MockEndpointsAndSkip("direct:b")
@SpringBootApplication
@SpringBootTest(classes = MockEndpointsAndSkipDirtiesContextTest.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class MockEndpointsAndSkipDirtiesContextTest {

    @Produce(uri = "direct:a")
    private ProducerTemplate producer;

    @EndpointInject(uri = "mock:end")
    private MockEndpoint end;

    @EndpointInject(uri = "mock:direct:b")
    private MockEndpoint directB;

    @Autowired
    private CamelContext context;

    @Configuration
    public static class Config extends SpringRouteBuilder {

        @Override
        public void configure() throws Exception {
            from("direct:a").to("direct:b");
            from("direct:b").to("mock:end");
        }
    }

    @Test
    public void testMock() throws InterruptedException {
        end.expectedMessageCount(0);
        directB.expectedBodiesReceived("hello");

        producer.sendBody("hello");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMock2() throws InterruptedException {
        end.expectedMessageCount(0);
        directB.expectedBodiesReceived("bye");

        producer.sendBody("bye");

        MockEndpoint.assertIsSatisfied(context);
    }
}
