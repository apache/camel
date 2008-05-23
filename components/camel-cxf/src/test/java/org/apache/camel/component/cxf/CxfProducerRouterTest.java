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
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultExchange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.bus.CXFBusFactory;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.ServerImpl;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.helpers.CastUtils;

public class CxfProducerRouterTest extends ContextTestSupport {
    private static final transient Log LOG = LogFactory.getLog(CxfProducerRouterTest.class);
    private static final  String SIMPLE_SERVER_ADDRESS = "http://localhost:28080/test";

    private static final String ECHO_OPERATION = "echo";
    private static final String TEST_MESSAGE = "Hello World!";
    private ServerImpl simpleServer;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // start a simple front service
        ServerFactoryBean svrBean = new ServerFactoryBean();
        svrBean.setAddress(SIMPLE_SERVER_ADDRESS);
        svrBean.setServiceClass(HelloService.class);
        svrBean.setServiceBean(new HelloServiceImpl());
        svrBean.setBus(CXFBusFactory.getDefaultBus());

        simpleServer = (ServerImpl)svrBean.create();
        simpleServer.start();


    }

    @Override
    protected void tearDown() throws Exception {
        if (simpleServer != null) {
            simpleServer.stop();
        }
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:EndpointA").to(getSimpleEndpointUri());
            }
        };
    }


    public void testInvokingSimpleServerWithParams() throws Exception {
        Exchange senderExchange = new DefaultExchange(context, ExchangePattern.InOut);
        final List<String> params = new ArrayList<String>();
        params.add(TEST_MESSAGE);
        senderExchange.getIn().setBody(params);
        senderExchange.getIn().setHeader(CxfConstants.OPERATION_NAME, ECHO_OPERATION);

        Exchange exchange = template.send("direct:EndpointA", senderExchange);

        org.apache.camel.Message out = exchange.getOut();
        Object[] output = (Object[])out.getBody();
        LOG.info("Received output text: " + output[0]);
        Map<String, Object> responseContext = CastUtils.cast((Map)out.getHeader(Client.RESPONSE_CONTEXT));
        assertNotNull(responseContext);
        assertEquals("We should get the response context here", "UTF-8", responseContext.get(org.apache.cxf.message.Message.ENCODING));
        assertEquals("Reply body on Camel is wrong", "echo " + TEST_MESSAGE, output[0]);
    }


    private String getSimpleEndpointUri() {
        return "cxf://" + SIMPLE_SERVER_ADDRESS
            + "?serviceClass=org.apache.camel.component.cxf.HelloService";
    }

}
