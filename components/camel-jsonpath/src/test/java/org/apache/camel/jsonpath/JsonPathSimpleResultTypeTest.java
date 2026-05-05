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
package org.apache.camel.jsonpath;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class JsonPathSimpleResultTypeTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setHeader("age", simple("${int:jsonpath($.age)}"))
                        .setHeader("id", simple("${long:jsonpath($.id)}"))
                        .setHeader("name", simple("${string:jsonpath($.name)}"))
                        .setHeader("vip", simple("${boolean:jsonpath($.vip)}"))
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testInlinedResultType() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedHeaderReceived("age", Integer.valueOf("42"));
        getMockEndpoint("mock:result").message(0).header("age").isInstanceOf(Integer.class);
        getMockEndpoint("mock:result").expectedHeaderReceived("id", Long.valueOf("1234567890"));
        getMockEndpoint("mock:result").message(0).header("id").isInstanceOf(Long.class);
        getMockEndpoint("mock:result").expectedHeaderReceived("name", "scott");
        getMockEndpoint("mock:result").expectedHeaderReceived("vip", Boolean.valueOf("true"));
        getMockEndpoint("mock:result").message(0).header("vip").isInstanceOf(Boolean.class);

        template.sendBody("direct:start", "{\"id\": \"1234567890\", \"age\": \"42\", \"name\": \"scott\", \"vip\": \"true\"}");

        MockEndpoint.assertIsSatisfied(context);
    }

}
