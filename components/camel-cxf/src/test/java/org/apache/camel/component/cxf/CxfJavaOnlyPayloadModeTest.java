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

import org.w3c.dom.Document;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * A unit test for java only CXF in payload mode
 */
public class CxfJavaOnlyPayloadModeTest extends CamelTestSupport {
    private static int port1 = CXFTestSupport.getPort1(); 

    private String url = "cxf://http://localhost:" + port1 + "/CxfJavaOnlyPayloadModeTest/helloworld"
        + "?wsdlURL=classpath:person.wsdl"
        + "&serviceName={http://camel.apache.org/wsdl-first}PersonService"
        + "&portName={http://camel.apache.org/wsdl-first}soap"
        + "&dataFormat=PAYLOAD"
        + "&properties.exceptionMessageCauseEnabled=true&properties.faultStackTraceEnabled=true";
    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @Test
    public void testCxfJavaOnly() throws Exception {
        String s = "<GetPerson xmlns=\"http://camel.apache.org/wsdl-first/types\"><personId>123</personId></GetPerson>";
        Document xml = context.getTypeConverter().convertTo(Document.class, s);

        Object output = template.requestBody(url, xml);
        assertNotNull(output);

        // using CxfPayload in payload mode
        CxfPayload<?> payload = (CxfPayload<?>) output;

        // convert the payload body to string
        String reply = context.getTypeConverter().convertTo(String.class, payload.getBody().get(0));
        assertNotNull(reply);

        assertTrue(reply.contains("<personId>123</personId"));
        assertTrue(reply.contains("<ssn>456</ssn"));
        assertTrue(reply.contains("<name>Donald Duck</name"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(url).process(new Processor() {
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
