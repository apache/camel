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

import java.io.StringReader;
import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Holder;

import org.w3c.dom.Element;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.wsdl_first.Person;
import org.apache.camel.wsdl_first.PersonService;
import org.apache.camel.wsdl_first.UnknownPersonFault;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.staxutils.StaxUtils;
import org.junit.Test;

/**
 * Unit test to verify CxfConsumer to generate SOAP fault in PAYLOAD mode
 */
public class CxfConsumerPayloadFaultTest extends CamelTestSupport {
    protected static final String PORT_NAME_PROP = "portName={http://camel.apache.org/wsdl-first}soap";
    protected static final String SERVICE_NAME = "{http://camel.apache.org/wsdl-first}PersonService";
    protected static final String SERVICE_NAME_PROP =  "serviceName=" + SERVICE_NAME;
    protected static final String WSDL_URL_PROP = "wsdlURL=classpath:person.wsdl";
    

    protected static final String DETAILS = "<detail><UnknownPersonFault xmlns=\"http://camel.apache.org/wsdl-first/types\">"
        + "<personId></personId></UnknownPersonFault></detail>";
    

    
    protected final String serviceAddress = "http://localhost:" + CXFTestSupport.getPort1() 
        + "/" + getClass().getSimpleName() + "/PersonService";
    protected final String fromURI = "cxf://" + serviceAddress + "?" 
        + PORT_NAME_PROP + "&" + SERVICE_NAME_PROP + "&" + WSDL_URL_PROP + "&dataFormat=payload";
    
    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(fromURI).process(new Processor() {
                    public void process(final Exchange exchange) throws Exception {
                        QName faultCode = new QName("http://schemas.xmlsoap.org/soap/envelope/", "Server");
                        SoapFault fault = new SoapFault("Get the null value of person name", faultCode);
                        Element details = StaxUtils.read(new StringReader(DETAILS)).getDocumentElement();
                        fault.setDetail(details);
                        exchange.setException(fault);
                        
                    }
                });
            }
        };
    }

    @Test
    public void testInvokingFromCxfClient() throws Exception {
        URL wsdlURL = getClass().getClassLoader().getResource("person.wsdl");
        PersonService ss = new PersonService(wsdlURL, QName.valueOf(SERVICE_NAME));

        Person client = ss.getSoap();
                
        Client c = ClientProxy.getClient(client);
        c.getInInterceptors().add(new LoggingInInterceptor());
        c.getOutInterceptors().add(new LoggingOutInterceptor());
        ((BindingProvider)client).getRequestContext()
            .put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, serviceAddress);
        
        Holder<String> personId = new Holder<>();
        personId.value = "";
        Holder<String> ssn = new Holder<>();
        Holder<String> name = new Holder<>();
        Throwable t = null;
        try {
            client.getPerson(personId, ssn, name);
            fail("expect UnknownPersonFault");
        } catch (UnknownPersonFault e) {
            t = e;
            assertEquals("Get the wrong fault detail", 
                         "", e.getFaultInfo().getPersonId());
        }
        
        assertNotNull(t);
        assertTrue(t instanceof UnknownPersonFault);

    }
}
