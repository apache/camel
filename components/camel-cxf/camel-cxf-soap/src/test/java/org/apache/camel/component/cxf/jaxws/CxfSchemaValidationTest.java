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
import jakarta.xml.ws.Holder;
import jakarta.xml.ws.soap.SOAPFaultException;

import javax.xml.namespace.QName;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.wsdl_first.Person;
import org.apache.camel.wsdl_first.PersonService;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.frontend.ClientProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CxfSchemaValidationTest extends CamelTestSupport {

    protected static final String PORT_NAME_PROP = "portName={http://camel.apache.org/wsdl-first}soap";
    protected static final String SERVICE_NAME = "{http://camel.apache.org/wsdl-first}PersonService";
    protected static final String SERVICE_NAME_PROP = "serviceName=" + SERVICE_NAME;
    protected static final String WSDL_URL_PROP = "wsdlURL=classpath:person.wsdl";

    protected final String serviceAddressValidationEnabled = "http://localhost:" + CXFTestSupport.getPort1()
                                                             + "/" + getClass().getSimpleName() + "/PersonService";

    protected final String serviceAddressValidationDisabled = "http://localhost:" + CXFTestSupport.getPort2()
                                                              + "/" + getClass().getSimpleName() + "/PersonService";

    protected final String cxfServerUriValidationEnabled = "cxf://" + serviceAddressValidationEnabled + "?"
                                                           + PORT_NAME_PROP + "&" + SERVICE_NAME_PROP + "&" + WSDL_URL_PROP
                                                           + "&dataFormat=payload&schemaValidationEnabled=true";
    protected final String cxfServerUriValidationDisabled
            = "cxf://" + serviceAddressValidationDisabled + "?" + PORT_NAME_PROP + "&"
              + SERVICE_NAME_PROP + "&" + WSDL_URL_PROP + "&dataFormat=payload";

    protected final String cxfProducerUriValidationEnabled
            = "cxf://" + serviceAddressValidationDisabled + "?" + PORT_NAME_PROP + "&"
              + SERVICE_NAME_PROP + "&" + WSDL_URL_PROP + "&dataFormat=payload&schemaValidationEnabled=true";

    protected final String cxfProducerUriValidationDisabled
            = "cxf://" + serviceAddressValidationDisabled + "?" + PORT_NAME_PROP + "&"
              + SERVICE_NAME_PROP + "&" + WSDL_URL_PROP + "&dataFormat=payload";

    protected final String clientUriValidationEnabled = "direct:validationEnabled";

    protected final String clientUriValidationDisabled = "direct:validationDisabled";

    protected final String notValidRequest = "<GetPerson xmlns='http://camel.apache.org/wsdl-first/types'>"
                                             //Max Length: 30,
                                             + "<personId>4yLKOBllJjx4SCXRMXoNiOFEzQfCNA8BSBsyPUaQ</personId>"
                                             + "</GetPerson>";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(cxfServerUriValidationEnabled).to("direct:result");

                from(cxfServerUriValidationDisabled).to("direct:result");

                from(clientUriValidationEnabled).to(cxfProducerUriValidationEnabled);

                from(clientUriValidationDisabled).to(cxfProducerUriValidationDisabled);

                from("direct:result")
                        .process(exchange -> {
                            String xml = "<GetPersonResponse xmlns=\"http://camel.apache.org/wsdl-first/types\">"
                                         + "<personId>123</personId><ssn>456</ssn><name>Donald Duck</name>"
                                         + "</GetPersonResponse>";

                            exchange.getMessage().setBody(xml);
                        });
            }
        };
    }

    @Test
    public void schemaValidationDisabledServerTest() throws Exception {
        // invoke the service with a non-valid message
        try {
            invokeService(serviceAddressValidationDisabled, RandomStringUtils.random(40, true, true));
        } catch (SOAPFaultException e) {
            fail("Do not expect an exception here");
        }
    }

    @Test
    public void schemaValidationEnabledServerTest() throws Exception {
        //first, invoke service with valid message. No exception should be thrown
        invokeService(serviceAddressValidationEnabled, RandomStringUtils.random(10, true, true));

        // then invoke the service with a non-valid message

        /*
            Generate a personId string that should cause a validation error:

        <simpleType name="MyStringType">
            <restriction base="string">
                <maxLength value="30" />
            </restriction>
        </simpleType>
        ......
        <xsd:element name="personId" type="tns:MyStringType"/>

        */
        try {
            invokeService(serviceAddressValidationEnabled, RandomStringUtils.random(40, true, true));
            fail("expect a Validation exception here");
        } catch (SOAPFaultException e) {
            assertEquals("the length of the value is 40, but the required maximum is 30.", e.getMessage(), "");
        }
    }

    @Test
    public void schemaValidationEnabledClientTest() {
        Exchange ex = template.send(clientUriValidationEnabled, exchange -> {
            exchange.getMessage().setBody(notValidRequest);
        });

        assertNotNull(ex.getException());
        assertTrue(ex.getException().getMessage().contains("cvc-maxLength-valid"));
    }

    @Test
    public void schemaValidationDisabledClientTest() {
        Exchange ex = template.send(clientUriValidationDisabled, exchange -> {
            exchange.getMessage().setBody(notValidRequest);
        });
        assertNull(ex.getException());

    }

    private void invokeService(String address, String personIdParam) throws Exception {
        URL wsdlURL = getClass().getClassLoader().getResource("person.wsdl");
        PersonService ss = new PersonService(wsdlURL, QName.valueOf(SERVICE_NAME));

        Person client = ss.getSoap();

        Client c = ClientProxy.getClient(client);
        c.getInInterceptors().add(new LoggingInInterceptor());
        c.getOutInterceptors().add(new LoggingOutInterceptor());
        ((BindingProvider) client).getRequestContext()
                .put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, address);

        Holder<String> personId = new Holder<>();

        personId.value = personIdParam;
        Holder<String> ssn = new Holder<>();
        Holder<String> name = new Holder<>();
        client.getPerson(personId, ssn, name);
    }
}
