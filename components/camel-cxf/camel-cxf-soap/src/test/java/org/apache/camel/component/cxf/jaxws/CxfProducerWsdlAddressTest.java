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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A producer with no address in its URI calls the address that the WSDL gives for the port.
 */
public class CxfProducerWsdlAddressTest extends CamelTestSupport {

    private static final String SERVICE = "serviceName={http://camel.apache.org/wsdl-first}PersonService"
                                          + "&portName={http://camel.apache.org/wsdl-first}soap";

    private static final String REQUEST
            = "<GetPerson xmlns=\"http://camel.apache.org/wsdl-first/types\"><personId>1</personId></GetPerson>";

    private static final String RESPONSE
            = "<GetPersonResponse xmlns=\"http://camel.apache.org/wsdl-first/types\">"
              + "<personId>1</personId><ssn>000-000-0000</ssn><name>Bonjour</name></GetPersonResponse>";

    private static AvailablePortFinder.Port port;

    @BeforeAll
    static void allocatePorts() {
        port = AvailablePortFinder.find();
    }

    @AfterAll
    static void releasePorts() {
        if (port != null) {
            port.release();
        }
    }

    private static String serviceAddress() {
        return "http://localhost:" + port.getPort() + "/CxfProducerWsdlAddressTest/PersonService";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // the WSDL this service serves at ?wsdl has its own address as the soap:address of the port
                from("cxf://" + serviceAddress() + "?wsdlURL=classpath:person.wsdl&dataFormat=PAYLOAD&" + SERVICE)
                        .setBody(constant(RESPONSE));
            }
        };
    }

    @Test
    public void testAddressFromWsdl() {
        String uri = "cxf://?wsdlURL=RAW(" + serviceAddress() + "?wsdl)&dataFormat=PAYLOAD&defaultOperationName=GetPerson&"
                     + SERVICE;
        String reply = template.requestBody(uri, REQUEST, String.class);
        assertTrue(reply.contains("<name>Bonjour</name>"), reply);
    }
}
