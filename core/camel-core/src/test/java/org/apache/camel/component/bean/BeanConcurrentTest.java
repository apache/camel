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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class BeanConcurrentTest extends ContextTestSupport {

    @Test
    public void testBeanConcurrent() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1000);
        mock.expectsNoDuplicates(body());

        // start from 1000 to be 4 digit always (easier to string compare)
        for (int i = 1000; i < 2000; i++) {
            template.sendBody("seda:foo", "" + i);
        }

        context.getRouteController().startRoute("foo");

        assertMockEndpointsSatisfied();

        // should be 1000 messages
        List<String> list = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            String body = mock.getReceivedExchanges().get(i).getIn().getBody(String.class);
            list.add(body);
        }
        list.sort(null);

        // and they should be unique and no lost messages
        assertEquals(1000, list.size());
        for (int i = 1; i < 1000; i++) {
            int num = 1000 + i;
            String s = "" + num + " " + num;
            assertEquals(s, list.get(i));
        }
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myBean", new MyBean());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo?concurrentConsumers=10").routeId("foo").noAutoStartup().to("bean:myBean").to("mock:result");
            }
        };
    }

    @SuppressWarnings("unused")
    private static class MyBean {

        private String foo;
        private String bar;
        private int baz;

        public String getFoo() {
            return foo;
        }

        public void setFoo(String foo) {
            this.foo = foo;
        }

        public String getBar() {
            return bar;
        }

        public void setBar(String bar) {
            this.bar = bar;
        }

        public int getBaz() {
            return baz;
        }

        public void setBaz(int baz) {
            this.baz = baz;
        }

        public void doSomething() {
            // noop
        }

        public String echo(String s) {
            return s + " " + s;
        }

        @Override
        public String toString() {
            return "MyBean";
        }
    }
}
