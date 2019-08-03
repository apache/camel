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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.greeter_control.Greeter;
import org.apache.cxf.greeter_control.PingMeFault;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.junit.Test;

/**
 * Test for throwing an exception with a JAX-WS WebFault annotation from Camel CXF consumer
 */
public class JaxWsWebFaultAnnotationToFaultTest extends CamelTestSupport {

    protected static final String ROUTER_ADDRESS = "http://localhost:" + CXFTestSupport.getPort1() 
        + "/JaxWsWebFaultAnnotationToFaultTest/router";
    protected static final String SERVICE_CLASS = "serviceClass=org.apache.cxf.greeter_control.Greeter";
    protected static final String SERVICE_URI = "cxf://" + ROUTER_ADDRESS + "?" + SERVICE_CLASS;

    protected static final String MESSAGE = "this is our test message for the exception";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(SERVICE_URI).process(new Processor() {

                    public void process(Exchange exchng) throws Exception {
                        throw new PingMeFault(MESSAGE);
                    }
                });
            }
        };
    }

    @Test
    public void testInvokingServiceFromCXFClient() throws Exception {
        JaxWsProxyFactoryBean proxyFactory = new JaxWsProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(ROUTER_ADDRESS);
        clientBean.setServiceClass(Greeter.class);

        Greeter client = (Greeter) proxyFactory.create();

        try {
            client.pingMe();
            fail("Expect to get an exception here");
        } catch (PingMeFault expected) {
            assertEquals(MESSAGE, expected.getMessage());
        } catch (Throwable t) {
            t.printStackTrace();
            fail("The CXF client did not manage to map the client exception "
                + t.getClass().getName() + " to a " + PingMeFault.class.getName()
                + ": " + t.getMessage());
        }

    }

}