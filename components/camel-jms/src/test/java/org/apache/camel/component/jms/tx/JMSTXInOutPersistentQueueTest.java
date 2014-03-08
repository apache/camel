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
package org.apache.camel.component.jms.tx;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class JMSTXInOutPersistentQueueTest extends CamelSpringTestSupport {

    private static int counter;

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jms/tx/JmsTransacted-context.xml");
    }

    @Test
    public void testJMSTXInOutPersistentQueueWithClientRedelivery() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("World", "World", "World");
        getMockEndpoint("mock:reply").expectedBodiesReceived("Bye World", "Bye World", "Bye World");

        try {
            template.sendBody("direct:start", "World");
            fail("Should thrown an exception");
        } catch (Exception e) {
            // ignore
        }

        // let client re-try
        try {
            template.sendBody("direct:start", "World");
            fail("Should thrown an exception");
        } catch (Exception e) {
            // ignore
        }

        // now we succeed
        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").inOut("activemq:queue:foo?replyTo=myReplies")
                    .to("mock:reply")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            if (counter++ < 2) {
                                throw new IllegalArgumentException("Damn");
                            }
                        }
                    }).to("mock:result");

                from("activemq:queue:foo").to("mock:foo").transform(body().prepend("Bye "));
            }
        };
    }
}
