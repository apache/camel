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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for bridged methods.
 */
public class BeanInfoWithBridgedMethodTest extends ContextTestSupport {

    @Test
    public void testBridgedMethod() {
        BeanInfo beanInfo = new BeanInfo(context, MyService.class);

        DefaultExchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new Request(1));

        assertDoesNotThrow(() -> {
            MyService myService = new MyService();
            MethodInvocation mi = beanInfo.createInvocation(null, exchange);
            assertEquals("MyService", mi.getMethod().getDeclaringClass().getSimpleName());
            assertEquals(2, mi.getMethod().invoke(myService, new Request(1)));
        }, "This should not be ambiguous!");
    }

    @Test
    public void testPackagePrivate() {
        BeanInfo beanInfo = new BeanInfo(context, MyPackagePrivateService.class);

        DefaultExchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new Request(1));

        assertDoesNotThrow(() -> {
            MyPackagePrivateService myService = new MyPackagePrivateService();
            MethodInvocation mi = beanInfo.createInvocation(null, exchange);
            assertEquals("Service", mi.getMethod().getDeclaringClass().getSimpleName());
            assertEquals(4, mi.getMethod().invoke(myService, new Request(2)));
        }, "This should not be ambiguous!");
    }

    public static class Request {
        int x;

        public Request(int x) {
            this.x = x;
        }
    }

    public interface Service<R> {

        int process(R request);
    }

    public static class MyService implements Service<Request> {

        @Override
        public int process(Request request) {
            return request.x + 1;
        }
    }

    static class MyPackagePrivateService implements Service<Request> {

        @Override
        public int process(Request request) {
            return request.x + 2;
        }
    }

}
