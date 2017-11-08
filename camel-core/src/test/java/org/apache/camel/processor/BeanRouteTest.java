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
package org.apache.camel.processor;

import java.util.concurrent.atomic.AtomicInteger;
import javax.naming.Context;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.jndi.JndiContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class BeanRouteTest extends ContextTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(BeanRouteTest.class);
    protected MyBean myBean = new MyBean();

    public void testSendingMessageWithMethodNameHeader() throws Exception {
        String expectedBody = "Wobble";

        template.sendBodyAndHeader("direct:in", expectedBody, Exchange.BEAN_METHOD_NAME, "read");

        assertEquals("bean received correct value for: " + myBean, expectedBody, myBean.body);
    }

    public void testSendingMessageWithMethodNameHeaderWithMoreVerboseCoe() throws Exception {
        final String expectedBody = "Wibble";

        template.send("direct:in", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody(expectedBody);
                in.setHeader(Exchange.BEAN_METHOD_NAME, "read");
            }
        });
        assertEquals("bean received correct value", expectedBody, myBean.body);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Object lookedUpBean = context.getRegistry().lookupByName("myBean");
        assertSame("Lookup of 'myBean' should return same object!", myBean, lookedUpBean);
    }

    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        answer.bind("myBean", myBean);
        return answer;
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in").bean("myBean");
            }
        };
    }

    public static class MyBean {
        private static AtomicInteger counter = new AtomicInteger(0);
        public String body;
        private int id;

        public MyBean() {
            id = counter.incrementAndGet();
        }

        @Override
        public String toString() {
            return "MyBean:" + id;
        }

        public void read(String body) {
            this.body = body;
            LOG.info("read() method on {} with body: {}", this, body);
        }

        public void wrongMethod(String body) {
            fail("wrongMethod() called with: " + body);
        }
    }
}
