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
package org.apache.camel.impl;

import java.lang.reflect.Method;

import org.apache.camel.Consume;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class CamelPostProcessorHelperConsumePropertyTest extends ContextTestSupport {

    public void testConsumePropertyExplicit() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyConsumeBean my = new MyConsumeBean();
        my.setFoo("seda:foo");

        Method method = my.getClass().getMethod("consumeSomething", String.class);
        helper.consumerInjection(method, my, "foo");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("seda:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testConsumePropertyImplicit() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyConsumeBean my = new MyConsumeBean();
        my.setFoo("seda:foo");

        Method method = my.getClass().getMethod("foo", String.class);
        helper.consumerInjection(method, my, "foo");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("seda:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testConsumePropertyOnImplicit() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyConsumeBean my = new MyConsumeBean();
        my.setFoo("seda:foo");

        Method method = my.getClass().getMethod("onFoo", String.class);
        helper.consumerInjection(method, my, "foo");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("seda:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testConsumePropertyEndpointImplicit() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyConsumeBean my = new MyConsumeBean();
        my.setBarEndpoint("seda:bar");

        Method method = my.getClass().getMethod("bar", String.class);
        helper.consumerInjection(method, my, "bar");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("seda:bar", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testConsumePropertyOnEndpointImplicit() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyConsumeBean my = new MyConsumeBean();
        my.setBarEndpoint("seda:bar");

        Method method = my.getClass().getMethod("onBar", String.class);
        helper.consumerInjection(method, my, "bar");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("seda:bar", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public class MyConsumeBean {

        private String foo;
        private String barEndpoint;

        public String getFoo() {
            return foo;
        }

        public void setFoo(String foo) {
            this.foo = foo;
        }

        public String getBarEndpoint() {
            return barEndpoint;
        }

        public void setBarEndpoint(String barEndpoint) {
            this.barEndpoint = barEndpoint;
        }

        @Consume(property = "foo")
        public void consumeSomething(String body) {
            assertEquals("Hello World", body);
            template.sendBody("mock:result", body);
        }

        @Consume()
        public void foo(String body) {
            assertEquals("Hello World", body);
            template.sendBody("mock:result", body);
        }

        @Consume()
        public void onFoo(String body) {
            assertEquals("Hello World", body);
            template.sendBody("mock:result", body);
        }

        @Consume()
        public void bar(String body) {
            assertEquals("Hello World", body);
            template.sendBody("mock:result", body);
        }

        @Consume()
        public void onBar(String body) {
            assertEquals("Hello World", body);
            template.sendBody("mock:result", body);
        }
    }

}
