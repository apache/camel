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

/**
 * A unit test for testing reading SOAP body with address override in PAYLOAD mode.
 */
public class CxfPayLoadMessageRouterAddressOverrideTest extends CxfPayLoadMessageRouterTest {

    private String routerEndpointURI = "cxf://" + getRouterAddress() + "?" + SERVICE_CLASS + "&dataFormat=PAYLOAD";
    private String serviceEndpointURI = "cxf://http://localhost:9002/badAddress" + "?" + SERVICE_CLASS + "&dataFormat=PAYLOAD";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(routerEndpointURI).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        
                        exchange.getIn().setHeader(Exchange.DESTINATION_OVERRIDE_URL, getServiceAddress());
                        
                        CxfPayload<?> payload = exchange.getIn().getBody(CxfPayload.class);
                        List<Source> elements = payload.getBodySources();
                        assertNotNull("We should get the elements here", elements);
                        assertEquals("Get the wrong elements size", elements.size(), 1);
                        Element el = new XmlConverter().toDOMElement(elements.get(0));
                        assertEquals("Get the wrong namespace URI", el.getNamespaceURI(), "http://cxf.component.camel.apache.org/");
                    }
                })
                .to(serviceEndpointURI);
            }
        };
    }

}
