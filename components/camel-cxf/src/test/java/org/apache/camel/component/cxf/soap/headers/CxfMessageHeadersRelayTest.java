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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
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
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.cxf.CxfConstants;
import org.apache.camel.component.cxf.CxfEndpoint;
import org.apache.camel.component.cxf.CxfHeaderFilterStrategy;
import org.apache.camel.component.cxf.MessageHeaderFilter;
import org.apache.camel.impl.DefaultExchange;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.headers.Header;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.jaxb.JAXBDataBinding;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.outofband.header.OutofBandHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit38.AbstractJUnit38SpringContextTests;


/**
 * This test suite verifies message header filter features
 *
 * @version $Revision$
 */
@ContextConfiguration
public class CxfMessageHeadersRelayTest extends AbstractJUnit38SpringContextTests {

    @Autowired
    protected CamelContext context;
    protected ProducerTemplate template;

    private Endpoint relayEndpoint;
    private Endpoint noRelayEndpoint;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        template = context.createProducerTemplate();

        relayEndpoint = Endpoint.publish("http://localhost:9090/HeaderService/", new HeaderTesterImpl());
        noRelayEndpoint = Endpoint.publish("http://localhost:7070/HeaderService/", new HeaderTesterImpl(false));
    }

    @Override
    protected void tearDown() throws Exception {
        
        if (relayEndpoint != null) {
            relayEndpoint.stop();
            relayEndpoint = null;
        }
        
        if (noRelayEndpoint != null) {
            noRelayEndpoint.stop();
            noRelayEndpoint = null;
        }

        super.tearDown();
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
        Thread.sleep(5000);
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
        Thread.sleep(5000);

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
        CxfEndpoint endpoint = (CxfEndpoint)context
            .getEndpoint("cxf:bean:serviceExtraRelays?headerFilterStrategy=#customMessageFilterStrategy");
        CxfHeaderFilterStrategy strategy = (CxfHeaderFilterStrategy)endpoint.getHeaderFilterStrategy();
        List<MessageHeaderFilter> filters = strategy.getMessageHeaderFilters();
        assertEquals("Expected number of filters ", 2, filters.size());
        Map<String, MessageHeaderFilter> messageHeaderFilterMap = strategy.getMessageHeaderFiltersMap();
        for (String ns : new CustomHeaderFilter().getActivationNamespaces()) {
            assertEquals("Expected a filter class for namespace: " + ns,
                         CustomHeaderFilter.class, messageHeaderFilterMap.get(ns).getClass());
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
}
