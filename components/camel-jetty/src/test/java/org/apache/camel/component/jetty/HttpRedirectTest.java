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
package org.apache.camel.component.jetty;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.junit.Test;

public class HttpRedirectTest extends BaseJettyTest {

    @Test
    public void testHttpRedirect() throws Exception {
        try {
            template.requestBody("http://localhost:{{port}}/test", "Hello World", String.class);
            fail("Should have thrown an exception");
        } catch (RuntimeCamelException e) {
            HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            assertEquals(301, cause.getStatusCode());
            assertEquals(true, cause.isRedirectError());
            assertEquals(true, cause.hasRedirectLocation());
            assertEquals("http://localhost:" + getPort() + "/test", cause.getUri());
            assertEquals("http://localhost:" + getPort() + "/newtest", cause.getRedirectLocation());
        }
    }

    @Test
    public void testHttpRedirectFromCamelRoute() throws Exception {
        MockEndpoint errorEndpoint = context.getEndpoint("mock:error", MockEndpoint.class);
        errorEndpoint.expectedMessageCount(1);
        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(0);
        try {
            template.requestBody("direct:start", "Hello World", String.class);
            fail("Should have thrown an exception");
        } catch (RuntimeCamelException e) {
            HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            assertEquals(302, cause.getStatusCode());
        }
        errorEndpoint.assertIsSatisfied();
        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty://http://localhost:{{port}}/test").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 301);
                        exchange.getOut().setHeader("location", "http://localhost:" + getPort() + "/newtest");
                    }
                });
                from("jetty://http://localhost:{{port}}/remove").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 302);
                    }
                });

                from("direct:start").onException(HttpOperationFailedException.class).to("mock:error").end().to("http://localhost:{{port}}/remove?throwExceptionOnFailure=true")
                    .to("mock:result");

            }
        };
    }
}
