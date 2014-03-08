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
package org.apache.camel.component.jetty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultHeaderFilterStrategy;
import org.apache.camel.util.IOHelper;

import org.junit.Test;

public class HttpRoundtripHeaderTest extends BaseJettyTest {
    protected final String uri = "http://localhost:" + getPort() + "/WhichWillGetCloseException";
    protected final String jettyUri = "jetty:" + uri;
    protected final String outputText = ":output";
    protected String inputText = "input";
    protected String expectedText = inputText + outputText;

    // http://issues.apache.org/activemq/browse/CAMEL-324
    @Test
    public void testHttpRoundTripHeaders() throws Exception {
        MockEndpoint mockEndpoint = resolveMandatoryEndpoint("mock:results", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        InputStream answer = (InputStream) template.requestBody(uri, inputText);

        verifyMockGotExpectedText(mockEndpoint, expectedText);

        // read the response data
        String lastLine = readLastLine(answer);

        assertNotNull("last response line", lastLine);
        assertEquals("response matches: " + expectedText, expectedText, lastLine);
    }

    @Test
    public void testHttpRoundTripHeadersWithNoIgnoredHeaders() throws Exception {
        MockEndpoint mockEndpoint = resolveMandatoryEndpoint("mock:results", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        JettyHttpEndpoint endpoint = context.getEndpoint(jettyUri, JettyHttpEndpoint.class);
        // override the default set of ignored headers which includes Content-Length
        ((DefaultHeaderFilterStrategy)endpoint.getHeaderFilterStrategy()).setOutFilter(null);

        // read the response data
        InputStream answer = (InputStream) template.requestBody(uri, inputText);
        verifyMockGotExpectedText(mockEndpoint, expectedText);

        String lastLine = readLastLine(answer);
        assertNotNull("last response line", lastLine);
        
        // Content-Length from request will truncate the output to just the inputText
        assertEquals("response matches: " + inputText, inputText, lastLine);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                Processor processor = new Processor() {
                    public void process(Exchange exchange) {
                        String input = (String) exchange.getIn().getBody();
                        // append some text to invalidate Context-Length
                        // for the http reply
                        exchange.getIn().setBody(input + outputText);
                    }
                };

                // the unmarshaller does a copy from in message to out
                // including all headers
                from(jettyUri).unmarshal().string().process(processor).to("mock:results");
            }
        };
    }

    private void verifyMockGotExpectedText(MockEndpoint mockEndpoint, String expected) throws InterruptedException {
        mockEndpoint.assertIsSatisfied();
        List<Exchange> list = mockEndpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        assertNotNull("exchange", exchange);
        Message in = exchange.getIn();
        assertNotNull("in", in);
        Map<String, Object> headers = in.getHeaders();
        assertTrue("no headers are propagated", !headers.isEmpty());
        assertEquals("body has expectedText:" + expected, expected, in.getBody());
    }

    private String readLastLine(InputStream answer) throws IOException {
        String lastLine = null;
        BufferedReader reader = IOHelper.buffered(new InputStreamReader(answer));
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            lastLine = line;
            log.info("Read: " + line);
        }
        reader.close();
        return lastLine;
    }
}
