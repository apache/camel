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

import javax.xml.namespace.QName;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.pizza.types.CallerIDHeaderType;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.headers.Header;
import org.apache.cxf.jaxb.JAXBDataBinding;
import org.junit.Test;

public class CxfPayLoadSoapHeaderViaCamelHeaderTest extends CxfPayLoadSoapHeaderTestAbstract {

    protected RouteBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: payload_soap_header_set
                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        CallerIDHeaderType callerId = new CallerIDHeaderType();
                        callerId.setName("Willem");
                        callerId.setPhoneNumber("108");
                        SoapHeader soapHeader = new SoapHeader(new QName("http://camel.apache.org/pizza/types", "CallerIDHeader"),
                                callerId, new JAXBDataBinding(CallerIDHeaderType.class));
                        List<SoapHeader> soapHeaders = new ArrayList<SoapHeader>(1);
                        soapHeaders.add(soapHeader);
                        // sets the SOAP header via a camel header
                        exchange.getIn().setHeader(Header.HEADER_LIST, soapHeaders);
                    }

                }).to(getServiceEndpointURI()) //
                  .to("mock:end");
                // END SNIPPET: payload_soap_header_set
            }
        };
    }

    @Test
    public void testCreateSoapHeaderViaCamelHeaderForSoapRequest() throws Exception {
        String body = "<OrderRequest xmlns=\"http://camel.apache.org/pizza/types\"><Toppings><Topping>topping_value</Topping></Toppings></OrderRequest>";
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedMessageCount(1);
        sendBody("direct:start", body);
        assertMockEndpointsSatisfied();
        Document message = mock.getExchanges().get(0).getIn().getMandatoryBody(Document.class);
        Element root = message.getDocumentElement();
        NodeList nodeList = root.getElementsByTagName("MinutesUntilReady");
        assertEquals(1, nodeList.getLength());
        Element elMinutesUntilReady = (Element) nodeList.item(0);
        /**
         * the phone number 108 which is given in the SOAP header is added to
         * 100 which results in 208, see class
         * org.apache.camel.component.cxf.PizzaImpl.
         */
        assertEquals("208", elMinutesUntilReady.getTextContent());
    }

}
