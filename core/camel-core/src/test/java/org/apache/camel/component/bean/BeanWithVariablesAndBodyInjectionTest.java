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
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Variables;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.BeanRouteTest;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class BeanWithVariablesAndBodyInjectionTest extends ContextTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(BeanRouteTest.class);
    protected MyBean myBean = new MyBean();

    @Test
    public void testSendMessage() throws Exception {
        template.send("direct:in", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty("p1", "abc");
                exchange.setProperty("p2", 123);

                Message in = exchange.getIn();
                exchange.setVariable("h1", "xyz");
                exchange.setVariable("h2", 456);
                in.setBody("TheBody");
            }
        });

        Map<String, Object> foo = myBean.variables;
        assertNotNull(foo, "myBean.foo");

        assertEquals("xyz", foo.get("h1"), "foo.h1");
        assertEquals(456, foo.get("h2"), "foo.h2");

        assertEquals("TheBody", myBean.body, "body");
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
        public Map<String, Object> variables;
        public Object body;

        @Override
        public String toString() {
            return "MyBean[foo: " + variables + " body: " + body + "]";
        }

        public void myMethod(@Variables Map<String, Object> variables, Object body) {
            this.variables = variables;
            this.body = body;
            LOG.info("myMethod() method called on {}", this);
        }

        public void anotherMethod(@Variables Map<String, Object> variables, Object body) {
            fail("Should not have called this method!");
        }
    }
}
