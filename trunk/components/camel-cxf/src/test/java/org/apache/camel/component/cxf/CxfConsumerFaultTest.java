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
import org.apache.camel.wsdl_first.UnknownPersonFault;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.helpers.DOMUtils;


// SET the fault message directly on the out message
public class CxfConsumerFaultTest extends CxfConsumerPayloadFaultTest {
    private static final String SERVICE_URI = "cxf://" + SERVICE_ADDRESS + "?" 
        + PORT_NAME_PROP + "&" + SERVICE_NAME_PROP + "&" + WSDL_URL_PROP
        + "&serviceClass=org.apache.camel.wsdl_first.Person";
   
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(SERVICE_URI).process(new Processor() {
                    public void process(final Exchange exchange) throws Exception {
                        // set the fault message here
                        org.apache.camel.wsdl_first.types.UnknownPersonFault faultDetail = new org.apache.camel.wsdl_first.types.UnknownPersonFault();
                        faultDetail.setPersonId("");
                        UnknownPersonFault fault = new UnknownPersonFault("Get the null value of person name", faultDetail);
                        exchange.getOut().setBody(fault);
                        exchange.getOut().setFault(true);
                    }
                });
                
            }
        };
    }
    
}


