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
package org.apache.camel.component.jms.tx;

import org.apache.camel.Body;
import org.apache.camel.EndpointInject;
import org.apache.camel.Handler;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringRouteBuilder;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class JMXTXUseOriginalBodyWithDLCErrorHandlerTest extends JMXTXUseOriginalBodyTest {

    @EndpointInject("mock:end")
    protected MockEndpoint endpoint;

    @EndpointInject("mock:error")
    protected MockEndpoint error;

    @EndpointInject("mock:dead")
    protected MockEndpoint dead;

    @EndpointInject("mock:checkpoint1")
    protected MockEndpoint checkpoint1;

    @EndpointInject("mock:checkpoint2")
    protected MockEndpoint checkpoint2;

    @Produce("activemq:start")
    protected ProducerTemplate start;

    @Produce("activemq:broken")
    protected ProducerTemplate broken;

    @Produce("activemq:ok")
    protected ProducerTemplate ok;

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("/org/apache/camel/component/jms/tx/JMXTXUseOriginalBodyWithDLCErrorHandlerTest.xml");
    }

    @Override
    @Test
    public void testWithConstant() throws InterruptedException {
        endpoint.expectedMessageCount(0);
        dead.expectedMessageCount(0);
        error.expectedBodiesReceived("foo");
        checkpoint1.expectedBodiesReceived("foo");
        checkpoint2.expectedBodiesReceived("oh no");

        start.sendBody("foo");

        assertMockEndpointsSatisfied();
    }

    @Override
    @Test
    public void testWithBean() throws InterruptedException {
        endpoint.expectedMessageCount(0);
        dead.expectedMessageCount(0);
        error.expectedBodiesReceived("foo");
        checkpoint1.expectedBodiesReceived("foo");
        checkpoint2.expectedBodiesReceived("oh no");

        broken.sendBody("foo");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOk() throws InterruptedException {
        endpoint.expectedMessageCount(1);
        dead.expectedMessageCount(0);
        error.expectedMessageCount(0);
        checkpoint1.expectedBodiesReceived("foo");
        checkpoint2.expectedBodiesReceived("oh no");

        ok.sendBody("foo");

        assertMockEndpointsSatisfied();
    }

    public static class FooBean {
        @Handler
        public String process(@Body String body) {
            return "oh no";
        }
    }


    public static class TestRoutes extends SpringRouteBuilder {

        @Override
        public void configure() throws Exception {
            errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(5));

            onException(Exception.class)
                    .handled(true)
                    .useOriginalMessage()
                    .redeliveryDelay(0)
                    .maximumRedeliveries(2)
                    .to("mock:error");

            from("activemq:broken")
                    .transacted()
                    .to("mock:checkpoint1")
                    .setBody(method("foo"))
                    .to("mock:checkpoint2")
                    .throwException(new Exception("boo"))
                    .to("mock:end");

            from("activemq:start")
                    .transacted()
                    .to("mock:checkpoint1")
                    .setBody(constant("oh no"))
                    .to("mock:checkpoint2")
                    .throwException(new Exception("boo"))
                    .to("mock:end");

            from("activemq:ok")
                    .transacted()
                    .to("mock:checkpoint1")
                    .setBody(constant("oh no"))
                    .to("mock:checkpoint2")
                    .to("mock:end");

        }
    }
}