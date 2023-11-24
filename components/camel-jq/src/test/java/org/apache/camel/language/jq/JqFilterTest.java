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
package org.apache.camel.language.jq;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class JqFilterTest extends JqTestSupport {
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .filter().jq(".foo == \"bar\"")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testFilter() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(MAPPER.createObjectNode().put("foo", "bar"));

        template.sendBody("direct:start", MAPPER.createObjectNode().put("foo", "baz"));
        template.sendBody("direct:start", MAPPER.createObjectNode().put("foo", "bar"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testFilterStringJSon() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("{\"foo\": \"bar\"}");

        template.sendBody("direct:start", "{\"foo\": \"baz\"}");
        template.sendBody("direct:start", "{\"foo\": \"bar\"}");

        MockEndpoint.assertIsSatisfied(context);
    }
}
