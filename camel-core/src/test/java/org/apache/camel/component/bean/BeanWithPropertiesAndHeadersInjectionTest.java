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
package org.apache.camel.component.bean;

import java.util.Map;

import javax.naming.Context;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperties;
import org.apache.camel.Headers;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.BeanRouteTest;
import org.apache.camel.util.jndi.JndiContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class BeanWithPropertiesAndHeadersInjectionTest extends ContextTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(BeanRouteTest.class);
    protected MyBean myBean = new MyBean();

    public void testSendMessage() throws Exception {
        template.send("direct:in", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty("p1", "abc");
                exchange.setProperty("p2", 123);

                Message in = exchange.getIn();
                in.setHeader("h1", "xyz");
                in.setHeader("h2", 456);
            }
        });

        Map<?, ?> foo = myBean.foo;
        Map<?, ?> bar = myBean.bar;
        assertNotNull("myBean.foo", foo);
        assertNotNull("myBean.bar", bar);

        assertEquals("foo.p1", "abc", foo.get("p1"));
        assertEquals("foo.p2", 123, foo.get("p2"));

        assertEquals("bar.h1", "xyz", bar.get("h1"));
        assertEquals("bar.h2", 456, bar.get("h2"));
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
        public Map<?, ?> foo;
        public Map<?, ?> bar;

        @Override
        public String toString() {
            return "MyBean[foo: " + foo + " bar: " + bar + "]";
        }

        public void myMethod(@ExchangeProperties Map<?, ?> foo, @Headers Map<?, ?> bar) {
            this.foo = foo;
            this.bar = bar;
            LOG.info("myMethod() method called on " + this);
        }
    }
}
