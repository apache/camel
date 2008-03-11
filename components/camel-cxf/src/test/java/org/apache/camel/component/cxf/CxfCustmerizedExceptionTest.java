/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.cxf;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.ServerImpl;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;

public class CxfCustmerizedExceptionTest extends ContextTestSupport {
    protected static final String ROUTER_ADDRESS = "http://localhost:9002/router";
    protected static final String SERVICE_CLASS = "serviceClass=org.apache.camel.component.cxf.HelloService";
    protected static String ROUTER_ENDPOINT_URI = "cxf://" + ROUTER_ADDRESS + "?" + SERVICE_CLASS;

    private static final String EXCEPTION_MESSAGE = "This is an exception test message";

    private Bus bus;


    @Override
    protected void setUp() throws Exception {
        BusFactory.setDefaultBus(null);
        bus = BusFactory.getDefaultBus();
        super.setUp();

    }

    @Override
    protected void tearDown() throws Exception {
        //TODO need to shutdown the server
        super.tearDown();
        //bus.shutdown(true);
        BusFactory.setDefaultBus(null);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(ROUTER_ENDPOINT_URI).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Message message = exchange.getFault();
                        message.setBody(new CamelException(EXCEPTION_MESSAGE));
                    }

                });
            }
        };
    }

    protected CamelContext createCamelContext() throws Exception {
        return new DefaultCamelContext();
    }


    public void testInvokingServiceFromCXFClient() throws Exception {
        ClientProxyFactoryBean proxyFactory = new ClientProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(ROUTER_ADDRESS);
        clientBean.setServiceClass(HelloService.class);
        clientBean.setBus(bus);

        HelloService client = (HelloService) proxyFactory.create();

        try {
            client.echo("hello world");
            fail("Except to get an exception here");
        } catch (Exception e) {
            assertEquals("Except to get right exception message", e.getMessage(), EXCEPTION_MESSAGE);
        }

    }


}
