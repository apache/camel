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

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CxfConsumerMessageTest extends CxfConsumerTest {
    private static final transient Log LOG = LogFactory.getLog(CxfConsumerMessageTest.class);
    private static final String ECHO_METHOD = "ns1:echo xmlns:ns1=\"http://cxf.component.camel.apache.org/\"";

    private static final String ECHO_RESPONSE = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soap:Body><ns1:echoResponse xmlns:ns1=\"http://cxf.component.camel.apache.org/\">"
            + "<return xmlns=\"http://cxf.component.camel.apache.org/\">echo Hello World!</return>"
            + "</ns1:echoResponse></soap:Body></soap:Envelope>";
    private static final String ECHO_BOOLEAN_RESPONSE = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soap:Body><ns1:echoBooleanResponse xmlns:ns1=\"http://cxf.component.camel.apache.org/\">"
            + "<return xmlns=\"http://cxf.component.camel.apache.org/\">true</return>"
            + "</ns1:echoBooleanResponse></soap:Body></soap:Envelope>";

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(SIMPLE_ENDPOINT_URI + "&dataFormat=MESSAGE").process(new Processor() {
                    public void process(final Exchange exchange) {
                        Message in = exchange.getIn();
                        // Get the request message
                        String request = in.getBody(String.class);
                        // Send the response message back
                        if (request.indexOf(ECHO_METHOD) > 0) {
                            exchange.getOut().setBody(ECHO_RESPONSE);
                        } else { // echoBoolean call
                            exchange.getOut().setBody(ECHO_BOOLEAN_RESPONSE);
                        }

                    }
                });
            }
        };
    }


}
