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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.cxf.attachment.AttachmentImpl;
import org.apache.cxf.binding.Binding;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Assert;
import org.junit.Test;

/**
 * 
 */
public class DefaultCxfBindingTest extends Assert {
    private DefaultCamelContext context = new DefaultCamelContext();

    @Test
    public void testSetGetHeaderFilterStrategy() {
        DefaultCxfBinding cxfBinding = new DefaultCxfBinding();
        HeaderFilterStrategy hfs = new DefaultHeaderFilterStrategy();
        
        cxfBinding.setHeaderFilterStrategy(hfs);        
        assertSame("The header filter strategy is set", hfs, cxfBinding.getHeaderFilterStrategy());
    }
    
    @Test
    public void testPopulateCxfRequestFromExchange() {
        DefaultCxfBinding cxfBinding = new DefaultCxfBinding();
        cxfBinding.setHeaderFilterStrategy(new DefaultHeaderFilterStrategy());
        Exchange exchange = new DefaultExchange(context);
        org.apache.cxf.message.Exchange cxfExchange = new org.apache.cxf.message.ExchangeImpl();
        exchange.setProperty(CxfConstants.CXF_EXCHANGE, cxfExchange);
        exchange.setProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.PAYLOAD);
        Map<String, Object> requestContext = new HashMap<String, Object>();
        
        exchange.getIn().setHeader("soapAction", "urn:hello:world");
        exchange.getIn().setHeader("MyFruitHeader", "peach");
        exchange.getIn().addAttachment("att-1", new DataHandler(new FileDataSource("pom.xml")));

        cxfBinding.populateCxfRequestFromExchange(cxfExchange, exchange, requestContext);
        
        // check the protocol headers
        Map<String, List<String>> headers = CastUtils.cast((Map)requestContext.get(Message.PROTOCOL_HEADERS));
        assertNotNull(headers);
        assertTrue(headers.size() == 2);
        
        verifyHeader(headers, "soapaction", "urn:hello:world");
        verifyHeader(headers, "SoapAction", "urn:hello:world");
        verifyHeader(headers, "SOAPAction", "urn:hello:world");
        verifyHeader(headers, "myfruitheader", "peach");
        verifyHeader(headers, "myFruitHeader", "peach");
        verifyHeader(headers, "MYFRUITHEADER", "peach");
        
