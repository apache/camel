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

import java.io.StringReader;

import javax.xml.bind.JAXBContext;
import javax.xml.namespace.QName;

import org.w3c.dom.Element;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.wsdl_first.types.UnknownPersonFault;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.staxutils.StaxUtils;

public class CxfConsumerPayLoadMarshalFaultTest extends CxfConsumerPayloadFaultTest {
    
    protected static final String DETAILS = "<detail></detail>";
    
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(fromURI).process(new Processor() {
                    public void process(final Exchange exchange) throws Exception {
                        JAXBContext context = JAXBContext.newInstance("org.apache.camel.wsdl_first.types");
                        QName faultCode = new QName("http://schemas.xmlsoap.org/soap/envelope/", "Server");
                        SoapFault fault = new SoapFault("Get the null value of person name", faultCode);
                        Element details = StaxUtils.read(new StringReader(DETAILS)).getDocumentElement();
                        UnknownPersonFault unknowPersonFault = new UnknownPersonFault();
                        unknowPersonFault.setPersonId("");
                        context.createMarshaller().marshal(unknowPersonFault, details);
                        fault.setDetail(details);
                        exchange.getOut().setBody(fault);
                    }
                });
                
            }
        };
    }

}
