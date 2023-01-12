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
import java.util.ArrayList;
import java.util.List;

import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Holder;
import jakarta.xml.ws.WebServiceException;

import javax.xml.namespace.QName;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.camel.wsdl_first.JaxwsTestHandler;
import org.apache.camel.wsdl_first.Person;
import org.apache.camel.wsdl_first.PersonService;
import org.apache.camel.wsdl_first.UnknownPersonFault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractCxfWsdlFirstTest extends CamelSpringTestSupport {
    static int port1 = CXFTestSupport.getPort1();

    public static int getPort1() {
        return port1;
    }

    public static int getPort2() {
        return CXFTestSupport.getPort2();
    }

    @Test
    public void testInvokingServiceFromCXFClient() throws Exception {

        JaxwsTestHandler fromHandler = getMandatoryBean(JaxwsTestHandler.class, "fromEndpointJaxwsHandler");
        fromHandler.reset();

        JaxwsTestHandler toHandler = getMandatoryBean(JaxwsTestHandler.class, "toEndpointJaxwsHandler");
        toHandler.reset();

        URL wsdlURL = getClass().getClassLoader().getResource("person.wsdl");
        PersonService ss = new PersonService(wsdlURL, new QName("http://camel.apache.org/wsdl-first", "PersonService"));
        Person client = ss.getSoap();
        ((BindingProvider) client).getRequestContext()
                .put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                        "http://localhost:" + getPort2() + "/" + getClass().getSimpleName()
                                                                + "/PersonService/");

        Holder<String> personId = new Holder<>();
        personId.value = "hello";
        Holder<String> ssn = new Holder<>();
        Holder<String> name = new Holder<>();
        client.getPerson(personId, ssn, name);
        assertEquals("Bonjour", name.value, "we should get the right answer from router");

        personId.value = "";
        try {
            client.getPerson(personId, ssn, name);
            fail("We expect to get the UnknowPersonFault here");
        } catch (UnknownPersonFault fault) {
            // We expect to get fault here
        }

        personId.value = "Invoking getPerson with invalid length string, expecting exception...xxxxxxxxx";
        try {
            client.getPerson(personId, ssn, name);
            fail("We expect to get the WebSerivceException here");
        } catch (WebServiceException ex) {
            // Caught expected WebServiceException here
            assertTrue(ex.getMessage().indexOf("MyStringType") > 0
                    || ex.getMessage().indexOf("Could not parse the XML stream") != -1
                    || ex.getMessage().indexOf("the required maximum is 30") > 0,
                    "Should get the xml vaildate error! " + ex.getMessage());
        }

        verifyJaxwsHandlers(fromHandler, toHandler);
    }

    protected void verifyJaxwsHandlers(JaxwsTestHandler fromHandler, JaxwsTestHandler toHandler) {
        assertEquals(2, fromHandler.getFaultCount());
        assertEquals(4, fromHandler.getMessageCount());
        // Changed to use noErrorhandler and now the message will not be sent again.
        assertEquals(3, toHandler.getMessageCount());
        assertEquals(1, toHandler.getFaultCount());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInvokingServiceWithCamelProducer() throws Exception {
        Exchange exchange = sendJaxWsMessageWithHolders("hello");
        assertEquals(false, exchange.isFailed(), "The request should be handled sucessfully");
        org.apache.camel.Message out = exchange.getMessage();
        List<Object> result = out.getBody(List.class);
        assertEquals(4, result.size(), "The result list should not be empty");
        Holder<String> name = (Holder<String>) result.get(3);
        assertEquals("Bonjour", name.value, "we should get the right answer from router");

        exchange = sendJaxWsMessageWithHolders("");
        assertEquals(true, exchange.isFailed(), "We should get a fault here");
        Throwable ex = exchange.getException();
        assertTrue(ex instanceof UnknownPersonFault, "We should get the UnknowPersonFault here");
    }

    protected Exchange sendJaxWsMessageWithHolders(final String personIdString) {
        Exchange exchange = template.send("direct:producer", new Processor() {
            public void process(final Exchange exchange) {
                final List<Object> params = new ArrayList<>();
                Holder<String> personId = new Holder<>();
                personId.value = personIdString;
                params.add(personId);
                Holder<String> ssn = new Holder<>();
                Holder<String> name = new Holder<>();
                params.add(ssn);
                params.add(name);
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, "GetPerson");
            }
        });
        return exchange;
    }
}
