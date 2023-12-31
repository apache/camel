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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Variable;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

public class MethodFilterVariableTest extends ContextTestSupport {
    @Test
    public void testSendMatchingMessage() throws Exception {
        String body = "<person name='James' city='London'/>";
        getMockEndpoint("mock:result").expectedBodiesReceived(body);

        template.sendBodyAndHeader("direct:start", ExchangePattern.InOut, body, "city", "London");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendNotMatchingMessage() throws Exception {
        String body = "<person name='Hiram' city='Tampa'/>";
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", ExchangePattern.InOut, body, "city", "Tampa");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                from("direct:start")
                        .setVariable("location", header("city"))
                        .filter().method("myBean", "matches").to("mock:result");
                // END SNIPPET: example
            }
        };
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();
        answer.bind("myBean", new MyBean());
        return answer;
    }

    // START SNIPPET: filter
    public static class MyBean {
        public boolean matches(@Variable("location") String location) {
            return "London".equals(location);
        }
    }
    // END SNIPPET: filter
}
