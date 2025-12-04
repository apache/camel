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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeanWithHeadersAndBodyInject2Test extends ContextTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(BeanWithHeadersAndBodyInject2Test.class);
    private final MyBean myBean = new MyBean();
    private final Map<String, User> users = new HashMap<>();

    @Test
    public void testCannotBindToParameter() {
        // Create hashmap for testing purpose
        users.put("charles", new User("Charles", "43"));
        users.put("claus", new User("Claus", "33"));

        Exchange out = template.send("direct:in", new Processor() {
            public void process(Exchange exchange) {
                exchange.setProperty("p1", "abc");
                exchange.setProperty("p2", 123);

                Message in = exchange.getIn();
                in.setHeader("users", users); // add users hashmap
                in.setBody("TheBody");
            }
        });

        assertTrue(out.isFailed(), "Should fail");
        assertIsInstanceOf(RuntimeCamelException.class, out.getException());
        assertIsInstanceOf(
                NoTypeConversionAvailableException.class, out.getException().getCause());
    }

    @Test
    public void testBindToParameter() {
        final List<String> list = new ArrayList<>();
        list.add("Charles");
        list.add("Claus");

        Exchange out = template.send("direct:in", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody("TheBody");
                exchange.getIn().setHeader("users", list);
            }
        });

        assertFalse(out.isFailed(), "Should not fail");
        assertSame(list, myBean.users);
        assertEquals("TheBody", myBean.body);
    }

    @Test
    public void testBindToParameterIsNullValue() {
        Exchange out = template.send("direct:in", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody("TheBody");
                exchange.getIn().setHeader("users", null);
            }
        });

        assertFalse(out.isFailed(), "Should not fail");
        assertEquals("TheBody", myBean.body);
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry answer = super.createCamelRegistry();
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
        public Object body;
        public List<User> users;

        public void myMethod(@Header(value = "users") List<User> users, Object body) {
            LOG.info("myMethod() method called on {}", this);
            LOG.info(" users {}", users);
            this.body = body;
            this.users = users;
        }
    }

    public static class User {
        public final String name;
        public final String age;

        public User(String name, String age) {
            this.name = name;
            this.age = age;
        }
    }
}
