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

import javax.naming.Context;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.jndi.JndiContext;

/**
 * Unit test to demonstrate routes initiated by sending a body to bean.
 */
public class SendBodyToBeanDirectlyTest extends ContextTestSupport {

    public void testSendBodyToBeanDirectly() throws Exception {
        // note: the route chain is newever invoked as bean:one will only call this bean
        // and not do a route
        Object response = template.requestBody("bean:one", "Start:");
        assertEquals("Start:one", response);
    }

    public void testSendBodyToBeanIndirectly() throws Exception {
        // note we must use pipeline in the route (check the builder)
        Object response = template.requestBody("direct:start", "Start:");
        assertEquals("Start:onetwo", response);
    }

    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        answer.bind("one", new MyBean("one"));
        answer.bind("two", new MyBean("two"));
        return answer;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // this route will never happen
                from("bean:one").to("bean:two");

                // must use pipeline to force the route chains with beans to beans
                from("direct:start").pipeline("bean:one", "bean:two");
            }
        };
    }

    public static class MyBean {

        private String postfix;

        public MyBean(String postfix) {
            this.postfix = postfix;
        }

        public String doSomething(String body) {
            return body + postfix;
        }
    }

}