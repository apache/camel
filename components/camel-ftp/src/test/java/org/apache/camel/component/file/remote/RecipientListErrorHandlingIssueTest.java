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
package org.apache.camel.component.file.remote;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class RecipientListErrorHandlingIssueTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        // use a wrong password so we cannot login and get an exception so we
        // can test that the error handler kick in and we know which endpoint failed
        return "ftp://admin@localhost:" + getPort() + "/recipientlist?password=denied";
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testUsingInterceptor() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).handled(true).to("mock:error");

                interceptSendToEndpoint("(ftp|direct):.*").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String target = exchange.getIn().getHeader(Exchange.INTERCEPTED_ENDPOINT, String.class);
                        exchange.getIn().setHeader("target", target);
                    }
                });

                from("direct:start").recipientList(header("foo"));

                from("direct:foo").setBody(constant("Bye World")).to("mock:foo");
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:error").message(0).header("target").isEqualTo(getFtpUrl());

        String foo = "direct:foo," + getFtpUrl();

        Map<String, Object> headers = new HashMap<>();
        headers.put("foo", foo);
        headers.put(Exchange.FILE_NAME, "hello.txt");

        template.sendBodyAndHeaders("direct:start", "Hello World", headers);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUsingExistingHeaders() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).handled(true).to("mock:error");

                from("direct:start").recipientList(header("foo"));

                from("direct:foo").setBody(constant("Bye World")).to("mock:foo");
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:foo").message(0).header(Exchange.TO_ENDPOINT).isEqualTo("mock://foo");
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:error").message(0).header(Exchange.FAILURE_ENDPOINT).isEqualTo(getFtpUrl());

        String foo = "direct:foo," + getFtpUrl();

        Map<String, Object> headers = new HashMap<>();
        headers.put("foo", foo);
        headers.put(Exchange.FILE_NAME, "hello.txt");

        template.sendBodyAndHeaders("direct:start", "Hello World", headers);

        assertMockEndpointsSatisfied();
    }
}