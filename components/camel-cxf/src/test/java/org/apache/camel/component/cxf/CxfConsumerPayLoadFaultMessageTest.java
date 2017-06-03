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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.staxutils.StaxUtils;

// SET the fault message directly on the out message
public class CxfConsumerPayLoadFaultMessageTest extends CxfConsumerPayloadFaultTest {

    protected static final String FAULTS = "<soap:Fault xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><faultcode>soap:Server</faultcode>"
        + "<faultstring>Get the null value of person name</faultstring>"
        + "<detail><UnknownPersonFault xmlns=\"http://camel.apache.org/wsdl-first/types\"><personId /></UnknownPersonFault></detail></soap:Fault>";
    
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(fromURI).process(new Processor() {
                    public void process(final Exchange exchange) throws Exception {
                        Element details = StaxUtils.read(new StringReader(FAULTS)).getDocumentElement();
                        List<Element> outElements = new ArrayList<Element>();
                        outElements.add(details);
                        CxfPayload<SoapHeader> responsePayload = new CxfPayload<SoapHeader>(null, outElements);
                        exchange.getOut().setBody(responsePayload);
                        exchange.getOut().setFault(true);
                    }
                });
                
            }
        };
    }
    
}


