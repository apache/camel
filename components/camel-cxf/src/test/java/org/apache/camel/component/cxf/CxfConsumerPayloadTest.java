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

import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.cxf.binding.soap.SoapHeader;


public class CxfConsumerPayloadTest extends CxfConsumerMessageTest {
        
    private static final String ECHO_RESPONSE = "<ns1:echoResponse xmlns:ns1=\"http://cxf.component.camel.apache.org/\">"
            + "<return xmlns=\"http://cxf.component.camel.apache.org/\">echo Hello World!</return>"
            + "</ns1:echoResponse>";
    private static final String ECHO_BOOLEAN_RESPONSE = "<ns1:echoBooleanResponse xmlns:ns1=\"http://cxf.component.camel.apache.org/\">"
            + "<return xmlns=\"http://cxf.component.camel.apache.org/\">true</return>"
            + "</ns1:echoBooleanResponse>";
    private static final String ECHO_REQUEST = "<ns1:echo xmlns:ns1=\"http://cxf.component.camel.apache.org/\">"
            + "<arg0 xmlns=\"http://cxf.component.camel.apache.org/\">Hello World!</arg0></ns1:echo>";
    private static final String ECHO_BOOLEAN_REQUEST = "<ns1:echoBoolean xmlns:ns1=\"http://cxf.component.camel.apache.org/\">"
            + "<arg0 xmlns=\"http://cxf.component.camel.apache.org/\">true</arg0></ns1:echoBoolean>";

    // START SNIPPET: payload
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(SIMPLE_ENDPOINT_URI + "&dataFormat=PAYLOAD").to("log:info").process(new Processor() {
                    @SuppressWarnings("unchecked")
                    public void process(final Exchange exchange) throws Exception {
                        CxfPayload<SoapHeader> requestPayload = exchange.getIn().getBody(CxfPayload.class);
                        List<Element> inElements = requestPayload.getBody();
                        List<Element> outElements = new ArrayList<Element>();
                        // You can use a customer toStringConverter to turn a CxfPayLoad message into String as you want                        
                        String request = exchange.getIn().getBody(String.class);
                        XmlConverter converter = new XmlConverter();
                        String documentString = ECHO_RESPONSE;
                        if (inElements.get(0).getLocalName().equals("echoBoolean")) {
                            documentString = ECHO_BOOLEAN_RESPONSE;
                            assertEquals("Get a wrong request", ECHO_BOOLEAN_REQUEST, request);
                        } else {
                            assertEquals("Get a wrong request", ECHO_REQUEST, request);
                        }
                        Document outDocument = converter.toDOMDocument(documentString);
                        outElements.add(outDocument.getDocumentElement());
                        // set the payload header with null
                        CxfPayload<SoapHeader> responsePayload = new CxfPayload<SoapHeader>(null, outElements);
                        exchange.getOut().setBody(responsePayload); 
                    }
                });
            }
        };
    }
    // END SNIPPET: payload

}
