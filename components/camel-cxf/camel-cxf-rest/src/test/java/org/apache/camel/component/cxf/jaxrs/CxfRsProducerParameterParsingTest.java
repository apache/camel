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
package org.apache.camel.component.cxf.jaxrs;

import jakarta.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CxfRsProducerParameterParsingTest extends CamelTestSupport {

    private static final int PORT = CXFTestSupport.getPort7();

    @Test
    public void testValuelessQueryParameter() {
        Exchange exchange = sendWithQuery("flag&key=value");

        assertNull(exchange.getException(), "Should not throw on valueless query parameter");
        Response response = (Response) exchange.getMessage().getBody();
        assertEquals(204, response.getStatus());
    }

    @Test
    public void testQueryParameterWithEmptyValue() {
        Exchange exchange = sendWithQuery("name=&key=value");

        assertNull(exchange.getException(), "Should not throw on empty-value query parameter");
        Response response = (Response) exchange.getMessage().getBody();
        assertEquals(204, response.getStatus());
    }

    @Test
    public void testMultipleValuelessQueryParameters() {
        Exchange exchange = sendWithQuery("debug&trace&verbose");

        assertNull(exchange.getException(), "Should not throw on multiple valueless query parameters");
        Response response = (Response) exchange.getMessage().getBody();
        assertEquals(204, response.getStatus());
    }

    private Exchange sendWithQuery(String queryString) {
        return context.createProducerTemplate().send(
                "cxfrs://http://localhost:" + PORT + "/CxfRsProducerParameterParsingTest",
                exchange -> {
                    exchange.setPattern(ExchangePattern.InOut);
                    Message inMessage = exchange.getIn();
                    inMessage.setHeader(Exchange.HTTP_METHOD, "GET");
                    inMessage.setHeader(Exchange.HTTP_PATH, "/CxfRsProducerParameterParsingTest/");
                    inMessage.setHeader(Exchange.HTTP_QUERY, queryString);
                    inMessage.setHeader(Exchange.CONTENT_TYPE, "application/text");
                    inMessage.setBody(null);
                });
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("undertow://http://localhost:%s/CxfRsProducerParameterParsingTest/?matchOnUriPrefix=true", PORT)
                        .to("mock:result");
            }
        };
    }
}
