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

package org.apache.camel.component.cxf.soap.headers;

import java.io.StringReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Holder;

import org.w3c.dom.Node;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.CxfConstants;
import org.apache.camel.component.cxf.CxfEndpoint;
import org.apache.camel.component.cxf.CxfMessage;
import org.apache.camel.component.cxf.headers.MessageHeadersRelay;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.spring.SpringTestSupport;
import org.apache.cxf.BusFactory;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.ServerImpl;
import org.apache.cxf.headers.Header;
import org.apache.cxf.headers.Header.Direction;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.jaxb.JAXBDataBinding;
import org.apache.cxf.jaxws.EndpointImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.outofband.header.OutofBandHeader;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;


public class CxfMessageHeadersRelayTest extends SpringTestSupport {

    private ServerImpl relayServer;
    private ServerImpl noRelayServer;
    private ServerImpl relayServerWithInsertion;

    @Override
    protected void setUp() throws Exception {
        setUseRouteBuilder(false);
        super.setUp();
        startTargetCxfService();
    }

    @Override
    protected void tearDown() throws Exception {
        if (relayServer != null) {
            relayServer.stop();
        }
        if (noRelayServer != null) {
            noRelayServer.stop();
        }        
        if (relayServerWithInsertion != null) {
            relayServerWithInsertion.stop();
        }
        
        super.tearDown();
        BusFactory.setDefaultBus(null);
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/spring/message_headers_relay.xml");
    }

    protected void startTargetCxfService() {
        Object impl = new HeaderTesterImpl();
        String address = "http://localhost:9091/HeaderService/";
        EndpointImpl endpoint = (EndpointImpl) Endpoint.publish(address, impl);
        relayServer = endpoint.getServer();
        
        impl = new HeaderTesterImpl(false);
        address = "http://localhost:7070/HeaderService/";
        endpoint = (EndpointImpl) Endpoint.publish(address, impl);
        noRelayServer = endpoint.getServer();
        
        impl = new HeaderTesterWithInsertionImpl();
        address = "http://localhost:5091/HeaderService/";
        endpoint = (EndpointImpl) Endpoint.publish(address, impl);
        relayServerWithInsertion = endpoint.getServer();
    }

    
    protected static void addOutOfBoundHeader(HeaderTester proxy, boolean invalid) throws JAXBException {
        InvocationHandler handler  = Proxy.getInvocationHandler(proxy);
        BindingProvider  bp = null;

        try {
            if (handler instanceof BindingProvider) {
                bp = (BindingProvider)handler;
                Map<String, Object> requestContext = bp.getRequestContext();
                requestContext.put(Header.HEADER_LIST, buildOutOfBandHeaderList(invalid)); 
            }
        } catch (JAXBException ex) {
            throw ex;
        }
        
    }
   
    protected static List<Header> buildOutOfBandHeaderList(boolean invalid) throws JAXBException {
        OutofBandHeader ob = new OutofBandHeader();
        ob.setName("testOobHeader");
        ob.setValue("testOobHeaderValue");
        ob.setHdrAttribute(invalid ? "dontProcess" : "testHdrAttribute");

        SoapHeader hdr = new SoapHeader(
                new QName(Constants.TEST_HDR_NS, Constants.TEST_HDR_REQUEST_ELEM), 
                ob, 
                new JAXBDataBinding(ob.getClass()));
        hdr.setMustUnderstand(true);

        List<Header> headers = new ArrayList<Header>();
        headers.add(hdr);
        return headers;
    }
    
    public void testInHeaderCXFClientRelay() throws Exception {
        HeaderService s = new HeaderService(getClass().getClassLoader().getResource("soap_header.wsdl"),
                                            HeaderService.SERVICE);
        HeaderTester proxy = s.getSoapPortRelay();
        InHeader me = new InHeader();
        me.setRequestType("CXF user");
        InHeaderResponse response = proxy.inHeader(me, Constants.IN_HEADER_DATA);
        assertTrue("Expected in band header to propagate but it didn't", 
                   response.getResponseType().equals("pass"));
    }
    
