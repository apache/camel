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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.cxf.binding.soap.SoapFault;

public class CxfMessageStreamExceptionTest extends CxfMessageCustomizedExceptionTest {

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: onException
                from("direct:start").onException(SoapFault.class).maximumRedeliveries(0).handled(true)
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            SoapFault fault = exchange
                                .getProperty(Exchange.EXCEPTION_CAUGHT, SoapFault.class);
                            exchange.getOut().setFault(true);
                            exchange.getOut().setBody(fault);
                        }

                    }).end().to(serviceURI);
                // END SNIPPET: onException
                // START SNIPPET: MessageStreamFault
                from(routerEndpointURI).process(new Processor() {

                    public void process(Exchange exchange) throws Exception {
                        Message out = exchange.getOut();
                        // Set the message body with the 
                        out.setBody(this.getClass().getResourceAsStream("SoapFaultMessage.xml"));
                        // Set the response code here
                        out.setHeader(org.apache.cxf.message.Message.RESPONSE_CODE, new Integer(500));
                    }

                });
             // END SNIPPET: MessageStreamFault
            }
        };
    }
}
