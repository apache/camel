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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.Test;

/**
 * Tests to ensure a consistent return value when using the different ways of
 * configuring the RecipientList pattern
 */
public class RecipientListReturnValueTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myBean", new MyBean());
        return jndi;
    }

    @Test
    public void testRecipientListWithRecipientList() throws Exception {
        doTestRecipientList("direct:recipientList");
    }

    @Test
    public void testRecipientListWithBeanRef() throws Exception {
        doTestRecipientList("direct:beanRef");
    }

    private void doTestRecipientList(String uri) throws InterruptedException {
        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedBodiesReceived("Hello a");

        MockEndpoint b = getMockEndpoint("mock:b");
        b.expectedBodiesReceived("Hello b");

        String out = template.requestBody(uri, "Hello " + uri, String.class);

        assertEquals("Hello b", out);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:beanRef").bean("myBean", "route");
                from("direct:recipientList").recipientList().method("myBean", "recipientList");

                from("direct:a").transform(constant("Hello a")).to("mock:a");
                from("direct:b").transform(constant("Hello b")).to("mock:b");
            }
        };
    }

    public class MyBean {

        @org.apache.camel.RecipientList
        public String[] route() {
            return new String[] {"direct:a", "direct:b"};
        }

        public String[] recipientList() {
            return new String[] {"direct:a", "direct:b"};
        }
    }
}
