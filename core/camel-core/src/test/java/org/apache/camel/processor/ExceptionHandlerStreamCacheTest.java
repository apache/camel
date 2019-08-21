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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.junit.Before;
import org.junit.Test;

/**
 * Test cases for dealing with stream types in an exception handler
 */
public class ExceptionHandlerStreamCacheTest extends ContextTestSupport {

    private MockEndpoint successEndpoint;
    private MockEndpoint exceptionEndpoint;

    @Test
    public void testSendError() throws Exception {
        doTestInputStreamPayload("error");
    }

    private void doTestInputStreamPayload(String message) throws InterruptedException, IOException {
        successEndpoint.expectedMessageCount(0);
        exceptionEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start", new ByteArrayInputStream(message.getBytes()));

        successEndpoint.assertIsSatisfied();
        exceptionEndpoint.assertIsSatisfied();

        InputStream body = (InputStream)exceptionEndpoint.getExchanges().get(0).getIn().getBody();
        assertEquals("Ensure message re-readability in the exception handler", message, new String(IOConverter.toBytes(body)));
    }

    @Test
    public void testSendErrorXml() throws Exception {
        doTestXmlPayload("<error/>");
    }

    private void doTestXmlPayload(String xml) throws InterruptedException, TransformerException {
        successEndpoint.expectedMessageCount(0);
        exceptionEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start", new StreamSource(new ByteArrayInputStream(xml.getBytes())));

        successEndpoint.assertIsSatisfied();
        exceptionEndpoint.assertIsSatisfied();

        StreamSource body = (StreamSource)exceptionEndpoint.getExchanges().get(0).getIn().getBody();
        assertEquals("Ensure message re-readability in the exception handler", xml, new XmlConverter().toString(body, null));
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        exceptionEndpoint = getMockEndpoint("mock:exception");
        successEndpoint = getMockEndpoint("mock:success");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            public void configure() {
                // enable support for stream caching
                context.setStreamCaching(true);

                onException(Exception.class).handled(true).to("mock:exception");

                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String message = exchange.getIn().getBody(String.class);

                        if (message.contains("error")) {
                            throw new RuntimeException(message);
                        }
                    }
                }).to("mock:success");
            }
        };
    }
}
