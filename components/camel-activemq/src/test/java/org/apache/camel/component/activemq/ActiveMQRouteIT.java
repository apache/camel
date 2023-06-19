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
package org.apache.camel.component.activemq;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ActiveMQRouteIT extends ActiveMQITSupport {

    private ProducerTemplate template;

    private MockEndpoint resultEndpoint;
    private String expectedBody = "Hello there!";

    @BeforeEach
    void setupTemplate() {
        template = contextExtension.getProducerTemplate();
        resultEndpoint = contextExtension.getMockEndpoint("mock:result");
    }

    @Test
    public void testJmsQueue() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);

        template.sendBodyAndHeader("activemq:queue:ping", expectedBody, "cheese", 123);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testRequestReply() {
        String response = template.requestBody("activemq:queue:inOut", expectedBody, String.class);
        assertEquals("response", response);
    }

    @Test
    public void testJmsTopic() throws Exception {
        resultEndpoint.expectedMessageCount(2);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);
        template.sendBodyAndHeader("activemq:topic:ping", expectedBody, "cheese", 123);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testPrefixWildcard() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        template.sendBody("activemq:wildcard.foo.bar", expectedBody);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testIncludeDestination() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).header("JMSDestination").isEqualTo("queue://ping");
        template.sendBody("activemq:queue:ping", expectedBody);
        resultEndpoint.assertIsSatisfied();
    }

    @ContextFixture
    public void configureContext(CamelContext context) {

        context.addComponent("activemq", activeMQComponent(service.defaultEndpoint()));
    }

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    private static RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:queue:ping")
                        .to("log:routing")
                        .to("mock:result");

                from("activemq:queue:inOut")
                        .setBody()
                        .constant("response");

                from("activemq:topic:ping")
                        .to("log:routing")
                        .to("mock:result");

                from("activemq:topic:ping")
                        .to("log:routing")
                        .to("mock:result");

                from("activemq:queue:wildcard.#")
                        .to("log:routing")
                        .to("mock:result");

                from("activemq:queue:uriEndpoint")
                        .to("log:routing")
                        .to("mock:result");
            }
        };
    }
}
