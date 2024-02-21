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
package org.apache.camel.component.cxf.jaxws;

import java.net.URL;

import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Endpoint;
import jakarta.xml.ws.Holder;

import javax.xml.namespace.QName;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.wsdl_first.Person;
import org.apache.camel.wsdl_first.PersonImpl;
import org.apache.camel.wsdl_first.PersonService;
import org.apache.camel.wsdl_first.UnknownPersonFault;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.frontend.ClientProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CXFWsdlOnlyPayloadModeNoSpringTest extends CamelTestSupport {

    protected static final String SERVICE_NAME_PROP = "serviceName=";
    protected static final String PORT_NAME_PROP = "portName={http://camel.apache.org/wsdl-first}soap";
    protected static final String WSDL_URL_PROP = "wsdlURL=classpath:person.wsdl";
    protected Endpoint endpoint;

    protected int port1 = CXFTestSupport.getPort1();
    protected int port2 = CXFTestSupport.getPort2();

    @BeforeEach
    public void startService() {
        endpoint = Endpoint.publish("http://localhost:" + port1 + "/" + getClass().getSimpleName()
                                    + "/PersonService",
                new PersonImpl());
    }

    @AfterEach
    public void stopService() {
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
        final String cn = getClass().getSimpleName();
        return new RouteBuilder() {
            public void configure() {
                from("cxf://http://localhost:" + port2
                     + "/" + cn + "/PersonService?" + PORT_NAME_PROP + "&" + SERVICE_NAME_PROP + getServiceName() + "&"
                     + WSDL_URL_PROP + "&dataFormat=" + getDataFormat())
                        .process(new Processor() {

                            @Override
                            public void process(Exchange exchange) throws Exception {
                                checkSOAPAction(exchange);
                            }

                        })
                        .to("cxf://http://localhost:" + port1
                            + "/" + cn + "/PersonService?" + PORT_NAME_PROP + "&" + SERVICE_NAME_PROP + getServiceName()
                            + "&" + WSDL_URL_PROP + "&dataFormat=" + getDataFormat());
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

        ((BindingProvider) client).getRequestContext()
                .put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                        "http://localhost:" + port1 + "/" + getClass().getSimpleName() + "/PersonService");
        c.getInInterceptors().add(new LoggingInInterceptor());
        c.getOutInterceptors().add(new LoggingOutInterceptor());

        Holder<String> personId = new Holder<>();
        personId.value = "hello";
        Holder<String> ssn = new Holder<>();
        Holder<String> name = new Holder<>();
        client.getPerson(personId, ssn, name);
        assertEquals("Bonjour", name.value);

    }

    @Test
    public void testApplicationFault() {
        URL wsdlURL = getClass().getClassLoader().getResource("person.wsdl");
        PersonService ss = new PersonService(wsdlURL, QName.valueOf(getServiceName()));

        Person client = ss.getSoap();
        ((BindingProvider) client).getRequestContext()
                .put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                        "http://localhost:" + port1 + "/"
                                                                + getClass().getSimpleName() + "/PersonService");

        Client c = ClientProxy.getClient(client);
        c.getInInterceptors().add(new LoggingInInterceptor());
        c.getOutInterceptors().add(new LoggingOutInterceptor());

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
        }

        assertNotNull(t);
        assertTrue(t instanceof UnknownPersonFault);

    }

    protected String getServiceName() {
        return "{http://camel.apache.org/wsdl-first}PersonService";
    }
}
