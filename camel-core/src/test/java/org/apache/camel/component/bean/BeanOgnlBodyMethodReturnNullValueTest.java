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

/**
 *
 */
public class BeanOgnlBodyMethodReturnNullValueTest extends ContextTestSupport {

    public void testBothValue() {
        ExamplePojo fooBar = new ExamplePojo();
        fooBar.setFoo("foo1");
        fooBar.setBar("bar2");

        String result = template.requestBody("direct:start", fooBar, String.class);
        assertEquals("foo: foo1; bar: bar2", result);
    }

    public void testNullValue() {
        ExamplePojo fooBar = new ExamplePojo();
        fooBar.setFoo(null);
        fooBar.setBar("test");

        String result = template.requestBody("direct:start", fooBar, String.class);
        assertEquals("foo: null; bar: test", result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .bean(new ExampleBean(), "doWithFooBar(${body.foo}, ${body.bar})");
            }
        };
    }

    public static class ExampleBean {
        public String doWithFooBar(String foo, String bar) {
            return String.format("foo: %s; bar: %s", foo, bar);
        }
    }

    public static class ExamplePojo {
        private String foo;
        private String bar;

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
    }
}
