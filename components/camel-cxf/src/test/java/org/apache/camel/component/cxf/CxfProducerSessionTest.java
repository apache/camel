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

import java.util.Collections;
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.cookie.ExchangeCookieHandler;
import org.apache.camel.http.common.cookie.InstanceCookieHandler;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.jaxws.support.JaxWsServiceFactoryBean;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngineFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class CxfProducerSessionTest extends CamelTestSupport {
    private static final int PORT = CXFTestSupport.getPort1();
    private static final String SIMPLE_SERVER_ADDRESS = "http://127.0.0.1:" + PORT + "/CxfProducerSessionTest/test";
    private static final String REQUEST_MESSAGE_EXPRESSION = "<ns1:echo xmlns:ns1=\"http://cxf.component.camel.apache.org/\"><arg0>${in.body}</arg0></ns1:echo>";
    private static final Map<String, String> NAMESPACES = Collections.singletonMap("ns1", "http://cxf.component.camel.apache.org/");
    private static final String PARAMETER_XPATH = "/ns1:echoResponse/return/text()";

    private String url = "cxf://" + SIMPLE_SERVER_ADDRESS + "?serviceClass=org.apache.camel.component.cxf.EchoService&dataFormat=PAYLOAD&synchronous=true";

    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @BeforeClass
    public static void startServer() throws Exception {
        // start a simple front service
        JaxWsServiceFactoryBean svrFBean = new JaxWsServiceFactoryBean();
        svrFBean.setServiceClass(EchoService.class);
        JaxWsServerFactoryBean svrBean = new JaxWsServerFactoryBean(svrFBean);
        svrBean.setAddress(SIMPLE_SERVER_ADDRESS);
        svrBean.setServiceClass(EchoService.class);
        svrBean.setServiceBean(new EchoServiceSessionImpl());
        // make the Jetty server support sessions
        Bus bus = BusFactory.newInstance().createBus();
        JettyHTTPServerEngineFactory jettyFactory = bus.getExtension(JettyHTTPServerEngineFactory.class);
        jettyFactory.createJettyHTTPServerEngine(PORT, "http").setSessionSupport(true);
        svrBean.setBus(bus);
        svrBean.create();
    }

    @AfterClass
    public static void destroyServer() {
        // If we don't destroy this the session support will spill over to other
        // tests and they will fail
        JettyHTTPServerEngineFactory.destroyForPort(PORT);
    }

    @Test
    public void testNoSession() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);
        String response = template.requestBody("direct:start", "World", String.class);
        assertEquals("New New World", response);
        response = template.requestBody("direct:start", "World", String.class);
        assertEquals("New New World", response);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExchangeSession() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);
        String response = template.requestBody("direct:exchange", "World", String.class);
        assertEquals("Old New World", response);
        response = template.requestBody("direct:exchange", "World", String.class);
        assertEquals("Old New World", response);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInstanceSession() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);
        String response = template.requestBody("direct:instance", "World", String.class);
        assertEquals("Old New World", response);
        response = template.requestBody("direct:instance", "World", String.class);
        assertEquals("Old Old World", response);
        assertMockEndpointsSatisfied();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSessionWithInvalidPayload() throws Throwable {
        try {
            template.requestBody("direct:invalid", "World", String.class);
        } catch (CamelExecutionException e) {
            if (e.getCause() != null) {
                throw e.getCause();
            }
            throw e;
        }
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .setBody().simple(REQUEST_MESSAGE_EXPRESSION)
                    .to(url)
                    .setBody().xpath(PARAMETER_XPATH, String.class, NAMESPACES)
                    .setBody().simple(REQUEST_MESSAGE_EXPRESSION)
                    .to(url)
                    .setBody().xpath(PARAMETER_XPATH, String.class, NAMESPACES)
                    .to("mock:result");
                from("direct:instance")
                    .setBody().simple(REQUEST_MESSAGE_EXPRESSION)
                    .to(url + "&cookieHandler=#instanceCookieHandler")
                    .setBody().xpath(PARAMETER_XPATH, String.class, NAMESPACES)
                    .setBody().simple(REQUEST_MESSAGE_EXPRESSION)
                    .to(url + "&cookieHandler=#instanceCookieHandler")
                    .setBody().xpath(PARAMETER_XPATH, String.class, NAMESPACES)
                    .to("mock:result");
                from("direct:exchange")
                    .setBody().simple(REQUEST_MESSAGE_EXPRESSION)
                    .to(url + "&cookieHandler=#exchangeCookieHandler")
                    .setBody().xpath(PARAMETER_XPATH, String.class, NAMESPACES)
                    .setBody().simple(REQUEST_MESSAGE_EXPRESSION)
                    .to(url + "&cookieHandler=#exchangeCookieHandler")
                    .setBody().xpath(PARAMETER_XPATH, String.class, NAMESPACES)
                    .to("mock:result");
                from("direct:invalid")
                    .to(url + "&cookieHandler=#exchangeCookieHandler");
            }
        };
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndiRegistry = super.createRegistry();
        jndiRegistry.bind("instanceCookieHandler", new InstanceCookieHandler());
        jndiRegistry.bind("exchangeCookieHandler", new ExchangeCookieHandler());
        return jndiRegistry;
    }
}
