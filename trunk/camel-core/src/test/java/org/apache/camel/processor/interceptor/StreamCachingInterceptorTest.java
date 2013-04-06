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
package org.apache.camel.processor.interceptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.BytesSource;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.StringSource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxp.XmlConverter;

public class StreamCachingInterceptorTest extends ContextTestSupport {

    private static final String MESSAGE = "<hello>world!</hello>";
    private static final String BODY_TYPE = "body.type";
    
    private MockEndpoint a;
    private MockEndpoint b;
    private final XmlConverter converter = new XmlConverter();

    public void testConvertStreamSourceWithRouteBuilderStreamCaching() throws Exception {
        a.expectedMessageCount(1);

        StreamSource message = new StreamSource(new StringReader(MESSAGE));
        template.sendBody("direct:a", message);

        assertMockEndpointsSatisfied();
        assertTrue(a.assertExchangeReceived(0).getIn().getBody() instanceof StreamCache);
    }
    
    public void testNoConversionForOtherXmlSourceTypes() throws Exception {
        a.expectedMessageCount(3);

        send(converter.toDOMSource(MESSAGE));
        send(new StringSource(MESSAGE));
        send(new BytesSource(MESSAGE.getBytes()));

        assertMockEndpointsSatisfied();
        for (Exchange exchange : a.getExchanges()) {
            assertFalse(exchange.getIn().getHeader(BODY_TYPE, Class.class).toString() + " shouldn't have been converted to StreamCache", 
                        exchange.getIn().getBody() instanceof StreamCache);
        }        
    }

    private void send(Source source) {
        template.sendBodyAndHeader("direct:a", source, BODY_TYPE, source.getClass());
    }

    public void testConvertStreamSourceWithRouteOnlyStreamCaching() throws Exception {
        b.expectedMessageCount(1);

        StreamSource message = new StreamSource(new StringReader(MESSAGE));
        template.sendBody("direct:b", message);

        assertMockEndpointsSatisfied();
        assertTrue(b.assertExchangeReceived(0).getIn().getBody() instanceof StreamCache);        
        assertEquals(b.assertExchangeReceived(0).getIn().getBody(String.class), MESSAGE);
    }

    public void testConvertInputStreamWithRouteBuilderStreamCaching() throws Exception {
        a.expectedMessageCount(1);

        InputStream message = new ByteArrayInputStream(MESSAGE.getBytes());
        template.sendBody("direct:a", message);

        assertMockEndpointsSatisfied();
        assertTrue(a.assertExchangeReceived(0).getIn().getBody() instanceof StreamCache);
        assertEquals(a.assertExchangeReceived(0).getIn().getBody(String.class), MESSAGE);
    }

    public void testIgnoreAlreadyRereadable() throws Exception {
        a.expectedMessageCount(1);

        template.sendBody("direct:a", MESSAGE);

        assertMockEndpointsSatisfied();
        assertTrue(a.assertExchangeReceived(0).getIn().getBody() instanceof String);
    }

    public void testStreamCachingInterceptorToString() {
        StreamCachingInterceptor cache = new StreamCachingInterceptor();
        assertNotNull(cache.toString());

        StreamCaching caching = new StreamCaching();
        assertNotNull(caching.toString());
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        a = getMockEndpoint("mock:a");
        b = getMockEndpoint("mock:b");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                //Stream caching for a single route...
                //START SNIPPET: route
                from("direct:a").streamCaching().to("mock:a");
                //END SNIPPET: route

                //... or for all the following routes in this builder
                //START SNIPPET: routebuilder
                context.setStreamCaching(true);
                from("direct:b").to("mock:b");
                //END SNIPPET: routebuilder
            }
        };
    }

}
