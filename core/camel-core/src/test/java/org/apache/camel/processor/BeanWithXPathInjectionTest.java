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
import org.apache.camel.language.xpath.XPath;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanWithXPathInjectionTest extends ContextTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(BeanRouteTest.class);
    protected MyBean myBean = new MyBean();

    @Test
    public void testSendMessage() throws Exception {
        String expectedBody = "<env:Envelope xmlns:env='http://www.w3.org/2003/05/soap-envelope'><env:Body>"
                              + "<foo>bar</foo></env:Body></env:Envelope>";

        template.sendBodyAndHeader("direct:in", expectedBody, "foo", "bar");

        assertEquals(expectedBody, myBean.body, "bean body: " + myBean);
        assertEquals("bar", myBean.foo, "bean foo: " + myBean);
    }

    @Test
    public void testSendTwoMessages() throws Exception {
        // 1st message
        String expectedBody = "<env:Envelope xmlns:env='http://www.w3.org/2003/05/soap-envelope'><env:Body>"
                              + "<foo>bar</foo></env:Body></env:Envelope>";

        template.sendBodyAndHeader("direct:in", expectedBody, "foo", "bar");

        assertEquals(expectedBody, myBean.body, "bean body: " + myBean);
        assertEquals("bar", myBean.foo, "bean foo: " + myBean);

        // 2nd message
        String expectedBody2 = "<env:Envelope xmlns:env='http://www.w3.org/2003/05/soap-envelope'><env:Body>"
                               + "<foo>baz</foo></env:Body></env:Envelope>";

        template.sendBodyAndHeader("direct:in", expectedBody2, "foo", "baz");

        assertEquals(expectedBody2, myBean.body, "bean body: " + myBean);
        assertEquals("baz", myBean.foo, "bean foo: " + myBean);
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();

        answer.bind("myBean", myBean);
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in").bean("myBean");
            }
        };
    }

    public static class MyBean {
        public String body;
        public String foo;

        @Override
        public String toString() {
            return "MyBean[foo: " + foo + " body: " + body + "]";
        }

        public void read(String body, @XPath("/soap:Envelope/soap:Body/foo/text()") String foo) {
            this.foo = foo;
            this.body = body;
            LOG.info("read() method called on " + this);
        }
    }
}
