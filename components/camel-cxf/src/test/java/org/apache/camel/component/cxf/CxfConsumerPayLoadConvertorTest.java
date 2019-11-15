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

import java.util.List;

import javax.xml.transform.Source;

import org.w3c.dom.Element;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.cxf.binding.soap.SoapHeader;

public class CxfConsumerPayLoadConvertorTest extends CxfConsumerPayloadTest {
    
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(simpleEndpointURI + "&dataFormat=PAYLOAD").to("log:info").process(new Processor() {
                    @SuppressWarnings("unchecked")
                    public void process(final Exchange exchange) throws Exception {
                        CxfPayload<SoapHeader> requestPayload = exchange.getIn().getBody(CxfPayload.class);
                        List<Source> inElements = requestPayload.getBodySources();
                        // You can use a customer toStringConverter to turn a CxfPayLoad message into String as you want                        
                        String request = exchange.getIn().getBody(String.class);
                        String documentString = ECHO_RESPONSE;
                        
                        Element in = new XmlConverter().toDOMElement(inElements.get(0));
                        // Just check the element namespace
                        if (!in.getNamespaceURI().equals(ELEMENT_NAMESPACE)) {
                            throw new IllegalArgumentException("Wrong element namespace");
                        }
                        if (in.getLocalName().equals("echoBoolean")) {
                            documentString = ECHO_BOOLEAN_RESPONSE;
                            checkRequest("ECHO_BOOLEAN_REQUEST", request);
                        } else {
                            documentString = ECHO_RESPONSE;
                            checkRequest("ECHO_REQUEST", request);
                        }
                        // just set the documentString into to the message body
                        exchange.getOut().setBody(documentString); 
                    }
                });
            }
        };
    }

}
