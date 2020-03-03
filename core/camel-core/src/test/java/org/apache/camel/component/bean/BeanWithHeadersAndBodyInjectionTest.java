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
package org.apache.camel.component.bean;

import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Headers;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.BeanRouteTest;
import org.apache.camel.spi.Registry;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeanWithHeadersAndBodyInjectionTest extends ContextTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(BeanRouteTest.class);
    protected MyBean myBean = new MyBean();

    @Test
    public void testSendMessage() throws Exception {
        template.send("direct:in", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty("p1", "abc");
                exchange.setProperty("p2", 123);

                Message in = exchange.getIn();
                in.setHeader("h1", "xyz");
                in.setHeader("h2", 456);
                in.setBody("TheBody");
            }
        });

        Map<String, Object> foo = myBean.headers;
        assertNotNull("myBean.foo", foo);

        assertEquals("foo.h1", "xyz", foo.get("h1"));
        assertEquals("foo.h2", 456, foo.get("h2"));

        assertEquals("body", "TheBody", myBean.body);
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
                from("direct:in").to("bean:myBean?method=myMethod");
            }
        };
    }

    public static class MyBean {
        public Map<String, Object> headers;
        public Object body;

        @Override
        public String toString() {
            return "MyBean[foo: " + headers + " body: " + body + "]";
        }

        public void myMethod(@Headers Map<String, Object> headers, Object body) {
            this.headers = headers;
            this.body = body;
            LOG.info("myMethod() method called on " + this);
        }

        public void anotherMethod(@Headers Map<String, Object> headers, Object body) {
            fail("Should not have called this method!");
        }
    }
}
