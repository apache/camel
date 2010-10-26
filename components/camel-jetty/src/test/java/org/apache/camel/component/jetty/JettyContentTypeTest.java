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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.MessageHelper;
import org.junit.Test;

/**
 * Unit test for content-type
 */
public class JettyContentTypeTest extends BaseJettyTest {

    protected void sendMessageWithContentType(boolean usingGZip) {
        Endpoint endpoint = context.getEndpoint("http://localhost:{{port}}/myapp/myservice");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("<order>123</order>");
        exchange.getIn().setHeader("User", "Claus");
        exchange.getIn().setHeader("SOAPAction", "test");
        exchange.getIn().setHeader("Content-Type", "text/xml");
        if (usingGZip) {
            exchange.getIn().setHeader(Exchange.CONTENT_ENCODING, "gzip");
        }
        template.send(endpoint, exchange);

        String body = exchange.getOut().getBody(String.class);
        assertEquals("<order>OK</order>", body);
        assertEquals("Get a wrong content-type ", MessageHelper.getContentType(exchange.getOut()), "text/xml");
    }

    @Test
    public void testSameContentType() throws Exception {
        sendMessageWithContentType(false);
    }
    
    @Test
    public void testContentTypeWithGZipEncoding() throws Exception {
        sendMessageWithContentType(true);
    }

    @Test
    public void testMixedContentType() throws Exception {
        Endpoint endpoint = context.getEndpoint("http://localhost:{{port}}/myapp/myservice");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("<order>123</order>");
        exchange.getIn().setHeader("Content-Type", "text/xml");
        template.send(endpoint, exchange);

        String body = exchange.getOut().getBody(String.class);
        assertEquals("FAIL", body);
        assertEquals("Get a wrong content-type ", MessageHelper.getContentType(exchange.getOut()), "text/plain");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("jetty:http://localhost:{{port}}/myapp/myservice").process(new MyBookService());
            }
        };
    }

    public class MyBookService implements Processor {
        public void process(Exchange exchange) throws Exception {
            String user = exchange.getIn().getHeader("User", String.class);
            String contentType = ExchangeHelper.getContentType(exchange);
            String body = exchange.getIn().getBody(String.class);
            String encoding = exchange.getIn().getHeader(Exchange.CONTENT_ENCODING, String.class);
            if (encoding != null) {
                exchange.getOut().setHeader(Exchange.CONTENT_ENCODING, encoding);
            }
            if ("Claus".equals(user) && contentType.startsWith("text/xml") && body.equals("<order>123</order>")) {
                assertEquals("test", exchange.getIn().getHeader("SOAPAction", String.class));
                exchange.getOut().setBody("<order>OK</order>");
                exchange.getOut().setHeader("Content-Type", "text/xml");
            } else {
                exchange.getOut().setBody("FAIL");
                exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "text/plain");
            }
        }
    }

}