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
package org.apache.camel.component.cxf.common.header;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import junit.framework.TestCase;

import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.message.Message;
import org.junit.Test;

public class CxfHeaderHelperTest extends TestCase {

    private DefaultCamelContext context;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = new DefaultCamelContext();
        context.start();
    }

    @Test
    public void testPropagateCamelToCxf() {
        Exchange exchange = new DefaultExchange(context);

        exchange.getIn().setHeader("soapAction", "urn:hello:world");
        exchange.getIn().setHeader("MyFruitHeader", "peach");
        exchange.getIn().setHeader("MyBrewHeader", Arrays.asList("cappuccino", "espresso"));
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/xml");
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, "200");
        exchange.getIn().setHeader(Exchange.HTTP_URI, "/hello/cxf");
        exchange.getIn().setHeader(Exchange.HTTP_METHOD, "GET");
        exchange.getIn().setHeader(Exchange.HTTP_PATH, "/hello/cxf");
        Map<String, Object> requestContext = Collections.singletonMap("request", "true");
        Map<String, Object> responseContext = Collections.singletonMap("response", "true");
        exchange.getIn().setHeader(Client.REQUEST_CONTEXT, requestContext);
        exchange.getIn().setHeader(Client.RESPONSE_CONTEXT, responseContext);
        org.apache.cxf.message.Message cxfMessage = new org.apache.cxf.message.MessageImpl();
        
        CxfHeaderHelper.propagateCamelToCxf(new DefaultHeaderFilterStrategy(), 
                                            exchange.getIn().getHeaders(), cxfMessage, exchange);

        assertEquals("text/xml", cxfMessage.get(Message.CONTENT_TYPE));
        assertEquals("200", cxfMessage.get(Message.RESPONSE_CODE));
        assertEquals(requestContext, cxfMessage.get(Client.REQUEST_CONTEXT));
        assertEquals(responseContext, cxfMessage.get(Client.RESPONSE_CONTEXT));

        assertNull(cxfMessage.get(Exchange.HTTP_RESPONSE_CODE));
        
        // check the protocol headers
        Map<String, List<String>> cxfHeaders = 
            CastUtils.cast((Map<?, ?>)cxfMessage.get(org.apache.cxf.message.Message.PROTOCOL_HEADERS));
        assertNotNull(cxfHeaders);
        assertTrue(cxfHeaders.size() == 7);

        verifyHeader(cxfHeaders, "soapaction", "urn:hello:world");
        verifyHeader(cxfHeaders, "SoapAction", "urn:hello:world");
        verifyHeader(cxfHeaders, "SOAPAction", "urn:hello:world");
        verifyHeader(cxfHeaders, "myfruitheader", "peach");
        verifyHeader(cxfHeaders, "myFruitHeader", "peach");
        verifyHeader(cxfHeaders, "MYFRUITHEADER", "peach");
        verifyHeader(cxfHeaders, "MyBrewHeader", Arrays.asList("cappuccino", "espresso"));
        verifyHeader(cxfHeaders, Message.CONTENT_TYPE, "text/xml");
        verifyHeader(cxfHeaders, Message.REQUEST_URI, "/hello/cxf");
        verifyHeader(cxfHeaders, Message.HTTP_REQUEST_METHOD, "GET");
        verifyHeader(cxfHeaders, Message.PATH_INFO, "/hello/cxf");

        assertNull(cxfHeaders.get(Exchange.HTTP_RESPONSE_CODE));
        assertNull(cxfHeaders.get(Exchange.HTTP_URI));
        assertNull(cxfHeaders.get(Exchange.HTTP_METHOD));
        assertNull(cxfHeaders.get(Exchange.HTTP_PATH));
    }

    @Test
    public void testPropagateCxfToCamel() {
        Exchange exchange = new DefaultExchange(context);
        org.apache.cxf.message.Message cxfMessage = new org.apache.cxf.message.MessageImpl();
        Map<String, List<String>> cxfHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        cxfHeaders.put("Content-Length", Arrays.asList("241"));
        cxfHeaders.put("soapAction", Arrays.asList("urn:hello:world"));
        cxfHeaders.put("myfruitheader", Arrays.asList("peach"));
        cxfHeaders.put("mybrewheader", Arrays.asList("cappuccino", "espresso"));
        cxfHeaders.put(Message.CONTENT_TYPE, Arrays.asList("text/xml"));
        cxfHeaders.put(Message.ENCODING, Arrays.asList("UTF-8"));
        cxfHeaders.put(Message.RESPONSE_CODE, Arrays.asList("201")); // Ignored
        cxfHeaders.put(Message.REQUEST_URI, Arrays.asList("/base/hello/cxf"));
        cxfHeaders.put(Message.HTTP_REQUEST_METHOD, Arrays.asList("GET"));
        cxfHeaders.put(Message.PATH_INFO, Arrays.asList("/base/hello/cxf"));
        cxfHeaders.put(Message.BASE_PATH, Arrays.asList("/base"));
        cxfMessage.put(org.apache.cxf.message.Message.PROTOCOL_HEADERS, cxfHeaders);

        cxfMessage.put(Message.RESPONSE_CODE, "200");
        Map<String, Object> requestContext = Collections.singletonMap("request", "true");
        Map<String, Object> responseContext = Collections.singletonMap("response", "true");
        cxfMessage.put(Client.REQUEST_CONTEXT, requestContext);
        cxfMessage.put(Client.RESPONSE_CONTEXT, responseContext);

        CxfHeaderHelper.propagateCxfToCamel(new DefaultHeaderFilterStrategy(), 
                                            cxfMessage, exchange.getIn(), exchange);

        Map<String, Object> camelHeaders = exchange.getIn().getHeaders();
        assertEquals("urn:hello:world", camelHeaders.get("soapaction"));
        assertEquals("urn:hello:world", camelHeaders.get("SoapAction"));
        assertEquals("241", camelHeaders.get("content-length"));
        assertEquals("peach", camelHeaders.get("MyFruitHeader"));
        assertEquals(Arrays.asList("cappuccino", "espresso"), camelHeaders.get("MyBrewHeader"));
        assertEquals("text/xml; charset=UTF-8", camelHeaders.get(Exchange.CONTENT_TYPE));
        assertEquals("/base/hello/cxf", camelHeaders.get(Exchange.HTTP_URI));
        assertEquals("GET", camelHeaders.get(Exchange.HTTP_METHOD));
        assertEquals("/hello/cxf", camelHeaders.get(Exchange.HTTP_PATH));

        assertEquals("200", camelHeaders.get(Exchange.HTTP_RESPONSE_CODE));
        assertEquals(requestContext, camelHeaders.get(Client.REQUEST_CONTEXT));
        assertEquals(responseContext, camelHeaders.get(Client.RESPONSE_CONTEXT));

        assertNull(camelHeaders.get(Message.RESPONSE_CODE));
        assertNull(camelHeaders.get(Message.REQUEST_URI));
        assertNull(camelHeaders.get(Message.HTTP_REQUEST_METHOD));
        assertNull(camelHeaders.get(Message.PATH_INFO));
        assertNull(camelHeaders.get(Message.RESPONSE_CODE));
    } 

    @Test
    public void testPropagateCxfToCamelWithMerged() {
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(CxfConstants.CAMEL_CXF_PROTOCOL_HEADERS_MERGED, Boolean.TRUE);
        
        org.apache.cxf.message.Message cxfMessage = new org.apache.cxf.message.MessageImpl();
        Map<String, List<String>> cxfHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        cxfHeaders.put("myfruitheader", Arrays.asList("peach"));
        cxfHeaders.put("mybrewheader", Arrays.asList("cappuccino", "espresso"));
        cxfMessage.put(org.apache.cxf.message.Message.PROTOCOL_HEADERS, cxfHeaders);

        CxfHeaderHelper.propagateCxfToCamel(new DefaultHeaderFilterStrategy(), 
                                            cxfMessage, exchange.getIn(), exchange);

        Map<String, Object> camelHeaders = exchange.getIn().getHeaders();
        assertEquals("peach", camelHeaders.get("MyFruitHeader"));
        assertEquals("cappuccino, espresso", camelHeaders.get("MyBrewHeader"));
    }

    private void verifyHeader(Map<String, List<String>> headers, String name, List<String> value) {
        List<String> values = headers.get(name);
        assertTrue("The entry must be available", values != null && values.size() == ((List<?>)value).size());
        assertEquals("The value must match", value, values);
    }

    private void verifyHeader(Map<String, List<String>> headers, String name, String value) {
        List<String> values = headers.get(name);
        assertTrue("The entry must be available", values != null && values.size() == 1);
        assertEquals("The value must match", value, values.get(0));
    }

}