    public void testOutHeaderCXFClientRelay() throws Exception {
        HeaderService s = new HeaderService(getClass().getClassLoader().getResource("soap_header.wsdl"),
                                            HeaderService.SERVICE);
        HeaderTester proxy = s.getSoapPortRelay();
        OutHeader me = new OutHeader();
        me.setRequestType("CXF user");
        Holder<OutHeaderResponse> result = new Holder<OutHeaderResponse>(new OutHeaderResponse()); 
        Holder<SOAPHeaderData> header = new Holder<SOAPHeaderData>(new SOAPHeaderData());
        proxy.outHeader(me, result, header);
        assertTrue("Expected in band header to propagate but it didn't", 
                   result.value.getResponseType().equals("pass"));
        assertTrue("Expected in band response header to propagate but it either didn't " 
                   + " or its contents do not match",
                   Constants.equals(Constants.OUT_HEADER_DATA, header.value));
    }

    public void testInOutHeaderCXFClientRelay() throws Exception {
        HeaderService s = new HeaderService(getClass().getClassLoader().getResource("soap_header.wsdl"),
                                            HeaderService.SERVICE);
        HeaderTester proxy = s.getSoapPortRelay();
        InoutHeader me = new InoutHeader();
        me.setRequestType("CXF user");
        Holder<SOAPHeaderData> header = new Holder<SOAPHeaderData>(Constants.IN_OUT_REQUEST_HEADER_DATA);
        InoutHeaderResponse result = proxy.inoutHeader(me, header);
        assertTrue("Expected in band header to propagate but it didn't", 
                   result.getResponseType().equals("pass"));
        assertTrue("Expected in band response header to propagate but it either didn't " 
                   + " or its contents do not match",
                   Constants.equals(Constants.IN_OUT_RESPONSE_HEADER_DATA, header.value));
    }

    public void testInOutOfBandHeaderCXFClientRelay() throws Exception {
        HeaderService s = new HeaderService(getClass().getClassLoader().getResource("soap_header.wsdl"),
                                            HeaderService.SERVICE);
        HeaderTester proxy = s.getSoapPortRelay();
        addOutOfBoundHeader(proxy, false);
        Me me = new Me();
        me.setFirstName("john");
        me.setLastName("Doh");
        Me response = proxy.inOutOfBandHeader(me);
        assertTrue("Expected the out of band header to propagate but it didn't", 
                   response.getFirstName().equals("pass"));
    }
    
    public void testInoutOutOfBandHeaderCXFClientRelay() throws Exception {
        HeaderService s = new HeaderService(getClass().getClassLoader().getResource("soap_header.wsdl"),
                                            HeaderService.SERVICE);
        HeaderTester proxy = s.getSoapPortRelay();
        addOutOfBoundHeader(proxy, false);
        Me me = new Me();
        me.setFirstName("john");
        me.setLastName("Doh");
        Me response = proxy.inoutOutOfBandHeader(me);
        assertTrue("Expected the out of band header to propagate but it didn't", 
                   response.getFirstName().equals("pass"));
        validateReturnedOutOfBandHeader(proxy);
    }
    
    public void testInoutOutOfBandHeaderCXFClientRelayWithHeaderInsertion() throws Exception {
        HeaderService s = new HeaderService(getClass().getClassLoader().getResource("soap_header.wsdl"),
                                            HeaderService.SERVICE);
        HeaderTester proxy = s.getSoapPortRelayWithInsertion();
        addOutOfBoundHeader(proxy, false);
        Me me = new Me();
        me.setFirstName("john");
        me.setLastName("Doh");
        Me response = proxy.inoutOutOfBandHeader(me);
        assertTrue("Expected the out of band header to propagate but it didn't", 
                   response.getFirstName().equals("pass"));
        
        InvocationHandler handler  = Proxy.getInvocationHandler(proxy);
        BindingProvider  bp = null;
        if (!(handler instanceof BindingProvider)) {
            fail("Unable to cast dynamic proxy InocationHandler to BindingProvider type");
        }

        bp = (BindingProvider)handler;
        Map<String, Object> responseContext = bp.getResponseContext();
        validateReturnedOutOfBandHeaderWithInsertion(responseContext, true);
    }
    
    public void testOutOutOfBandHeaderCXFClientRelay() throws Exception {
        HeaderService s = new HeaderService(getClass().getClassLoader().getResource("soap_header.wsdl"),
                                            HeaderService.SERVICE);
        HeaderTester proxy = s.getSoapPortRelay();
        Me me = new Me();
        me.setFirstName("john");
        me.setLastName("Doh");
        Me response = proxy.outOutOfBandHeader(me);
        assertTrue("Expected the out of band header to propagate but it didn't", 
                   response.getFirstName().equals("pass"));
        validateReturnedOutOfBandHeader(proxy);
    }

