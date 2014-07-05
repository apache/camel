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
package org.apache.camel.component.cxf.common.header;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;


/**
 * 
 */
public class CxfHeaderHelperTest extends Assert {
    private DefaultCamelContext context = new DefaultCamelContext();
    
    @Test
    public void testPropagateCamelToCxf() {
        Exchange exchange = new DefaultExchange(context);
        
        exchange.getIn().setHeader("soapAction", "urn:hello:world");
        exchange.getIn().setHeader("MyFruitHeader", "peach");
        exchange.getIn().setHeader("MyBrewHeader", Arrays.asList("cappuccino", "espresso"));
        org.apache.cxf.message.Message cxfMessage = new org.apache.cxf.message.MessageImpl();
        
        CxfHeaderHelper.propagateCamelToCxf(new DefaultHeaderFilterStrategy(), 
                                            exchange.getIn().getHeaders(), cxfMessage, exchange);
        
        // check the protocol headers
        Map<String, List<String>> cxfHeaders = 
            CastUtils.cast((Map<?, ?>)cxfMessage.get(org.apache.cxf.message.Message.PROTOCOL_HEADERS));
        assertNotNull(cxfHeaders);
        assertTrue(cxfHeaders.size() == 3);
        
        verifyHeader(cxfHeaders, "soapaction", "urn:hello:world");
        verifyHeader(cxfHeaders, "SoapAction", "urn:hello:world");
        verifyHeader(cxfHeaders, "SOAPAction", "urn:hello:world");
        verifyHeader(cxfHeaders, "myfruitheader", "peach");
        verifyHeader(cxfHeaders, "myFruitHeader", "peach");
        verifyHeader(cxfHeaders, "MYFRUITHEADER", "peach");
        verifyHeader(cxfHeaders, "MyBrewHeader", Arrays.asList("cappuccino", "espresso"));
    } 

    @Test
    public void testPropagateCxfToCamel() {
        Exchange exchange = new DefaultExchange(context);
        org.apache.cxf.message.Message cxfMessage = new org.apache.cxf.message.MessageImpl();
        Map<String, List<String>> cxfHeaders = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        cxfHeaders.put("Content-Length", Arrays.asList("241"));
        cxfHeaders.put("soapAction", Arrays.asList("urn:hello:world"));
        cxfHeaders.put("myfruitheader", Arrays.asList("peach"));
        cxfHeaders.put("mybrewheader", Arrays.asList("cappuccino", "espresso"));
        cxfMessage.put(org.apache.cxf.message.Message.PROTOCOL_HEADERS, cxfHeaders);
        
        Map<String, Object> camelHeaders = exchange.getIn().getHeaders();
        CxfHeaderHelper.propagateCxfToCamel(new DefaultHeaderFilterStrategy(), 
                                            cxfMessage, camelHeaders, exchange);

        assertEquals("urn:hello:world", camelHeaders.get("soapaction"));
        assertEquals("urn:hello:world", camelHeaders.get("SoapAction"));
        assertEquals("241", camelHeaders.get("content-length"));
        assertEquals("peach", camelHeaders.get("MyFruitHeader"));
        assertEquals(Arrays.asList("cappuccino", "espresso"), camelHeaders.get("MyBrewHeader"));
    } 

    @Test
    public void testPropagateCxfToCamelWithMerged() {
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(CxfConstants.CAMEL_CXF_PROTOCOL_HEADERS_MERGED, Boolean.TRUE);
        
        org.apache.cxf.message.Message cxfMessage = new org.apache.cxf.message.MessageImpl();
        Map<String, List<String>> cxfHeaders = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        cxfHeaders.put("myfruitheader", Arrays.asList("peach"));
        cxfHeaders.put("mybrewheader", Arrays.asList("cappuccino", "espresso"));
        cxfMessage.put(org.apache.cxf.message.Message.PROTOCOL_HEADERS, cxfHeaders);

        Map<String, Object> camelHeaders = exchange.getIn().getHeaders();
        CxfHeaderHelper.propagateCxfToCamel(new DefaultHeaderFilterStrategy(), 
                                            cxfMessage, camelHeaders, exchange);

        assertEquals("peach", camelHeaders.get("MyFruitHeader"));
        assertEquals("cappuccino, espresso", camelHeaders.get("MyBrewHeader"));
    } 

    @Test
    public void testContentType() {

        Exchange camelExchange = EasyMock.createMock(Exchange.class);
        HeaderFilterStrategy strategy = setupHeaderStrategy(camelExchange);
        Message cxfMessage = new MessageImpl();
        CxfHeaderHelper.propagateCamelToCxf(strategy, 
            Collections.<String, Object>singletonMap("Content-Type", "text/xml"), cxfMessage, camelExchange);

        Map<String, List<String>> cxfHeaders = CastUtils.cast((Map<?, ?>)cxfMessage.get(Message.PROTOCOL_HEADERS)); 
        assertEquals(1, cxfHeaders.size());
        assertEquals(1, cxfHeaders.get("Content-Type").size());
        assertEquals("text/xml", cxfHeaders.get("Content-Type").get(0)); 
      
        assertEquals("text/xml", cxfMessage.get(Message.CONTENT_TYPE));   
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

    private HeaderFilterStrategy setupHeaderStrategy(Exchange exchange) {

        HeaderFilterStrategy strategy = EasyMock.createMock(HeaderFilterStrategy.class);
        strategy.applyFilterToCamelHeaders("Content-Type", "text/xml", exchange);
        EasyMock.expectLastCall().andReturn(false);
        EasyMock.replay(strategy);
        return strategy;
    }

}
