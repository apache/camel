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

import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Holder;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.wsdl_first.Person;
import org.apache.camel.wsdl_first.PersonImpl;
import org.apache.camel.wsdl_first.PersonService;
import org.apache.camel.wsdl_first.UnknownPersonFault;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class CXFWsdlOnlyPayloadModeNoSpringTest extends CamelTestSupport {
    
    protected static final String SERVICE_NAME_PROP =  "serviceName=";
    protected static final String PORT_NAME_PROP = "portName={http://camel.apache.org/wsdl-first}soap";
    protected static final String WSDL_URL_PROP = "wsdlURL=classpath:person.wsdl";
    protected static Endpoint endpoint;

    @BeforeClass
    public static void startService() {
        endpoint = Endpoint.publish("http://localhost:8093/PersonService", new PersonImpl());
    }
    
    @AfterClass
    public static void stopService() {
        if (endpoint != null) {
            endpoint.stop();
        }

    }
    
    protected void checkSOAPAction(Exchange exchange) {
        // check the SOAPAction to be null
        assertNull(exchange.getIn().getHeader("SOAPAction"));
        
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("cxf://http://localhost:8092/PersonService?" + PORT_NAME_PROP + "&" + SERVICE_NAME_PROP + getServiceName() + "&" + WSDL_URL_PROP + "&dataFormat=" + getDataFormat())
                    .process(new Processor() {

                        @Override
                        public void process(Exchange exchange) throws Exception {
                            checkSOAPAction(exchange);
                        }
                        
                    })
                    .to("cxf://http://localhost:8093/PersonService?" + PORT_NAME_PROP + "&" + SERVICE_NAME_PROP + getServiceName() + "&" + WSDL_URL_PROP + "&dataFormat=" + getDataFormat());
            }
        };
    }
 
    protected String getDataFormat() {
        return "PAYLOAD";
    }

    @Test
    public void testRoutes() throws Exception {
        URL wsdlURL = getClass().getClassLoader().getResource("person.wsdl");
        PersonService ss = new PersonService(wsdlURL, QName.valueOf(getServiceName()));

        Person client = ss.getSoap();
        
        Client c = ClientProxy.getClient(client);
        c.getInInterceptors().add(new LoggingInInterceptor());
        c.getOutInterceptors().add(new LoggingOutInterceptor());
        
        Holder<String> personId = new Holder<String>();
        personId.value = "hello";
        Holder<String> ssn = new Holder<String>();
        Holder<String> name = new Holder<String>();
        client.getPerson(personId, ssn, name);
        assertEquals("Bonjour", name.value);

    }
    
    @Test
    public void testApplicationFault() {
        URL wsdlURL = getClass().getClassLoader().getResource("person.wsdl");
        PersonService ss = new PersonService(wsdlURL, QName.valueOf(getServiceName()));

        Person client = ss.getSoap();
        
        Client c = ClientProxy.getClient(client);
        c.getInInterceptors().add(new LoggingInInterceptor());
        c.getOutInterceptors().add(new LoggingOutInterceptor());
        
        Holder<String> personId = new Holder<String>();
        personId.value = "";
        Holder<String> ssn = new Holder<String>();
        Holder<String> name = new Holder<String>();
        Throwable t = null;
        try {
            client.getPerson(personId, ssn, name);
            fail("expect UnknownPersonFault");
        } catch (UnknownPersonFault e) {
            t = e;
        }
        
        assertNotNull(t);
        assertTrue(t instanceof UnknownPersonFault);
        
    }
    
    protected String getServiceName() {
        return "{http://camel.apache.org/wsdl-first}PersonService";
    }
}
