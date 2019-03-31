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
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.junit.Test;

public class BeanExcludedMethodTest extends ContextTestSupport {

    @Test
    public void testExcludedMethod() throws Exception {
        BeanInfo info = new BeanInfo(context, MyDummyBean.class);

        Exchange exchange = new DefaultExchange(context);
        MyDummyBean pojo = new MyDummyBean();
        MethodInvocation mi = info.createInvocation(pojo, exchange);
        assertNull("Should not be possible to find a suitable method", mi);
    }

    @Test
    public void testNotExcludedMethod() throws Exception {
        BeanInfo info = new BeanInfo(context, MyOtherDummyBean.class);

        Exchange exchange = new DefaultExchange(context);
        MyOtherDummyBean pojo = new MyOtherDummyBean();
        MethodInvocation mi = info.createInvocation(pojo, exchange);
        assertNotNull(mi);
        assertEquals("hello", mi.getMethod().getName());
    }

    public static class MyDummyBean {

        @Override
        public boolean equals(Object obj) {
            fail("Should not call equals");
            return true;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

    }

    public static class MyOtherDummyBean {

        @Override
        public boolean equals(Object obj) {
            fail("Should not call equals");
            return true;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public String toString() {
            return "dummy";
        }

        public String hello(String hi) {
            return "Hello " + hi;
        }
    }

}
