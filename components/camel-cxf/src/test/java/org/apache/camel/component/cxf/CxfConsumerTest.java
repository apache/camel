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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.BusFactory;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;


public class CxfConsumerTest extends ContextTestSupport {
    protected static final String SIMPLE_ENDPOINT_ADDRESS = "http://localhost:28080/test";
    protected static final String SIMPLE_ENDPOINT_URI = "cxf://" + SIMPLE_ENDPOINT_ADDRESS
        + "?serviceClass=org.apache.camel.component.cxf.HelloService";
    private static final transient Log LOG = LogFactory.getLog(CxfProducerRouterTest.class);

    private static final String ECHO_OPERATION = "echo";
    private static final String ECHO_BOOLEAN_OPERATION = "echoBoolean";
    private static final String TEST_MESSAGE = "Hello World!";


    // START SNIPPET: example
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(SIMPLE_ENDPOINT_URI).choice().when(header(CxfConstants.OPERATION_NAME).isEqualTo(ECHO_OPERATION)).process(new Processor() {
                    public void process(final Exchange exchange) {
                        Message in = exchange.getIn();
                        // Get the parameter list
                        List parameter = in.getBody(List.class);
                        // Get the operation name
                        String operation = (String)in.getHeader(CxfConstants.OPERATION_NAME);
                        Object result = operation + " " + (String)parameter.get(0);
                        // Put the result back
                        exchange.getOut().setBody(result);
                    }
                })
                .when(header(CxfConstants.OPERATION_NAME).isEqualTo(ECHO_BOOLEAN_OPERATION)).process(new Processor() {
                    public void process(final Exchange exchange) {
                        Message in = exchange.getIn();
                        // Get the parameter list
                        List parameter = in.getBody(List.class);
                        // Put the result back
                        exchange.getOut().setBody((Boolean)parameter.get(0));
                    }
                });

            }
        };
    }
    // END SNIPPET: example

    public void testInvokingServiceFromCXFClient() throws Exception {
        ClientProxyFactoryBean proxyFactory = new ClientProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(SIMPLE_ENDPOINT_ADDRESS);
        clientBean.setServiceClass(HelloService.class);
        clientBean.setBus(BusFactory.getDefaultBus());

        HelloService client = (HelloService) proxyFactory.create();

        String result = client.echo(TEST_MESSAGE);
        assertEquals("We should get the echo string result from router", result, "echo " + TEST_MESSAGE);

        Boolean bool = client.echoBoolean(Boolean.TRUE);
        assertNotNull("The result should not be null", bool);
        assertEquals("We should get the echo boolean result from router ", bool.toString(), "true");

    }



}
