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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.language.simple.Simple;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class BeanWithExpressionInjectionPredicateTest extends ContextTestSupport {
    protected MyBean myBean = new MyBean();

    @Test
    public void testSendMessage() throws Exception {
        template.sendBody("direct:in", "Hello");

        assertEquals("Hello", myBean.body);
        assertEquals(false, myBean.foo);
    }

    @Test
    public void testSendMessageWithFoo() throws Exception {
        template.sendBodyAndHeader("direct:in", "Hello", "foo", 123);

        assertEquals("Hello", myBean.body);
        assertEquals(true, myBean.foo);
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
        public boolean foo;

        public void read(String body, @Simple("${header.foo} != null") boolean foo) {
            this.body = body;
            this.foo = foo;
        }
    }
}
