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
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

public class BeanPropertiesFunctionTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry registry = super.createRegistry();
        registry.bind("fooBean", new BeanPropertiesFunctionTest.FooBean());
        registry.bind("barBean", new BeanPropertiesFunctionTest.BarBean());
        return registry;
    }

    @Test
    public void testParseEndpoint() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:{{bean:fooBean.foo}}").to("mock:{{bean:barBean.bar}}");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedMessageCount(1);

        template.sendBody("direct:foo", "Hello World");
        assertMockEndpointsSatisfied();
    }

    public static class FooBean {

        public String foo() {
            return "foo";
        }
    }

    public static class BarBean {

        public String bar() {
            return "bar";
        }
    }
}
