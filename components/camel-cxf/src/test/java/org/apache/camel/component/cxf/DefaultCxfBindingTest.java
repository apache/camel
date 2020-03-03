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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import junit.framework.TestCase;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.camel.support.ExchangeHelper;
import org.apache.cxf.attachment.AttachmentImpl;
import org.apache.cxf.binding.Binding;
import org.apache.cxf.binding.soap.SoapBindingConstants;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DefaultCxfBindingTest extends TestCase {
    
    private static final String SOAP_MESSAGE_1 = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\""
        + " xmlns=\"http://www.mycompany.com/test/\" xmlns:ns1=\"http://www.mycompany.com/test/1/\">"
        + " <soap:Body> <request> <ns1:identifier>TEST</ns1:identifier> </request>"
        + " </soap:Body> </soap:Envelope>";
    
    private static final String SOAP_MESSAGE_2 = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\""
        + " xmlns=\"http://www.mycompany.com/test/\" xmlns:ns1=\"http://www.mycompany.com/test/1/\">"
        + " <soap:Body> <ns1:identifier xmlns:ns1=\"http://www.mycompany.com/test/\" xmlns=\"http://www.mycompany.com/test/1/\">TEST</ns1:identifier>"
        + " </soap:Body> </soap:Envelope>";

    private DefaultCamelContext context;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = new DefaultCamelContext();
        context.start();
    }

    @Test
    public void testSetGetHeaderFilterStrategy() {
        DefaultCxfBinding cxfBinding = new DefaultCxfBinding();
        HeaderFilterStrategy hfs = new DefaultHeaderFilterStrategy();
        
        cxfBinding.setHeaderFilterStrategy(hfs);        
        assertSame("The header filter strategy is set", hfs, cxfBinding.getHeaderFilterStrategy());
    }
    
    private Document getDocument(String soapMessage) throws Exception {
        
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(IOConverter.toInputStream(soapMessage, null));
        document.getDocumentElement().normalize();
       
        return document;
    }
    
    @Test
    public void testPayloadBodyNamespace() throws Exception {
        MessageImpl message = new MessageImpl();
        Map<String, String> nsMap = new HashMap<>();
        Document document = getDocument(SOAP_MESSAGE_1);
        message.setContent(Node.class, document);
        DefaultCxfBinding.getPayloadBodyElements(message, nsMap);
        
        assertEquals(2, nsMap.size());
        assertEquals("http://www.mycompany.com/test/", nsMap.get("xmlns"));
        
        Element element = document.createElement("tag");
        DefaultCxfBinding.addNamespace(element, nsMap);
        assertEquals("http://www.mycompany.com/test/", element.getAttribute("xmlns"));
        assertEquals("http://www.mycompany.com/test/1/", element.getAttribute("xmlns:ns1"));
    }
    
    @Test
    public void testOverridePayloadBodyNamespace() throws Exception {
        MessageImpl message = new MessageImpl();
        Map<String, String> nsMap = new HashMap<>();
        Document document = getDocument(SOAP_MESSAGE_2);
        message.setContent(Node.class, document);
        DefaultCxfBinding.getPayloadBodyElements(message, nsMap);
        
        assertEquals(2, nsMap.size());
        assertEquals("http://www.mycompany.com/test/", nsMap.get("xmlns"));
        
        Element element = (Element)document.getElementsByTagName("ns1:identifier").item(0);
        assertNotNull("We should get the element", element);
        DefaultCxfBinding.addNamespace(element, nsMap);
        assertEquals("http://www.mycompany.com/test/1/", element.getAttribute("xmlns"));
        assertEquals("http://www.mycompany.com/test/", element.getAttribute("xmlns:ns1"));
    }

    
    @Test
    public void testSetCharsetWithContentType() {
        DefaultCxfBinding cxfBinding = new DefaultCxfBinding();
        cxfBinding.setHeaderFilterStrategy(new DefaultHeaderFilterStrategy());
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/xml;charset=ISO-8859-1");
        cxfBinding.setCharsetWithContentType(exchange);
        
        String charset = ExchangeHelper.getCharsetName(exchange);
        assertEquals("Get a wrong charset", "ISO-8859-1", charset);
        
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/xml");
        cxfBinding.setCharsetWithContentType(exchange);
        charset = ExchangeHelper.getCharsetName(exchange);
        assertEquals("Get a worng charset name", "UTF-8", charset);
    }
    
    @Test
    public void testPopulateCxfRequestFromExchange() {
        DefaultCxfBinding cxfBinding = new DefaultCxfBinding();
        cxfBinding.setHeaderFilterStrategy(new DefaultHeaderFilterStrategy());
        Exchange exchange = new DefaultExchange(context);
        org.apache.cxf.message.Exchange cxfExchange = new org.apache.cxf.message.ExchangeImpl();
        exchange.setProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.PAYLOAD);
        Map<String, Object> requestContext = new HashMap<>();
        
        exchange.getIn().setHeader("soapAction", "urn:hello:world");
        exchange.getIn().setHeader("MyFruitHeader", "peach");
        exchange.getIn().setHeader("MyBrewHeader", Arrays.asList("cappuccino", "espresso"));
        exchange.getIn(AttachmentMessage.class).addAttachment("att-1", new DataHandler(new FileDataSource("pom.xml")));
        exchange.getIn(AttachmentMessage.class).getAttachmentObject("att-1").setHeader("attachment-header", "value 1");

        cxfBinding.populateCxfRequestFromExchange(cxfExchange, exchange, requestContext);
        
        // check the protocol headers
        Map<String, List<String>> headers = CastUtils.cast((Map<?, ?>)requestContext.get(Message.PROTOCOL_HEADERS));
        assertNotNull(headers);
        assertEquals(3, headers.size());
        
        verifyHeader(headers, "soapaction", "urn:hello:world");
        verifyHeader(headers, "SoapAction", "urn:hello:world");
        verifyHeader(headers, "SOAPAction", "urn:hello:world");
        verifyHeader(headers, "myfruitheader", "peach");
        verifyHeader(headers, "myFruitHeader", "peach");
        verifyHeader(headers, "MYFRUITHEADER", "peach");
        verifyHeader(headers, "MyBrewHeader", Arrays.asList("cappuccino", "espresso"));
        
        Set<Attachment> attachments 
            = CastUtils.cast((Set<?>)requestContext.get(CxfConstants.CAMEL_CXF_ATTACHMENTS));
        assertNotNull(attachments);
        assertNotNull(attachments.size() == 1);
        Attachment att = attachments.iterator().next();
        assertEquals("att-1", att.getId());
        assertEquals("value 1", att.getHeader("attachment-header"));
    }

    @Test
    public void testPopulateCxfSoapHeaderRequestFromExchange() {
        DefaultCxfBinding cxfBinding = new DefaultCxfBinding();
        cxfBinding.setHeaderFilterStrategy(new DefaultHeaderFilterStrategy());
        Exchange exchange = new DefaultExchange(context);
        org.apache.cxf.message.Exchange cxfExchange = new org.apache.cxf.message.ExchangeImpl();
        Map<String, Object> requestContext = new HashMap<>();

        String expectedSoapActionHeader = "urn:hello:world";
        exchange.getIn().setHeader("soapAction", expectedSoapActionHeader);

        cxfBinding.populateCxfRequestFromExchange(cxfExchange, exchange, requestContext);

        String actualSoapActionHeader = (String)requestContext.get(SoapBindingConstants.SOAP_ACTION);
        assertEquals(expectedSoapActionHeader, actualSoapActionHeader);
    }
    
    @Test
    public void testPopulateCxfSoapHeaderRequestFromExchangeWithExplicitOperationName() {
        DefaultCxfBinding cxfBinding = new DefaultCxfBinding();
        cxfBinding.setHeaderFilterStrategy(new DefaultHeaderFilterStrategy());
        Exchange exchange = new DefaultExchange(context);
        org.apache.cxf.message.Exchange cxfExchange = new org.apache.cxf.message.ExchangeImpl();
        Map<String, Object> requestContext = new HashMap<>();

        String expectedSoapActionHeader = "urn:hello:world";
        exchange.getIn().setHeader(CxfConstants.OPERATION_NAMESPACE, "http://test123");
        exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, "testOperation");

        cxfBinding.populateCxfRequestFromExchange(cxfExchange, exchange, requestContext);

        String actualSoapActionHeader = (String)requestContext.get(SoapBindingConstants.SOAP_ACTION);
        assertNull(actualSoapActionHeader);
    }
    
    @Test
    public void testPopupalteExchangeFromCxfResponse() {
        DefaultCxfBinding cxfBinding = new DefaultCxfBinding();
        cxfBinding.setHeaderFilterStrategy(new DefaultHeaderFilterStrategy());
        Exchange exchange = new DefaultExchange(context);
        org.apache.cxf.message.Exchange cxfExchange = new org.apache.cxf.message.ExchangeImpl();
        exchange.setProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.PAYLOAD);
        Map<String, Object> responseContext = new HashMap<>();
        responseContext.put(org.apache.cxf.message.Message.RESPONSE_CODE, Integer.valueOf(200));
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("content-type", Arrays.asList("text/xml;charset=UTF-8"));
        headers.put("Content-Length", Arrays.asList("241"));
        responseContext.put(org.apache.cxf.message.Message.PROTOCOL_HEADERS, headers);
        org.apache.cxf.message.Message cxfMessage = new org.apache.cxf.message.MessageImpl();
        cxfExchange.setInMessage(cxfMessage);
        
        Set<Attachment> attachments = new HashSet<>();
        AttachmentImpl attachment = new AttachmentImpl("att-1", new DataHandler(new FileDataSource("pom.xml")));
        attachment.setHeader("additional-header", "value 1");
        attachments.add(attachment);
        cxfMessage.setAttachments(attachments);
        
        cxfBinding.populateExchangeFromCxfResponse(exchange, cxfExchange, responseContext);
        
        Map<String, Object> camelHeaders = exchange.getOut().getHeaders();
        assertNotNull(camelHeaders);
        assertEquals(responseContext, camelHeaders.get(Client.RESPONSE_CONTEXT));
        
        Map<String, org.apache.camel.attachment.Attachment> camelAttachments = exchange.getOut(AttachmentMessage.class).getAttachmentObjects();
        assertNotNull(camelAttachments);
        assertNotNull(camelAttachments.get("att-1"));
        assertEquals("value 1", camelAttachments.get("att-1").getHeader("additional-header"));
    }

    @Test
    public void testPopupalteExchangeFromCxfResponseOfNullBody() {
        DefaultCxfBinding cxfBinding = new DefaultCxfBinding();
        cxfBinding.setHeaderFilterStrategy(new DefaultHeaderFilterStrategy());
        Exchange exchange = new DefaultExchange(context);
        org.apache.cxf.message.Exchange cxfExchange = new org.apache.cxf.message.ExchangeImpl();
        exchange.setProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.PAYLOAD);
        Map<String, Object> responseContext = new HashMap<>();
        responseContext.put(org.apache.cxf.message.Message.RESPONSE_CODE, Integer.valueOf(200));
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        responseContext.put(org.apache.cxf.message.Message.PROTOCOL_HEADERS, headers);
        org.apache.cxf.message.Message cxfMessage = new org.apache.cxf.message.MessageImpl();
        cxfExchange.setInMessage(cxfMessage);
        
        cxfBinding.populateExchangeFromCxfResponse(exchange, cxfExchange, responseContext);

        CxfPayload<?> cxfPayload = exchange.getOut().getBody(CxfPayload.class);

        assertNotNull(cxfPayload);
        List<?> body = cxfPayload.getBody(); 
        assertNotNull(body);
        assertEquals(0, body.size());
    }
    
    @Test
    public void testPopupalteCxfResponseFromExchange() {
        DefaultCxfBinding cxfBinding = new DefaultCxfBinding();
        cxfBinding.setHeaderFilterStrategy(new DefaultHeaderFilterStrategy());
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        org.apache.cxf.message.Exchange cxfExchange = new org.apache.cxf.message.ExchangeImpl();
        exchange.setProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.PAYLOAD);
        
        exchange.getOut().setHeader("soapAction", "urn:hello:world");
        exchange.getOut().setHeader("MyFruitHeader", "peach");
        exchange.getOut(AttachmentMessage.class).addAttachment("att-1", new DataHandler(new FileDataSource("pom.xml")));
        exchange.getOut(AttachmentMessage.class).getAttachmentObject("att-1").setHeader("attachment-header", "value 1");
        
        Endpoint endpoint = mock(Endpoint.class);
        Binding binding = mock(Binding.class);
        when(endpoint.getBinding()).thenReturn(binding);
        org.apache.cxf.message.Message cxfMessage = new org.apache.cxf.message.MessageImpl();
        when(binding.createMessage()).thenReturn(cxfMessage);
        cxfExchange.put(Endpoint.class, endpoint);
        
        cxfBinding.populateCxfResponseFromExchange(exchange, cxfExchange);
        
        cxfMessage = cxfExchange.getOutMessage();
        assertNotNull(cxfMessage);
        
        Map<String, List<String>> headers = CastUtils.cast((Map<?, ?>)cxfMessage.get(Message.PROTOCOL_HEADERS));
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
        assertEquals("value 1", att.getHeader("attachment-header"));
    }

    @Test
    public void testPopupalteExchangeFromCxfRequest() {
        DefaultCxfBinding cxfBinding = new DefaultCxfBinding();
        cxfBinding.setHeaderFilterStrategy(new DefaultHeaderFilterStrategy());
        Exchange exchange = new DefaultExchange(context);
        org.apache.cxf.message.Exchange cxfExchange = new org.apache.cxf.message.ExchangeImpl();
        exchange.setProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.PAYLOAD);
        org.apache.cxf.message.Message cxfMessage = new org.apache.cxf.message.MessageImpl();
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("content-type", Arrays.asList("text/xml;charset=UTF-8"));
        headers.put("Content-Length", Arrays.asList("241"));
        headers.put("soapAction", Arrays.asList("urn:hello:world"));
        headers.put("myfruitheader", Arrays.asList("peach"));
        headers.put("mybrewheader", Arrays.asList("cappuccino", "espresso"));
        cxfMessage.put(org.apache.cxf.message.Message.PROTOCOL_HEADERS, headers);

        Set<Attachment> attachments = new HashSet<>();
        AttachmentImpl attachment = new AttachmentImpl("att-1", new DataHandler(new FileDataSource("pom.xml")));
        attachment.setHeader("attachment-header", "value 1");
        attachments.add(attachment);
        cxfMessage.setAttachments(attachments);
        
        cxfExchange.setInMessage(cxfMessage);

        cxfBinding.populateExchangeFromCxfRequest(cxfExchange, exchange);
        
        Map<String, Object> camelHeaders = exchange.getIn().getHeaders();
        assertNotNull(camelHeaders);
        assertEquals("urn:hello:world", camelHeaders.get("soapaction"));
        assertEquals("urn:hello:world", camelHeaders.get("SoapAction"));
        assertEquals("text/xml;charset=UTF-8", camelHeaders.get("content-type"));
        assertEquals("241", camelHeaders.get("content-length"));
        assertEquals("peach", camelHeaders.get("MyFruitHeader"));
        assertEquals(Arrays.asList("cappuccino", "espresso"), camelHeaders.get("MyBrewHeader"));

        Map<String, org.apache.camel.attachment.Attachment> camelAttachments = exchange.getIn(AttachmentMessage.class).getAttachmentObjects();
        assertNotNull(camelAttachments);
        assertNotNull(camelAttachments.get("att-1"));
        assertEquals("value 1", camelAttachments.get("att-1").getHeader("attachment-header"));
    }

    @Test
    public void testPopupalteExchangeFromCxfRequestWithHeaderMerged() {
        DefaultCxfBinding cxfBinding = new DefaultCxfBinding();
        cxfBinding.setHeaderFilterStrategy(new DefaultHeaderFilterStrategy());
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(CxfConstants.CAMEL_CXF_PROTOCOL_HEADERS_MERGED, Boolean.TRUE);
        org.apache.cxf.message.Exchange cxfExchange = new org.apache.cxf.message.ExchangeImpl();
        exchange.setProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.PAYLOAD);
        org.apache.cxf.message.Message cxfMessage = new org.apache.cxf.message.MessageImpl();
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("myfruitheader", Arrays.asList("peach"));
        headers.put("mybrewheader", Arrays.asList("cappuccino", "espresso"));
        cxfMessage.put(org.apache.cxf.message.Message.PROTOCOL_HEADERS, headers);

        cxfExchange.setInMessage(cxfMessage);

        cxfBinding.populateExchangeFromCxfRequest(cxfExchange, exchange);
        
        Map<String, Object> camelHeaders = exchange.getIn().getHeaders();
        assertNotNull(camelHeaders);
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
        assertEquals("The value must match", values.get(0), value);
    }

}
