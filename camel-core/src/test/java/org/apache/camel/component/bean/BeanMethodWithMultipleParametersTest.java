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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.BeanRouteTest;
import org.apache.camel.util.jndi.JndiContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class BeanMethodWithMultipleParametersTest extends ContextTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(BeanRouteTest.class);
    protected MyBean myBean = new MyBean();

    public void testDummy() throws Exception {

    }

    public void testSendMessageWithURI() throws Exception {
        Object[] args = {"abc", 5, "def"};
        template.sendBody("bean:myBean?method=myMethod&multiParameterArray=true", args);

        assertEquals("bean.foo", "abc", myBean.foo);
        assertEquals("bean.bar", 5, myBean.bar);
        assertEquals("bean.x", "def", myBean.x);
    }

    public void testSendMessageWithSettingHeader() throws Exception {
        Object[] args = {"hello", 123, "world"};
        template.sendBodyAndHeader("direct:in", args, Exchange.BEAN_MULTI_PARAMETER_ARRAY, true);

        assertEquals("bean.foo", "hello", myBean.foo);
        assertEquals("bean.bar", 123, myBean.bar);
        assertEquals("bean.x", "world", myBean.x);
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
                from("direct:in").to("bean:myBean?method=myMethod");
            }
        };
    }

    public class MyBean {
        public String foo;
        public int bar;
        public String x;

        @Override
        public String toString() {
            return "MyBean[foo: " + foo + " bar: " + bar + " x: " + x + "]";
        }

        public void myMethod(String foo, int bar, String x) {
            this.foo = foo;
            this.bar = bar;
            this.x = x;
            LOG.info("myMethod() method called on " + this);
        }

        public void anotherMethod(Object body) {
            fail("Should not have called this method!");
        }
    }
}