    public void testInOutOfBandHeaderCXFClientNoRelay() throws Exception {
        HeaderService s = new HeaderService(getClass().getClassLoader().getResource("soap_header.wsdl"),
                                            HeaderService.SERVICE);
        HeaderTester proxy = s.getSoapPortNoRelay();
        addOutOfBoundHeader(proxy, false);
        Me me = new Me();
        me.setFirstName("john");
        me.setLastName("Doh");
        Me response = proxy.inOutOfBandHeader(me);
        assertTrue("Expected the in out of band header *not* to propagate but it did", 
                   response.getFirstName().equals("pass"));
    }

    public void testOutOutOfBandHeaderCXFClientNoRelay() throws Exception {
        HeaderService s = new HeaderService(getClass().getClassLoader().getResource("soap_header.wsdl"),
                                            HeaderService.SERVICE);
        HeaderTester proxy = s.getSoapPortNoRelay();
        Me me = new Me();
        me.setFirstName("john");
        me.setLastName("Doh");
        Me response = proxy.outOutOfBandHeader(me);
        assertTrue("Expected the out out of band header *not* to propagate but it did", 
                   response.getFirstName().equals("pass"));
        validateReturnedOutOfBandHeader(proxy, false);
    }

    public void testInoutOutOfBandHeaderCXFClientNoRelay() throws Exception {
        HeaderService s = new HeaderService(getClass().getClassLoader().getResource("soap_header.wsdl"),
                                            HeaderService.SERVICE);
        HeaderTester proxy = s.getSoapPortNoRelay();
        addOutOfBoundHeader(proxy, false);
        Me me = new Me();
        me.setFirstName("john");
        me.setLastName("Doh");
        Me response = proxy.inoutOutOfBandHeader(me);
        assertTrue("Expected the in out of band header to *not* propagate but it did", 
                   response.getFirstName().equals("pass"));
        validateReturnedOutOfBandHeader(proxy, false);
    }

    public void testInHeaderCXFClientNoRelay() throws Exception {
        HeaderService s = new HeaderService(getClass().getClassLoader().getResource("soap_header.wsdl"),
                                            HeaderService.SERVICE);
        HeaderTester proxy = s.getSoapPortNoRelay();
        InHeader me = new InHeader();
        me.setRequestType("CXF user");
        InHeaderResponse response = null;
        try {
            response = proxy.inHeader(me, Constants.IN_HEADER_DATA);
        } catch (Exception e) {
            int i = 0;
        }
        assertTrue("Expected in in band header *not* to propagate but it did", 
                   response.getResponseType().equals("pass"));
    }
    
    public void testOutHeaderCXFClientNoRelay() throws Exception {
        HeaderService s = new HeaderService(getClass().getClassLoader().getResource("soap_header.wsdl"),
                                            HeaderService.SERVICE);
        HeaderTester proxy = s.getSoapPortNoRelay();
        OutHeader me = new OutHeader();
        me.setRequestType("CXF user");
        Holder<OutHeaderResponse> result = new Holder<OutHeaderResponse>(new OutHeaderResponse()); 
        Holder<SOAPHeaderData> header = new Holder<SOAPHeaderData>(new SOAPHeaderData());
        try {
            proxy.outHeader(me, result, header);
        } catch (Exception e) {
            int i = 0;
        }
        assertTrue("Ultimate remote HeaderTester.outHeader() destination was not reached", 
                   result.value.getResponseType().equals("pass"));
        assertTrue("Expected in band response header *not* to propagate but it did",
                   header.value == null);
    }
    
    public void testInoutHeaderCXFClientNoRelay() throws Exception {
        HeaderService s = new HeaderService(getClass().getClassLoader().getResource("soap_header.wsdl"),
                                            HeaderService.SERVICE);
        HeaderTester proxy = s.getSoapPortNoRelay();
        InoutHeader me = new InoutHeader();
        me.setRequestType("CXF user");
        Holder<SOAPHeaderData> header = new Holder<SOAPHeaderData>(Constants.IN_OUT_REQUEST_HEADER_DATA);
        InoutHeaderResponse result = null;
        try {
            result = proxy.inoutHeader(me, header);
        } catch (Exception e) {
            int i = 0;
        }
        assertTrue("Expected in band out header *not* to propagate but it did", 
                   result.getResponseType().equals("pass"));
        assertTrue("Expected in band response header *not* to propagate but did",
                   header.value == null);
    }

