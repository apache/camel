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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.xml.sax.SAXException;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.headers.Header;
import org.apache.cxf.helpers.CastUtils;

public class CxfConsumerPayloadTest extends CxfConsumerTest {
    
    private static final String ECHO_METHOD = "ns1:echo xmlns:ns1=\"http://cxf.component.camel.apache.org/\"";

    private static final String ECHO_RESPONSE = "<ns1:echoResponse xmlns:ns1=\"http://cxf.component.camel.apache.org/\">"
            + "<return xmlns=\"http://cxf.component.camel.apache.org/\">echo Hello World!</return>"
            + "</ns1:echoResponse>";
    private static final String ECHO_BOOLEAN_RESPONSE = "<ns1:echoBooleanResponse xmlns:ns1=\"http://cxf.component.camel.apache.org/\">"
            + "<return xmlns=\"http://cxf.component.camel.apache.org/\">true</return>"
            + "</ns1:echoBooleanResponse>";
    // START SNIPPET: payload
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(SIMPLE_ENDPOINT_URI + "&dataFormat=PAYLOAD").to("log:info").process(new Processor() {
                    public void process(final Exchange exchange) throws Exception {
                        Message inMessage = exchange.getIn();
                        if (inMessage instanceof CxfMessage) {
                            CxfMessage cxfInMessage = (CxfMessage) inMessage;
                            CxfMessage cxfOutMessage = (CxfMessage) exchange.getOut();
                            List<Element> inElements = cxfInMessage.getMessage().get(List.class);
                            List<Element> outElements = new ArrayList<Element>();
                            XmlConverter converter = new XmlConverter();
                            String documentString = ECHO_RESPONSE;
                            if (inElements.get(0).getLocalName().equals("echoBoolean")) {
                                documentString = ECHO_BOOLEAN_RESPONSE;
                            }
                            org.apache.cxf.message.Exchange ex = ((CxfExchange)exchange).getExchange();
                            Endpoint ep = ex.get(Endpoint.class);
                            org.apache.cxf.message.Message response = ep.getBinding().createMessage();
                            Document outDocument = converter.toDOMDocument(documentString);
                            outElements.add(outDocument.getDocumentElement());
                            response.put(List.class, outElements);
                            cxfOutMessage.setMessage(response);                            
                        }
                    }
                });
            }
        };
    }
    // END SNIPPET: payload

}
