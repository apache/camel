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

package org.apache.camel.component.http;

import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.apache.camel.component.http.HttpMethods.HTTP_METHOD;
import static org.apache.camel.component.http.HttpMethods.POST;


public class HttpPostWithBodyTest extends ContextTestSupport {
    protected String expectedText = "Method Not Allowed";

    public void testHttpPostWithError() throws Exception {

        Exchange exchange = template.send("direct:start", new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("q=test1234");
            }

        });

        assertNotNull("exchange", exchange);
        assertTrue("The exchange should be failed", exchange.isFailed());

        // get the ex message
        HttpOperationFailedException exception = (HttpOperationFailedException)exchange.getException();
        assertNotNull("exception", exception);

        int statusCode = exception.getStatusCode();
        assertTrue("The response code should not be 200", statusCode != 200);

        String reason = exception.getStatusLine().getReasonPhrase();

        assertNotNull("Should have a body!", reason);

        assertTrue("body should contain: " + expectedText, reason.contains(expectedText));

    }

    public void testHttpPostRecovery() throws Exception {

        MockEndpoint mockResult = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        MockEndpoint mockRecovery = resolveMandatoryEndpoint("mock:recovery", MockEndpoint.class);
        mockRecovery.expectedMessageCount(1);
        mockResult.expectedMessageCount(0);

        template.send("direct:reset", new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("q=activemq");
            }

        });

        mockRecovery.assertIsSatisfied();
        mockResult.assertIsSatisfied();
        List<Exchange> list = mockRecovery.getReceivedExchanges();
        Exchange exchange = list.get(0);
        assertNotNull("exchange", exchange);

        Message in = exchange.getIn();
        assertNotNull("in", in);

        Map<String, Object> headers = in.getHeaders();

        log.debug("Headers: " + headers);
        assertTrue("Should be more than one header but was: " + headers, headers.size() > 0);

        String body = in.getBody(String.class);

        log.debug("Body: " + body);
        assertNotNull("Should have a body!", body);
        assertTrue("body should contain: <html>", body.contains("<html>"));

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").setHeader(HTTP_METHOD, POST).to("http://www.google.com");
                from("direct:reset").setHeader(HTTP_METHOD, POST).
                    errorHandler(deadLetterChannel("direct:recovery").maximumRedeliveries(1)).to("http://www.google.com").to("mock:result");
                from("direct:recovery").setHeader(HTTP_METHOD, GET).to("http://www.google.com").to("mock:recovery");
            }
        };
    }
}
