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
package org.apache.camel.component.cxf.jaxrs;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfRsProducerSessionTest extends CamelSpringTestSupport {
    private static int port1 = CXFTestSupport.getPort1();
    private static int port2 = CXFTestSupport.getPort("CxfRsProducerSessionTest.jetty");

    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    public int getPort1() {
        return port1;
    }

    public int getPort2() {
        return port2;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/jaxrs/CxfRsSpringProducerSession.xml");
    }

    protected void setupDestinationURL(Message inMessage) {
        // do nothing here
    }

    @Test
    public void testNoSessionProxy() {
        String response = sendMessage("direct://proxy", "World", Boolean.FALSE).getOut().getBody(String.class);
        assertEquals("New New World", response);
        response = sendMessage("direct://proxy", "World", Boolean.FALSE).getOut().getBody(String.class);
        assertEquals("New New World", response);
    }

    @Test
    public void testExchangeSessionProxy() {
        String response = sendMessage("direct://proxyexchange", "World", Boolean.FALSE).getOut().getBody(String.class);
        assertEquals("Old New World", response);
        response = sendMessage("direct://proxyexchange", "World", Boolean.FALSE).getOut().getBody(String.class);
        assertEquals("Old New World", response);
    }

    @Test
    public void testInstanceSession() {
        String response = sendMessage("direct://proxyinstance", "World", Boolean.FALSE).getOut().getBody(String.class);
        assertEquals("Old New World", response);
        response = sendMessage("direct://proxyinstance", "World", Boolean.FALSE).getOut().getBody(String.class);
        assertEquals("Old Old World", response);
        // we do the instance tests for proxy and http in one test because order
        // matters here
        response = sendMessage("direct://httpinstance", "World", Boolean.TRUE).getOut().getBody(String.class);
        assertEquals("Old Old World", response);
    }

    @Test
    public void testNoSessionHttp() {
        String response = sendMessage("direct://http", "World", Boolean.TRUE).getOut().getBody(String.class);
        assertEquals("New New World", response);
        response = sendMessage("direct://http", "World", Boolean.TRUE).getOut().getBody(String.class);
        assertEquals("New New World", response);
    }

    @Test
    public void testExchangeSessionHttp() {
        String response = sendMessage("direct://httpexchange", "World", Boolean.TRUE).getOut().getBody(String.class);
        assertEquals("Old New World", response);
        response = sendMessage("direct://httpexchange", "World", Boolean.TRUE).getOut().getBody(String.class);
        assertEquals("Old New World", response);
    }

    private Exchange sendMessage(String endpoint, String body, Boolean httpApi) {
        Exchange exchange = template.send(endpoint, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                Message inMessage = exchange.getIn();
                inMessage.setHeader(CxfConstants.OPERATION_NAME, "echo");
                inMessage.setHeader(Exchange.HTTP_METHOD, "POST");
                inMessage.setHeader(Exchange.HTTP_PATH, "/echoservice/echo");
                inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_USING_HTTP_API, httpApi);
                inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_CLASS, String.class);
                inMessage.setHeader(Exchange.ACCEPT_CONTENT_TYPE, "application/json");
                inMessage.setBody(body);
            }
        });
        return exchange;
    }
}