        Set<Attachment> attachments = CastUtils.cast((Set)requestContext.get(CxfConstants.CAMEL_CXF_ATTACHMENTS));
        assertNotNull(attachments);
        assertNotNull(attachments.size() == 1);
        Attachment att = attachments.iterator().next();
        assertEquals("att-1", att.getId());
    }
    
    @Test
    public void testPopupalteExchangeFromCxfResponse() {
        DefaultCxfBinding cxfBinding = new DefaultCxfBinding();
        cxfBinding.setHeaderFilterStrategy(new DefaultHeaderFilterStrategy());
        Exchange exchange = new DefaultExchange(context);
        org.apache.cxf.message.Exchange cxfExchange = new org.apache.cxf.message.ExchangeImpl();
        exchange.setProperty(CxfConstants.CXF_EXCHANGE, cxfExchange);
        exchange.setProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.PAYLOAD);
        Map<String, Object> responseContext = new HashMap<String, Object>();
        responseContext.put(org.apache.cxf.message.Message.RESPONSE_CODE, Integer.valueOf(200));
        Map<String, List<String>> headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        headers.put("content-type", Arrays.asList("text/xml;charset=UTF-8"));
        headers.put("Content-Length", Arrays.asList("241"));
        responseContext.put(org.apache.cxf.message.Message.PROTOCOL_HEADERS, headers);
        org.apache.cxf.message.Message cxfMessage = new org.apache.cxf.message.MessageImpl();
        cxfExchange.setInMessage(cxfMessage);
        
        Set<Attachment> attachments = new HashSet<Attachment>();
        attachments.add(new AttachmentImpl("att-1", new DataHandler(new FileDataSource("pom.xml"))));
        cxfMessage.setAttachments(attachments);
        
        cxfBinding.populateExchangeFromCxfResponse(exchange, cxfExchange, responseContext);
        
        Map<String, Object> camelHeaders = exchange.getOut().getHeaders();
        assertNotNull(camelHeaders);
        assertEquals(responseContext, camelHeaders.get(Client.RESPONSE_CONTEXT));
        
        Map<String, DataHandler> camelAttachments = exchange.getOut().getAttachments();
        assertNotNull(camelAttachments);
        assertNotNull(camelAttachments.get("att-1"));
    }

    @Test
    public void testPopupalteCxfResponseFromExchange() {
        DefaultCxfBinding cxfBinding = new DefaultCxfBinding();
        cxfBinding.setHeaderFilterStrategy(new DefaultHeaderFilterStrategy());
        Exchange exchange = new DefaultExchange(context);
        org.apache.cxf.message.Exchange cxfExchange = new org.apache.cxf.message.ExchangeImpl();
        exchange.setProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.PAYLOAD);
        
        exchange.getOut().setHeader("soapAction", "urn:hello:world");
        exchange.getOut().setHeader("MyFruitHeader", "peach");
        exchange.getOut().addAttachment("att-1", new DataHandler(new FileDataSource("pom.xml")));
        
        IMocksControl control = EasyMock.createNiceControl();
        
        Endpoint endpoint = control.createMock(Endpoint.class);
        Binding binding = control.createMock(Binding.class);
        EasyMock.expect(endpoint.getBinding()).andReturn(binding);
        org.apache.cxf.message.Message cxfMessage = new org.apache.cxf.message.MessageImpl();
        EasyMock.expect(binding.createMessage()).andReturn(cxfMessage);
        cxfExchange.put(Endpoint.class, endpoint);
        control.replay();
        
        cxfBinding.populateCxfResponseFromExchange(exchange, cxfExchange);
        
        cxfMessage = cxfExchange.getOutMessage();
        assertNotNull(cxfMessage);
        
        Map<String, List<String>> headers = CastUtils.cast((Map)cxfMessage.get(Message.PROTOCOL_HEADERS));
        assertNotNull(headers);
        assertTrue(headers.size() == 2);
        
        verifyHeader(headers, "soapaction", "urn:hello:world");
        verifyHeader(headers, "SoapAction", "urn:hello:world");
        verifyHeader(headers, "SOAPAction", "urn:hello:world");
        verifyHeader(headers, "myfruitheader", "peach");
        verifyHeader(headers, "myFruitHeader", "peach");
        verifyHeader(headers, "MYFRUITHEADER", "peach");
        
        Collection<Attachment> attachments = cxfMessage.getAttachments();
        assertNotNull(attachments);
        assertNotNull(attachments.size() == 1);
        Attachment att = attachments.iterator().next();
        assertEquals("att-1", att.getId());
    }

    @Test
    public void testPopupalteExchangeFromCxfRequest() {
        DefaultCxfBinding cxfBinding = new DefaultCxfBinding();
        cxfBinding.setHeaderFilterStrategy(new DefaultHeaderFilterStrategy());
        Exchange exchange = new DefaultExchange(context);
        org.apache.cxf.message.Exchange cxfExchange = new org.apache.cxf.message.ExchangeImpl();
        exchange.setProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.PAYLOAD);
        org.apache.cxf.message.Message cxfMessage = new org.apache.cxf.message.MessageImpl();
        Map<String, List<String>> headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        headers.put("content-type", Arrays.asList("text/xml;charset=UTF-8"));
        headers.put("Content-Length", Arrays.asList("241"));
        headers.put("soapAction", Arrays.asList("urn:hello:world"));
        headers.put("myfruitheader", Arrays.asList("peach"));
        cxfMessage.put(org.apache.cxf.message.Message.PROTOCOL_HEADERS, headers);

        Set<Attachment> attachments = new HashSet<Attachment>();
        attachments.add(new AttachmentImpl("att-1", new DataHandler(new FileDataSource("pom.xml"))));
        cxfMessage.setAttachments(attachments);
        
        cxfExchange.setInMessage(cxfMessage);

        cxfBinding.populateExchangeFromCxfRequest(cxfExchange, exchange);
        
        Map<String, Object> camelHeaders = exchange.getIn().getHeaders();
        assertNotNull(camelHeaders);
        assertEquals(camelHeaders.get("soapaction"), "urn:hello:world");
        assertEquals(camelHeaders.get("SoapAction"), "urn:hello:world");
        assertEquals(camelHeaders.get("content-type"), "text/xml;charset=UTF-8");
        assertEquals(camelHeaders.get("content-length"), "241");
        assertEquals(camelHeaders.get("MyFruitHeader"), "peach");
        
        Map<String, DataHandler> camelAttachments = exchange.getIn().getAttachments();
        assertNotNull(camelAttachments);
        assertNotNull(camelAttachments.get("att-1"));
        
    }

    private void verifyHeader(Map<String, List<String>> headers, String name, String value) {
        List<String> values = headers.get(name);
        assertTrue("The entry must be available", values != null && values.size() == 1);
        assertEquals("The value must match", values.get(0), value);
    }

}
