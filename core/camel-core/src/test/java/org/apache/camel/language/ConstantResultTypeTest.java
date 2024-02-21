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
package org.apache.camel.language;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class ConstantResultTypeTest extends ContextTestSupport {

    @Test
    public void testConstant() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        // headers will be wrapper types
        getMockEndpoint("mock:result").message(0).header("foo").isInstanceOf(Integer.class);
        getMockEndpoint("mock:result").message(0).header("bar").isInstanceOf(Boolean.class);
        getMockEndpoint("mock:result").message(0).header("baz").isInstanceOf(String.class);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .setHeader("foo").constant("123", int.class)
                        .setHeader("bar").constant("true", boolean.class)
                        .setHeader("baz").constant("456")
                        .to("mock:result");
            }
        };
    }
}
