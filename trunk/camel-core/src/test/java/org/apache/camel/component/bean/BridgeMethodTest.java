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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;

/**
 * @version 
 */
public class BridgeMethodTest extends ContextTestSupport {

    public void testBridgeMethod() throws Exception {
        getMockEndpoint("mock:bar").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:bar").message(0).body().isEqualTo(new MyMessageClass(4));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("foo", new MyFooBean());
        jndi.bind("bar", new MyBarBean());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("bean:foo").to("bean:bar").to("mock:bar");
            }
        };
    }

    private static class MessageBaseClass {
        final int i;

        public MessageBaseClass(int i) {
            this.i = i;
        }

        public MessageBaseClass doubleIt() {
            return new MessageBaseClass(2 * i);
        }

        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }

            if (o.getClass() != getClass()) {
                return false;
            }

            return ((MessageBaseClass) o).i == i;
        }

        public int hashCode() {
            return 0;
        }
    }

    private static class MyMessageClass extends MessageBaseClass {

        public MyMessageClass(int i) {
            super(i);
        }

        public MyMessageClass doubleIt() {
            return new MyMessageClass(2 * i);
        }
    }

    @SuppressWarnings("unused")
    private static class MyFooBean {

        public MyMessageClass source() {
            return new MyMessageClass(2);
        }
    }

    private interface MyBar<T> {
        T function(T arg);
    }

    private static class MyBarBean implements MyBar<MessageBaseClass> {

        public MessageBaseClass function(MessageBaseClass arg) {
            return arg.doubleIt();
        }
    }
}