    public void testMessageHeadersRelaysSpringContext() throws Exception {
        CxfEndpoint endpoint = (CxfEndpoint)context.getEndpoint("cxf:bean:serviceExtraRelays");
        Collection<MessageHeadersRelay> relays = endpoint.getMessageHeadersRelays();
        assertTrue("Expected 4 relay headers but found " + relays.size(), relays.size() == 3);
        for (String ns : new CustomHeadersRelay().getActivationNamespaces()) {
            assertTrue("Expected a CustomHeadersRelay instance to be installed in the endpoint",
                       endpoint.getMessageHeadersRelay(ns).getClass() == CustomHeadersRelay.class);
        }
    }
    
    public void testInOutOfBandHeaderCamelTemplateDirect() throws Exception {
        doTestInOutOfBandHeaderCamelTemplate("direct:directProducer");
    }

    public void testOutOutOfBandHeaderCamelTemplateDirect() throws Exception {
        doTestOutOutOfBandHeaderCamelTemplate("direct:directProducer");
    }
    
    public void testInOutOutOfBandHeaderCamelTemplateDirect() throws Exception {
        doTestInOutOutOfBandHeaderCamelTemplate("direct:directProducer");
    }

    public void testInOutOfBandHeaderCamelTemplateRelay() throws Exception {
        doTestInOutOfBandHeaderCamelTemplate("direct:relayProducer");
    }
    
    public void testOutOutOfBandHeaderCamelTemplateRelay() throws Exception {
        doTestOutOutOfBandHeaderCamelTemplate("direct:relayProducer");
    }

    public void testInOutOutOfBandHeaderCamelTemplateRelay() throws Exception {
        doTestInOutOutOfBandHeaderCamelTemplate("direct:relayProducer");
    }
    
    protected void doTestInOutOfBandHeaderCamelTemplate(String producerUri) throws Exception {
        // START SNIPPET: sending
        Exchange senderExchange = new DefaultExchange(context, ExchangePattern.InOut);
        final List<Object> params = new ArrayList<Object>();
        Me me = new Me();
        me.setFirstName("john");
        me.setLastName("Doh");

        params.add(me);
        senderExchange.getIn().setBody(params);
        senderExchange.getIn().setHeader(CxfConstants.OPERATION_NAME, "inOutOfBandHeader");

        List<Header> headers = buildOutOfBandHeaderList(false);
        Map<String, Object> requestContext = new HashMap<String, Object>();
        requestContext.put(Header.HEADER_LIST, headers);
        senderExchange.getIn().setHeader(Client.REQUEST_CONTEXT, requestContext);

        Exchange exchange = template.send(producerUri, senderExchange);

        org.apache.camel.Message out = exchange.getOut();
        MessageContentsList result = (MessageContentsList)out.getBody();
        Map<String, Object> responseContext = CastUtils.cast((Map)out.getHeader(Client.RESPONSE_CONTEXT));
        assertNotNull(responseContext);
        assertTrue("Expected the out of band header to propagate but it didn't", 
                   result.get(0) != null && ((Me)result.get(0)).getFirstName().equals("pass"));

    }

    protected void doTestOutOutOfBandHeaderCamelTemplate(String producerUri) throws Exception {
        // START SNIPPET: sending
        Exchange senderExchange = new DefaultExchange(context, ExchangePattern.InOut);
        final List<Object> params = new ArrayList<Object>();
        Me me = new Me();
        me.setFirstName("john");
        me.setLastName("Doh");

        params.add(me);
        senderExchange.getIn().setBody(params);
        senderExchange.getIn().setHeader(CxfConstants.OPERATION_NAME, "outOutOfBandHeader");

        Exchange exchange = template.send(producerUri, senderExchange);

        org.apache.camel.Message out = exchange.getOut();
        MessageContentsList result = (MessageContentsList)out.getBody();
        assertTrue("Expected the out of band header to propagate but it didn't", 
                   result.get(0) != null && ((Me)result.get(0)).getFirstName().equals("pass"));
        Map<String, Object> responseContext = CastUtils.cast((Map)out.getHeader(Client.RESPONSE_CONTEXT));
        assertNotNull(responseContext);
        validateReturnedOutOfBandHeader(responseContext);
    }


