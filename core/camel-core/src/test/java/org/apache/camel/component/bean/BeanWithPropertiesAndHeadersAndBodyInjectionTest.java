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

import org.apache.camel.Body;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperties;
import org.apache.camel.Headers;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class BeanWithPropertiesAndHeadersAndBodyInjectionTest extends ContextTestSupport {
    protected MyBean myBean = new MyBean();

    @Test
    public void testSendMessage() throws Exception {
        Exchange out = template.send("direct:in", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty("p1", "abc");
                exchange.setProperty("p2", 123);

                Message in = exchange.getIn();
                in.setHeader("h1", "xyz");
                in.setHeader("h2", 456);
                in.setBody("TheBody");
            }
        });

        assertEquals("Should not fail", false, out.isFailed());

        Map<?, ?> foo = myBean.foo;
        Map<?, ?> bar = myBean.bar;
        assertNotNull("myBean.foo", foo);
        assertNotNull("myBean.bar", bar);

        assertEquals("foo.p1", "abc", foo.get("p1"));
        assertEquals("foo.p2", 123, foo.get("p2"));

        assertEquals("bar.h1", "xyz", bar.get("h1"));
        assertEquals("bar.h2", 456, bar.get("h2"));
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
                from("direct:in").bean("myBean");
            }
        };
    }

    public static class MyBean {
        private Map<?, ?> foo;
        private Map<?, ?> bar;
        private String body;

        @Override
        public String toString() {
            return "MyBean[foo: " + foo + " bar: " + bar + " body: " + body + "]";
        }

        public void myMethod(@ExchangeProperties Map<?, ?> foo, @Headers Map<?, ?> bar, @Body String body) {
            this.foo = foo;
            this.bar = bar;
            this.body = body;

            assertNotNull(toString());
        }
    }

}
