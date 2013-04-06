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

import java.io.ByteArrayInputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.cxf.converter.CxfPayloadConverter;
import org.apache.cxf.binding.soap.SoapHeader;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Unit test for setting arbitrary payload in PAYLOAD mode
 */
public class CxfDispatchPayloadTest extends CxfDispatchTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/CxfDispatchPayloadBeans.xml");
    }
    
    @Test
    public void testDispatchPayload() throws Exception {
        final String name = "Tila";
        Exchange exchange = sendJaxWsDispatchPayload(name, false);
        assertEquals("The request should be handled sucessfully ", exchange.isFailed(), false);
        
        org.apache.camel.Message response = exchange.getOut();
        assertNotNull("The response must not be null ", response);
        
        String value = decodeResponseFromPayload((CxfPayload<?>)response.getBody(CxfPayload.class), exchange);
        assertTrue("The response must match the request ", value.endsWith(name));
    }
    
    @Test
    public void testDispatchPayloadOneway() throws Exception {
        final String name = "Tila";
        Exchange exchange = sendJaxWsDispatchPayload(name, true);
        assertEquals("The request should be handled sucessfully ", exchange.isFailed(), false);
        
        org.apache.camel.Message response = exchange.getOut();
        assertNotNull("The response must not be null ", response);
        
        assertNull("The response must be null ", response.getBody());
    }

    
    private Exchange sendJaxWsDispatchPayload(final String name, final boolean oneway) {
        Exchange exchange = template.send("direct:producer", new Processor() {
            public void process(final Exchange exchange) {
                CxfPayload<SoapHeader> request = encodeRequestInPayload(oneway ? PAYLOAD_ONEWAY_TEMPLATE : PAYLOAD_TEMPLATE, 
                                                            name, exchange);
                exchange.getIn().setBody(request, CxfPayload.class);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAMESPACE, DISPATCH_NS);                                    
                // set the operation for oneway; otherwise use the default operation                
                if (oneway) {
                    exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, INVOKE_ONEWAY_NAME);
                }
            }
        });
        return exchange;
    }

    private static <T> CxfPayload<T> encodeRequestInPayload(String form, String name, Exchange exchange) {
        String payloadstr = String.format(form, name);
        CxfPayload<T> payload = null;
        try {
            Document doc = getDocumentBuilderFactory().newDocumentBuilder()
                                .parse(new ByteArrayInputStream(payloadstr.getBytes("utf-8")));
            payload = CxfPayloadConverter.documentToCxfPayload(doc, exchange);
        } catch (Exception e) {
            // ignore and let it fail
        }
        return payload;
    }

    private <T> String decodeResponseFromPayload(CxfPayload<T> payload, Exchange exchange) {
        String value = null;
        NodeList nodes = CxfPayloadConverter.cxfPayloadToNodeList(payload, exchange);
        if (nodes != null && nodes.getLength() == 1 && nodes.item(0) instanceof Element) {
            value = getResponseType((Element)nodes.item(0));
        }
        return value;
    }
}