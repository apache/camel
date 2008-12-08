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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test for content-type
 */
public class JettyContentTypeTest extends ContextTestSupport {

    public void testSameContentType() throws Exception {
        Endpoint endpoint = context.getEndpoint("http://localhost:8080/myapp/myservice");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("<order>123</order>");
        exchange.getIn().setHeader("user", "Claus");
        exchange.getIn().setHeader("content-type", "text/xml");
        template.send(endpoint, exchange);

        String body = exchange.getOut().getBody(String.class);
        assertEquals("<order>OK</order>", body);
        assertOutMessageHeader(exchange, "content-type", "text/xml");
    }

    public void testMixedContentType() throws Exception {
        Endpoint endpoint = context.getEndpoint("http://localhost:8080/myapp/myservice");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("<order>123</order>");
        exchange.getIn().setHeader("Content-Type", "text/xml");
        template.send(endpoint, exchange);

        String body = exchange.getOut().getBody(String.class);
        assertEquals("FAIL", body);
        assertOutMessageHeader(exchange, "Content-Type", "text/plain");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("jetty:http://localhost:8080/myapp/myservice").process(new MyBookService());
            }
        };
    }

    public class MyBookService implements Processor {
        public void process(Exchange exchange) throws Exception {
            if (exchange.getIn().getHeader("user") != null) {
                exchange.getOut().setBody("<order>OK</order>");
            } else {
                exchange.getOut().setBody("FAIL");
                exchange.getOut().setHeader("Content-Type", "text/plain");
            }
        }
    }

}