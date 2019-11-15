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

import javax.xml.namespace.QName;

import org.w3c.dom.Document;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * A unit test for java only CXF in payload mode
 */
public class CxfJavaOnlyCamelContextAwareTest extends CamelTestSupport {
    private static int port1 = CXFTestSupport.getPort1(); 

    @Test
    public void testCxfEndpointHasCamelContext() throws Exception {
        String s = "<GetPerson xmlns=\"http://camel.apache.org/wsdl-first/types\"><personId>123</personId></GetPerson>";
        Document xml = context.getTypeConverter().convertTo(Document.class, s);

        log.info("Endpoints: {}", context.getEndpoints());
        Object output = template.requestBody("personService", xml);
        assertNotNull(output);

        // using CxfPayload in payload mode
        CxfPayload<?> payload = (CxfPayload<?>) output;

        // convert the payload body to string
        String reply = context.getTypeConverter().convertTo(String.class, payload.getBody().get(0));
        assertNotNull(reply);
        
        assertTrue(reply.contains("<personId>123</personId"));
        assertTrue(reply.contains("<ssn>456</ssn"));
        assertTrue(reply.contains("<name>Donald Duck</name"));
        
        assertTrue(context.getEndpoint("personService") instanceof CamelContextAware);
        assertNotNull("CamelContext should be set on CxfEndpoint", context.getEndpoint("personService").getCamelContext());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                CxfEndpoint endpoint = new CxfEndpoint();
                endpoint.setAddress("http://localhost:" + port1 + "/PersonService");
                endpoint.setServiceNameAsQName(new QName("http://camel.apache.org/wsdl-first", "PersonService"));
                endpoint.setPortNameAsQName(new QName("http://camel.apache.org/wsdl-first", "soap"));
                endpoint.setWsdlURL("classpath:person.wsdl");
                endpoint.setDataFormat(DataFormat.PAYLOAD);
                context.addEndpoint("personService", endpoint);

                from(endpoint).process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        String s = "<GetPersonResponse xmlns=\"http://camel.apache.org/wsdl-first/types\">"
                                + "<personId>123</personId><ssn>456</ssn><name>Donald Duck</name>"
                                + "</GetPersonResponse>";

                        Document xml = context.getTypeConverter().convertTo(Document.class, s);
                        exchange.getOut().setBody(xml);
                    }
                });
            }
        };
    }
}
