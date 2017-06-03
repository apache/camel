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
package org.apache.camel.language.simple;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

public class SimpleMessageHistoryTest extends ContextTestSupport {

    public void testMessageHistory() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        String result = getMockEndpoint("mock:result").getExchanges().get(0).getIn().getBody(String.class);
        assertNotNull(result);

        assertTrue(result.contains("mock:a"));
        assertTrue(result.contains("mock:b"));
        assertTrue(result.contains("transform[simple{${messageHistory}}"));
        assertTrue(result.contains("Hello World"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("mock:a")
                    .log("${messageHistory}")
                    .to("mock:b")
                    .log("${messageHistory}")
                    .transform().simple("${messageHistory}")
                    .to("mock:result");
            }
        };
    }
}
