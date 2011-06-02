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
import java.io.InputStream;
import org.w3c.dom.Document;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;




/**
 * Unit test for setting arbitrary payload in MESSAGE mode
 */
public class CxfDispatchMessageTest extends CxfDispatchTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/CxfDispatchMessageBeans.xml");
    }

    @Test
    public void testDipatchMessage() throws Exception {
        final String name = "Tila";
        Exchange exchange = sendJaxWsDispatchMessage(name, false);
        assertEquals("The request should be handled sucessfully ", exchange.isFailed(), false);
        
        org.apache.camel.Message response = exchange.getOut();
        assertNotNull("The response message must not be null ", response);
        
        String value = decodeResponseFromMessage(response.getBody(InputStream.class), exchange);
        assertTrue("The response body must match the request ", value.endsWith(name));
    }
    
    @Test
    public void testDipatchMessageOneway() throws Exception {
        final String name = "Tila";
        Exchange exchange = sendJaxWsDispatchMessage(name, true);
        assertEquals("The request should be handled sucessfully ", exchange.isFailed(), false);
        
        org.apache.camel.Message response = exchange.getOut();
        assertNotNull("The response message must not be null ", response);
        
        assertNull("The response body must be null ", response.getBody());
    }


    protected Exchange sendJaxWsDispatchMessage(final String name, final boolean oneway) {
        Exchange exchange = template.send("direct:producer", new Processor() {
            public void process(final Exchange exchange) {
                InputStream request = encodeRequestInMessage(oneway ? MESSAGE_ONEWAY_TEMPLATE : MESSAGE_TEMPLATE, name, exchange);
                exchange.getIn().setBody(request, InputStream.class);
                // set the operation for oneway; otherwise use the default operation                
                if (oneway) {
                    exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, INVOKE_ONEWAY_NAME);
                }
            }
        });
        return exchange;
    }
    
    private static InputStream encodeRequestInMessage(String form, String name, Exchange exchange) {
        String payloadstr = String.format(form, name);
        InputStream message = null;
        try {
            message = new ByteArrayInputStream(payloadstr.getBytes("utf-8"));
        } catch (Exception e) {
            // ignore and let it fail
        }
        return message;
    }

    private String decodeResponseFromMessage(InputStream message, Exchange exchange) {
        String value = null;
        try {
            Document doc = getDocumentBuilderFactory().newDocumentBuilder().parse(message);
            value = getResponseType(doc.getDocumentElement());
        } catch (Exception e) {
            // ignore and let it fail
        }
        return value;
    }
}