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
package org.apache.camel.component.cxf.transport;

import jakarta.xml.ws.Endpoint;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test the CamelDestination with whole CXF context
public class JaxWSCamelDestinationTest extends JaxWSCamelTestSupport {
    private Endpoint endpoint;

    @AfterEach
    public void stopEndpoint() {
        if (endpoint != null) {
            endpoint.stop();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            public void configure() throws Exception {

                from("direct:start").to("direct:endpoint");

            }
        };
    }

    @Test
    public void testDestinationContentType() {
        // publish the endpoint
        endpoint = publishSampleWS("direct:endpoint");
        Exchange exchange = template.request("direct:start", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(REQUEST);
            }

        });
        assertEquals("text/xml; charset=UTF-8", exchange.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class));
        assertTrue(exchange.getMessage().getBody(String.class).indexOf("something!") > 0);
    }

    @Test
    public void testDestinationWithGzip() {
        // publish the endpoint
        endpoint = publishSampleWSWithGzipEnabled("direct:endpoint");
        Exchange exchange = template.request("direct:start", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(REQUEST);
                exchange.getIn().setHeader("Accept-Encoding", "gzip");
            }

        });
        assertEquals("gzip", exchange.getMessage().getHeader(Exchange.CONTENT_ENCODING, String.class));
    }
}
