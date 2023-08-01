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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractSpringJMSTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tags({ @Tag("not-parallel"), @Tag("spring"), @Tag("tx") })
public class TransactedAsyncUsingThreadsTest extends AbstractSpringJMSTestSupport {

    private static int counter;
    private static String thread1;
    private static String thread2;

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "/org/apache/camel/component/jms/tx/TransactedAsyncUsingThreadsTest.xml");
    }

    @BeforeEach
    public void init() {
        counter = 0;
        thread1 = "";
        thread2 = "";
    }

    @Test
    public void testConsumeAsyncOK() throws Exception {
        counter = 1;

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:async").expectedMessageCount(1);

        template.sendBody("activemq:queue:TransactedAsyncUsingThreadsTest", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        // transacted causes Camel to force sync routing
        assertEquals(thread1, thread2, "Should use a same thread when doing transacted async routing");
    }

    @Test
    public void testConsumeAsyncFail() throws Exception {
        counter = 0;

        getMockEndpoint("mock:result").expectedMessageCount(1);
        // we need a retry attempt so we get 2 messages
        getMockEndpoint("mock:async").expectedMessageCount(2);

        // the 1st message is the original message
        getMockEndpoint("mock:async").message(0).header("JMSRedelivered").isEqualTo(false);

        // the 2nd message is the redelivered by the JMS broker
        getMockEndpoint("mock:async").message(1).header("JMSRedelivered").isEqualTo(true);

        template.sendBody("activemq:queue:TransactedAsyncUsingThreadsTest", "Bye World");

        MockEndpoint.assertIsSatisfied(context);

        // transacted causes Camel to force sync routing
        assertEquals(thread1, thread2, "Should use a same thread when doing transacted async routing");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:TransactedAsyncUsingThreadsTest")
                        .process(exchange -> thread1 = Thread.currentThread().getName())
                        // use transacted routing
                        .transacted()
                        // and route async from this point forward
                        .threads(5)
                        // send to mock for verification
                        .to("mock:async")
                        .process(exchange -> {
                            thread2 = Thread.currentThread().getName();

                            if (counter++ == 0) {
                                // simulate error so we can test rollback and have the JMS broker
                                // do redelivery
                                throw new IllegalAccessException("Damn");
                            }
                        }).to("mock:result");

            }
        };
    }

}
