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
package org.apache.camel.component.cxf;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.MessageHelper;
import org.apache.cxf.helpers.CastUtils;
import org.junit.Test;

public class CxfRawMessageRouterTest extends CxfSimpleRouterTest {
    private String routerEndpointURI = "cxf://" + getRouterAddress() + "?" + SERVICE_CLASS + "&dataFormat=MESSAGE";
    private String serviceEndpointURI = "cxf://" + getServiceAddress() + "?" + SERVICE_CLASS + "&dataFormat=MESSAGE";
    
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(routerEndpointURI).to("log:org.apache.camel?level=DEBUG").to(serviceEndpointURI).to("mock:result");
            }
        };
    }
    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @Test
    public void testTheContentType() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.reset();
        result.expectedMessageCount(1);
        HelloService client = getCXFClient();
        client.echo("hello world");
        assertMockEndpointsSatisfied();        
        Map<?, ?> context = CastUtils.cast((Map<?, ?>)result.assertExchangeReceived(0).getIn().getHeaders().get("ResponseContext"));
        Map<?, ?> protocalHeaders = CastUtils.cast((Map<?, ?>) context.get("org.apache.cxf.message.Message.PROTOCOL_HEADERS"));
        assertTrue("Should get a right content type", protocalHeaders.get("content-type").toString().startsWith("[text/xml;"));
        assertTrue("Should get a right context type with a charset",  protocalHeaders.get("content-type").toString().indexOf("charset=") > 0);
        assertEquals("Should get the response code ", context.get("org.apache.cxf.message.Message.RESPONSE_CODE"), 200);
        assertTrue("Should get the content type", result.assertExchangeReceived(0).getIn().getHeaders().get("content-type").toString().startsWith("text/xml;")); 
        assertTrue("Should get the content type", result.assertExchangeReceived(0).getIn().getHeaders().get("content-type").toString().indexOf("charset=") > 0); 
        
    }
    
    @Test
    public void testTheContentTypeOnTheWire() throws Exception {
        Exchange exchange = template.send(getRouterAddress(),  new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" 
                                         + "<soap:Body><ns1:echo xmlns:ns1=\"http://cxf.component.camel.apache.org/\">"
                                         + "<arg0 xmlns=\"http://cxf.component.camel.apache.org/\">hello world</arg0>"
                                         + "</ns1:echo></soap:Body></soap:Envelope>");
                
            }

        });
        assertNotNull("We should get the Content-Type here", MessageHelper.getContentType(exchange.getOut()));
        assertTrue("Get wrong content type", MessageHelper.getContentType(exchange.getOut()).startsWith("text/xml"));
        assertNotNull("We should get the content-type here", exchange.getOut().getHeader("content-type"));
        String response = exchange.getOut().getBody(String.class);
        assertNotNull("Response should not be null", response);
        assertTrue("We should get right return result", response.indexOf("echo hello world") > 0);
    }
}
