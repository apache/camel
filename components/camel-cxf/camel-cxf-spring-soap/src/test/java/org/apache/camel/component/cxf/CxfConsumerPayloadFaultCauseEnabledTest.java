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

import java.net.URL;
import java.util.ResourceBundle;

import jakarta.xml.soap.SOAPFault;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Holder;
import jakarta.xml.ws.soap.SOAPFaultException;

import javax.xml.namespace.QName;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.camel.wsdl_first.Person;
import org.apache.camel.wsdl_first.PersonService;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.interceptor.Fault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test to verify CxfConsumer to generate SOAP fault in PAYLOAD mode with the exception cause returned
 */
public class CxfConsumerPayloadFaultCauseEnabledTest extends CamelSpringTestSupport {
    protected static final QName SERVICE_QNAME = new QName("http://camel.apache.org/wsdl-first", "PersonService");
    protected final String serviceAddress = "http://localhost:" + CXFTestSupport.getPort1()
                                            + "/" + getClass().getSimpleName() + "/PersonService";

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        CXFTestSupport.getPort1();
        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("cxf:bean:consumerEndpoint").process(new Processor() {
                    public void process(final Exchange exchange) throws Exception {
                        Throwable cause = new IllegalArgumentException("Homer");
                        Fault fault = new Fault("Someone messed up the service.", (ResourceBundle) null, cause);
                        exchange.setException(fault);
                    }
                });
            }
        };
    }

    @Test
    public void testInvokingFromCxfClient() throws Exception {
        this.getCamelContextService();
        URL wsdlURL = getClass().getClassLoader().getResource("person.wsdl");
        PersonService ss = new PersonService(wsdlURL, SERVICE_QNAME);

        Person client = ss.getSoap();

        Client c = ClientProxy.getClient(client);
        c.getInInterceptors().add(new LoggingInInterceptor());
        c.getOutInterceptors().add(new LoggingOutInterceptor());
        ((BindingProvider) client).getRequestContext()
                .put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, serviceAddress);

        Holder<String> personId = new Holder<>();
        personId.value = "";
        Holder<String> ssn = new Holder<>();
        Holder<String> name = new Holder<>();
        try {
            client.getPerson(personId, ssn, name);
            fail("SOAPFault expected!");
        } catch (Exception e) {
            assertTrue(e instanceof SOAPFaultException);
            SOAPFault fault = ((SOAPFaultException) e).getFault();
            assertEquals("Someone messed up the service. Caused by: Homer", fault.getFaultString());
        }
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/CxfConsumerFaultCauseEnabledBeans.xml");
    }
}
