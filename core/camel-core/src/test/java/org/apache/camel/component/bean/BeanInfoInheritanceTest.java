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
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for overridden methods in an inheritance.
 */
public class BeanInfoInheritanceTest extends ContextTestSupport {

    @Test
    public void testInheritance() {
        BeanInfo beanInfo = new BeanInfo(context, Y.class);

        DefaultExchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new Request());

        assertDoesNotThrow(() -> {
            MethodInvocation mi = beanInfo.createInvocation(null, exchange);
            assertNotNull(mi);
            assertEquals("process", mi.getMethod().getName());
            assertEquals("Y", mi.getMethod().getDeclaringClass().getSimpleName());
        }, "This should not be ambiguous!");
    }

    @Test
    public void testNoInheritance() {
        BeanInfo beanInfo = new BeanInfo(context, A.class);

        DefaultExchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new Request());

        assertDoesNotThrow(() -> {
            MethodInvocation mi = beanInfo.createInvocation(null, exchange);
            assertNotNull(mi);
            assertEquals("process", mi.getMethod().getName());
            assertEquals("A", mi.getMethod().getDeclaringClass().getSimpleName());
        }, "This should not be ambiguous!");
    }

    @Test
    public void testInheritanceAndOverload() {
        BeanInfo beanInfo = new BeanInfo(context, Z.class);

        DefaultExchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new Request());

        assertThrows(AmbiguousMethodCallException.class, () -> beanInfo.createInvocation(null, exchange),
                "This should be ambiguous!");
    }

    public static class Request {
        int x;
    }

    public static class X {

        public int process(Request request) {
            return 0;
        }
    }

    public static class Y extends X {

        @Override
        public int process(Request request) {
            return 1;
        }

        public int compute(String body) {
            return 2;
        }
    }

    public static class Z extends Y {

        public int compute(Request request) {
            return 2;
        }

        public int process(Request request, String body) {
            return 3;
        }
    }

    public static class A {

        public void doSomething(String body) {
            // noop
        }

        public int process(Request request) {
            return 0;
        }
    }

}
