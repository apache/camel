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
package org.apache.camel.component.cxf.jaxws;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.MessageHelper;
import org.apache.cxf.helpers.CastUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CxfRawMessageRouterTest extends CxfSimpleRouterTest {
    private String routerEndpointURI = "cxf://" + getRouterAddress() + "?" + SERVICE_CLASS + "&dataFormat=RAW";
    private String serviceEndpointURI = "cxf://" + getServiceAddress() + "?" + SERVICE_CLASS + "&dataFormat=RAW";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(routerEndpointURI).to("log:org.apache.camel?level=DEBUG").to(serviceEndpointURI).to("mock:result");
            }
        };
    }

    @Test
    public void testTheContentType() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.reset();
        result.expectedMessageCount(1);
        HelloService client = getCXFClient();
        client.echo("hello world");
        MockEndpoint.assertIsSatisfied(context);
        Map<?, ?> context
                = CastUtils.cast((Map<?, ?>) result.assertExchangeReceived(0).getIn().getHeaders().get("ResponseContext"));
        Map<?, ?> protocalHeaders = CastUtils.cast((Map<?, ?>) context.get("org.apache.cxf.message.Message.PROTOCOL_HEADERS"));
        assertTrue(protocalHeaders.get("content-type").toString().startsWith("[text/xml;"), "Should get a right content type");
        assertTrue(protocalHeaders.get("content-type").toString().indexOf("charset=") > 0,
                "Should get a right context type with a charset");
        assertEquals(200, context.get("org.apache.cxf.message.Message.RESPONSE_CODE"), "Should get the response code");
        assertTrue(result.assertExchangeReceived(0).getIn().getHeaders().get("content-type").toString().startsWith("text/xml;"),
                "Should get the content type");
        assertTrue(result.assertExchangeReceived(0).getIn().getHeaders().get("content-type").toString().indexOf("charset=") > 0,
                "Should get the content type");

    }

    @Test
    public void testTheContentTypeOnTheWire() throws Exception {
        Exchange exchange = template.send(getRouterAddress(), new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                                         + "<soap:Body><ns1:echo xmlns:ns1=\"http://jaxws.cxf.component.camel.apache.org/\">"
                                         + "<arg0 xmlns=\"http://jaxws.cxf.component.camel.apache.org/\">hello world</arg0>"
                                         + "</ns1:echo></soap:Body></soap:Envelope>");

            }

        });
        assertNotNull(MessageHelper.getContentType(exchange.getMessage()), "We should get the Content-Type here");
        assertTrue(MessageHelper.getContentType(exchange.getMessage()).startsWith("text/xml"), "Get wrong content type");
        assertNotNull(exchange.getMessage().getHeader("content-type"), "We should get the content-type here");
        String response = exchange.getMessage().getBody(String.class);
        assertNotNull(response, "Response should not be null");
        assertTrue(response.indexOf("echo hello world") > 0, "We should get right return result");
    }
}
