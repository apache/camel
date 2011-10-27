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
package org.apache.camel.processor;

import javax.naming.Context;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class MethodFilterTest extends ContextTestSupport {
    public void testSendMatchingMessage() throws Exception {
        String body = "<person name='James' city='London'/>";
        getMockEndpoint("mock:result").expectedBodiesReceived(body);

        template.sendBodyAndHeader("direct:start", ExchangePattern.InOut, body, "foo", "London");

        assertMockEndpointsSatisfied();
    }

    public void testSendNotMatchingMessage() throws Exception {
        String body = "<person name='Hiram' city='Tampa'/>";
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", ExchangePattern.InOut, body, "foo", "Tampa");

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                from("direct:start").
                        filter().method("myBean", "matches").
                        to("mock:result");
                // END SNIPPET: example
            }
        };
    }

    @Override
    protected Context createJndiContext() throws Exception {
        Context context = super.createJndiContext();
        context.bind("myBean", new MyBean());
        return context;
    }

    // START SNIPPET: filter
    public static class MyBean {
        public boolean matches(@Header("foo")String location) {
            return "London".equals(location);
        }
    }
    // END SNIPPET: filter
}