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
package org.apache.camel.issues;

import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class SetBodyTryCatchIssueTest extends ContextTestSupport {

    @Test
    public void testSetBody() throws Exception {
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedBodiesReceived("123");

        Object out = template.requestBody("direct:start", "Hello World");
        assertEquals("123", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").setHeader("foo", constant("123")).doTry().setHeader("bar", constant("456")).to("mock:bar").bean(SetBodyTryCatchIssueTest.class, "doSomething")
                    .doCatch(IllegalArgumentException.class)
                    // empty block
                    .end().setBody(header("foo")).to("mock:result");
            }
        };
    }

    public static void doSomething(Exchange exchange) throws Exception {
        Map<String, Object> headers = exchange.getIn().getHeaders();

        exchange.getOut().setBody("Bye World");
        // we copy the headers by mistake by setting it as a reference from the
        // IN
        // but we should ideally do as below instead
        // but we want to let Camel handle this situation as well, otherwise
        // headers may appear as lost
        // exchange.getOut().getHeaders().putAll(headers);
        exchange.getOut().setHeaders(headers);

        throw new IllegalArgumentException("Forced");
    }

}
