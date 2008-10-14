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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.jndi.JndiContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public class BeanRecipientListTest extends ContextTestSupport {
    private static final transient Log LOG = LogFactory.getLog(BeanRecipientListTest.class);
    protected MyBean myBean = new MyBean();

    public void testSendMessage() throws Exception {
        final String expectedBody = "Wibble";

        getMockEndpoint("mock:a").expectedBodiesReceived(expectedBody);
        getMockEndpoint("mock:b").expectedBodiesReceived(expectedBody);

        template.sendBody("direct:in", expectedBody);

        assertMockEndpointsSatisfied();
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
                from("direct:in").beanRef("myBean", "route");
            }
        };
    }

    public static class MyBean {
        private static AtomicInteger counter = new AtomicInteger(0);
        private int id;

        public MyBean() {
            id = counter.incrementAndGet();
        }

        @Override
        public String toString() {
            return "MyBean:" + id;
        }

        @org.apache.camel.RecipientList
        public String[] route(String body) {
            LOG.debug("Called " + this + " with body: " + body);
            return new String[] {"mock:a", "mock:b"};
        }
    }
}