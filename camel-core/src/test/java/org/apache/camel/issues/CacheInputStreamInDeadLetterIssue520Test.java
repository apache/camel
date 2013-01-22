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
package org.apache.camel.issues;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class CacheInputStreamInDeadLetterIssue520Test extends ContextTestSupport {
    private int count;

    public void testSendingInputStream() throws Exception {
        InputStream message = new ByteArrayInputStream("<hello>Willem</hello>".getBytes());
        sendingMessage(message);
    }

    public void testSendingReader() throws Exception {
        StringReader message = new StringReader("<hello>Willem</hello>");
        sendingMessage(message);
    }

    public void testSendingSource() throws Exception {
        StreamSource message = new StreamSource(new StringReader("<hello>Willem</hello>"));
        sendingMessage(message);
    }

    private void sendingMessage(Object message) throws InterruptedException {
        count = 0;
        MockEndpoint mock = getMockEndpoint("mock:error");
        mock.expectedMessageCount(1);

        // having dead letter channel as the errorHandler in place makes exchanges to appear as completed from
        // the client point of view so that we don't count with any thrown exception here (the client side)
        template.sendBody("direct:start", message);

        assertEquals("The message should be delivered 4 times", count, 4);
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // enable stream caching
                context.setStreamCaching(true);

                // 0 delay for faster unit test
                errorHandler(deadLetterChannel("direct:errorHandler").maximumRedeliveries(3).redeliveryDelay(0));

                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        count++;
                        // Read the in stream from cache
                        String result = exchange.getIn().getBody(String.class);
                        assertEquals("Should read the inputstream out again", "<hello>Willem</hello>", result);
                        throw new Exception("Forced exception by unit test");
                    }
                });

                //Need to set the streamCaching for the deadLetterChannel
                from("direct:errorHandler").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String result = exchange.getIn().getBody(String.class);
                        assertEquals("Should read the inputstream out again", "<hello>Willem</hello>", result);
                    }
                }).to("mock:error");

            }
        };
    }

}