    public void doTestInOutOutOfBandHeaderCamelTemplate(String producerUri) throws Exception {
        // START SNIPPET: sending
        Exchange senderExchange = new DefaultExchange(context, ExchangePattern.InOut);
        final List<Object> params = new ArrayList<Object>();
        Me me = new Me();
        me.setFirstName("john");
        me.setLastName("Doh");

        params.add(me);
        senderExchange.getIn().setBody(params);
        senderExchange.getIn().setHeader(CxfConstants.OPERATION_NAME, "inoutOutOfBandHeader");

        List<Header> inHeaders = buildOutOfBandHeaderList(false);
        Map<String, Object> requestContext = new HashMap<String, Object>();
        requestContext.put(Header.HEADER_LIST, inHeaders);
        senderExchange.getIn().setHeader(Client.REQUEST_CONTEXT, requestContext);

        Exchange exchange = template.send(producerUri, senderExchange);

        org.apache.camel.Message out = exchange.getOut();
        MessageContentsList result = (MessageContentsList)out.getBody();
        assertTrue("Expected the out of band header to propagate but it didn't", 
                   result.get(0) != null && ((Me)result.get(0)).getFirstName().equals("pass"));
        Map<String, Object> responseContext = CastUtils.cast((Map)out.getHeader(Client.RESPONSE_CONTEXT));
        assertNotNull(responseContext);
        validateReturnedOutOfBandHeader(responseContext);
    }

    protected static void validateReturnedOutOfBandHeader(HeaderTester proxy) {
        validateReturnedOutOfBandHeader(proxy, true);
    }
    
    protected static void validateReturnedOutOfBandHeader(HeaderTester proxy, boolean expect) {
        InvocationHandler handler  = Proxy.getInvocationHandler(proxy);
        BindingProvider  bp = null;
        if (!(handler instanceof BindingProvider)) {
            fail("Unable to cast dynamic proxy InocationHandler to BindingProvider type");
        }

        bp = (BindingProvider)handler;
        Map<String, Object> responseContext = bp.getResponseContext();
        validateReturnedOutOfBandHeader(responseContext, expect);
    }
    
    protected static void validateReturnedOutOfBandHeader(Map<String, Object> responseContext) {
        validateReturnedOutOfBandHeader(responseContext, true);
    }
    
