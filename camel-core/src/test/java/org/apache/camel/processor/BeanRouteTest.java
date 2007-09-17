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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.util.jndi.JndiContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.naming.Context;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @version $Revision: 1.1 $
 */
public class BeanRouteTest extends ContextTestSupport {
    private static final transient Log LOG = LogFactory.getLog(BeanRouteTest.class);
    protected MyBean myBean = new MyBean();

    public void testSendingMessageWithMethodNameHeader() throws Exception {
        String expectedBody = "Wobble";

        template.sendBodyAndHeader("direct:in", expectedBody, BeanProcessor.METHOD_NAME, "read");

        assertEquals("bean received correct value for: " + myBean, expectedBody, myBean.body);
    }

    public void testSendingMessageWithMethodNameHeaderWithMoreVerboseCoe() throws Exception {
        final String expectedBody = "Wibble";

        template.send("direct:in", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody(expectedBody);
                in.setHeader(BeanProcessor.METHOD_NAME, "read");
            }
        });
        assertEquals("bean received correct value", expectedBody, myBean.body);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Object lookedUpBean = context.getRegistry().lookup("myBean");
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
                from("direct:in").beanRef("myBean");
            }
        };
    }

    public static class MyBean {
        public String body;
        private static AtomicInteger counter = new AtomicInteger(0);
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
            LOG.info("read() method on " + this + " with body: " + body);
        }

        public void wrongMethod(String body) {
            fail("wrongMethod() called with: " + body);
        }
    }
}