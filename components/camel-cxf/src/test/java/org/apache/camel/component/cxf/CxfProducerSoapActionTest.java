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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.cxf.binding.soap.SoapBindingConstants;
import org.junit.Test;

public class CxfProducerSoapActionTest extends CamelTestSupport {

    private static int port = AvailablePortFinder.getNextAvailable();
    private static final String SOAP_ACTION = "http://camel.apache.org/order/Order";
    private static final String OPERATION_NAMESPACE = "http://camel.apache.org/order";
    private static final String OPERATION_NAME = "order";
    private static final String DIRECT_START = "direct:start";
    private static final String CXF_ENDPOINT = "cxf:http://localhost:" + port + "/order?wsdlURL=classpath:order.wsdl&loggingFeatureEnabled=true";
    private static final String REQUEST_MESSAGE = "<Envelope xmlns=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                                                  + "<Body/>" + "</Envelope>";

    @Test
    public void testSendSoapRequestWithoutSoapActionSet() {
        template.requestBody(DIRECT_START, REQUEST_MESSAGE, String.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from(DIRECT_START).setHeader(CxfConstants.OPERATION_NAME, constant(OPERATION_NAME))
                    .setHeader(CxfConstants.OPERATION_NAMESPACE, constant(OPERATION_NAMESPACE))
                    .process(new Processor() {

                        @Override
                        public void process(Exchange exchange) throws Exception {
                            final List<Object> params = new ArrayList<>();
                            params.add("foo");
                            params.add(10);
                            params.add("bar");
    
                            exchange.getIn().setBody(params);
    
                        }
                    }).to("log:org.apache.camel?level=DEBUG")
                        .to(CXF_ENDPOINT + "&serviceClass=org.apache.camel.order.OrderEndpoint");

                from(CXF_ENDPOINT + "&dataFormat=POJO&serviceClass=org.apache.camel.order.OrderEndpoint")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            String soapAction = exchange.getIn().getHeader(SoapBindingConstants.SOAP_ACTION,
                                                                           String.class);
                            assertEquals(SOAP_ACTION, soapAction);
    
                        }
                    });
            }

        };
    }

}