    protected static void validateReturnedOutOfBandHeader(Map<String, Object> responseContext, boolean expect) {
        OutofBandHeader hdrToTest = null;
        List oobHdr = (List)responseContext.get(Header.HEADER_LIST);
        if (!expect) {
            if (oobHdr == null || (oobHdr != null && oobHdr.size() == 0)) {
                return;
            }
            fail("Should have got *no* out-of-band headers, but some were found");
        }
        if (oobHdr == null) {
            fail("Should have got List of out-of-band headers");
        }

        assertTrue("HeaderHolder list expected to conain 1 object received " + oobHdr.size(),
                   oobHdr.size() == 1);

        if (oobHdr != null & oobHdr instanceof List) {
            Iterator iter = oobHdr.iterator();
            while (iter.hasNext()) {
                Object hdr = iter.next();
                if (hdr instanceof Header) {
                    Header hdr1 = (Header)hdr;
                    if (hdr1.getObject() instanceof Node) {
                        try {
                            JAXBElement job = (JAXBElement)JAXBContext
                                .newInstance(org.apache.cxf.outofband.header.ObjectFactory.class)
                                .createUnmarshaller().unmarshal((Node)hdr1.getObject());
                            hdrToTest = (OutofBandHeader)job.getValue();
                        } catch (JAXBException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        }

        assertNotNull("out-of-band header should not be null", hdrToTest);
        assertTrue("Expected out-of-band Header name testOobReturnHeaderName recevied :"
                   + hdrToTest.getName(), "testOobReturnHeaderName".equals(hdrToTest.getName()));
        assertTrue("Expected out-of-band Header value testOobReturnHeaderValue recevied :"
                   + hdrToTest.getValue(), "testOobReturnHeaderValue".equals(hdrToTest.getValue()));
        assertTrue("Expected out-of-band Header attribute testReturnHdrAttribute recevied :"
                   + hdrToTest.getHdrAttribute(), "testReturnHdrAttribute"
            .equals(hdrToTest.getHdrAttribute()));
    }
    
    protected static void validateReturnedOutOfBandHeaderWithInsertion(Map<String, Object> responseContext, boolean expect) {
        List<OutofBandHeader> hdrToTest = new ArrayList<OutofBandHeader>();
        List oobHdr = (List)responseContext.get(Header.HEADER_LIST);
        if (!expect) {
            if (oobHdr == null || (oobHdr != null && oobHdr.size() == 0)) {
                return;
            }
            fail("Should have got *no* out-of-band headers, but some were found");
        }
        if (oobHdr == null) {
            fail("Should have got List of out-of-band headers");
        }

        assertTrue("HeaderHolder list expected to conain 2 object received " + oobHdr.size(),
                   oobHdr.size() == 2);
        
        int i = 0;
        if (oobHdr != null & oobHdr instanceof List) {
            Iterator iter = oobHdr.iterator();
            while (iter.hasNext()) {
                Object hdr = iter.next();
                if (hdr instanceof Header) {
                    Header hdr1 = (Header)hdr;
                    if (hdr1.getObject() instanceof Node) {
                        try {
                            JAXBElement job = (JAXBElement)JAXBContext
                                .newInstance(org.apache.cxf.outofband.header.ObjectFactory.class)
                                .createUnmarshaller().unmarshal((Node)hdr1.getObject());
                            hdrToTest.add((OutofBandHeader)job.getValue());
                        } catch (JAXBException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        }

        assertTrue("out-of-band header should not be null", hdrToTest.size() > 0);
        assertTrue("Expected out-of-band Header name testOobReturnHeaderName recevied :"
                   + hdrToTest.get(0).getName(), "testOobReturnHeaderName".equals(hdrToTest.get(0).getName()));
        assertTrue("Expected out-of-band Header value testOobReturnHeaderValue recevied :"
                   + hdrToTest.get(0).getValue(), "testOobReturnHeaderValue".equals(hdrToTest.get(0).getValue()));
        assertTrue("Expected out-of-band Header attribute testReturnHdrAttribute recevied :"
                   + hdrToTest.get(0).getHdrAttribute(), "testReturnHdrAttribute"
            .equals(hdrToTest.get(0).getHdrAttribute()));
        
        assertTrue("Expected out-of-band Header name New_testOobHeader recevied :"
                   + hdrToTest.get(1).getName(), "New_testOobHeader".equals(hdrToTest.get(1).getName()));
        assertTrue("Expected out-of-band Header value New_testOobHeaderValue recevied :"
                   + hdrToTest.get(1).getValue(), "New_testOobHeaderValue".equals(hdrToTest.get(1).getValue()));
        assertTrue("Expected out-of-band Header attribute testHdrAttribute recevied :"
                   + hdrToTest.get(1).getHdrAttribute(), "testHdrAttribute"
            .equals(hdrToTest.get(1).getHdrAttribute()));
    }

    public static class InsertRequestOutHeaderProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            CxfMessage message = exchange.getIn().getBody(CxfMessage.class);
            Message cxf = message.getMessage();
            List<SoapHeader> soapHeaders = (List)cxf.get(Header.HEADER_LIST);

            // Insert a new header
            String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?><outofbandHeader "
                + "xmlns=\"http://cxf.apache.org/outofband/Header\" hdrAttribute=\"testHdrAttribute\" "
                + "xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" soap:mustUnderstand=\"1\">"
                + "<name>New_testOobHeader</name><value>New_testOobHeaderValue</value></outofbandHeader>";
            
            SoapHeader newHeader = new SoapHeader(soapHeaders.get(0).getName(),
                                                  DOMUtils.readXml(new StringReader(xml)).getDocumentElement());
            // make sure direction is IN since it is a request message.
            newHeader.setDirection(Direction.DIRECTION_IN);
            //newHeader.setMustUnderstand(false);
            soapHeaders.add(newHeader);
            
        }
        
    }
    
    public static class InsertResponseOutHeaderProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            CxfMessage message = exchange.getIn().getBody(CxfMessage.class);
            Map responseContext = (Map)message.getMessage().get(Client.RESPONSE_CONTEXT);
            List<SoapHeader> soapHeaders = (List)responseContext.get(Header.HEADER_LIST);
            
            // Insert a new header
            String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?><outofbandHeader "
                + "xmlns=\"http://cxf.apache.org/outofband/Header\" hdrAttribute=\"testHdrAttribute\" "
                + "xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" soap:mustUnderstand=\"1\">"
                + "<name>New_testOobHeader</name><value>New_testOobHeaderValue</value></outofbandHeader>";
            SoapHeader newHeader = new SoapHeader(soapHeaders.get(0).getName(),
                           DOMUtils.readXml(new StringReader(xml)).getDocumentElement());
            // make sure direction is OUT since it is a response message.
            newHeader.setDirection(Direction.DIRECTION_OUT);
            //newHeader.setMustUnderstand(false);
            soapHeaders.add(newHeader);
                                           
        }
    }
}
