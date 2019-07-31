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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.wsdl_first.UnknownPersonFault;

public class CxfConsumerFaultWithRouteTest extends CxfConsumerPayloadFaultTest {
    @Override
    protected RouteBuilder createRouteBuilder() {
        final String serviceURI = "cxf://" + serviceAddress + "?" 
            + PORT_NAME_PROP + "&" + SERVICE_NAME_PROP + "&" + WSDL_URL_PROP
            + "&serviceClass=org.apache.camel.wsdl_first.Person";

        return new RouteBuilder() {
            public void configure() {
                from(serviceURI).process(new Processor() {
                    public void process(final Exchange exchange) throws Exception {
                        // set the fault message here
                        org.apache.camel.wsdl_first.types.UnknownPersonFault faultDetail = new org.apache.camel.wsdl_first.types.UnknownPersonFault();
                        faultDetail.setPersonId("");
                        UnknownPersonFault fault = new UnknownPersonFault("Get the null value of person name", faultDetail);
                        throw fault;
                    }
                }).to("log:myfaultlog");
                
            }
        };
    }

}
