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
package org.apache.camel.component.jms;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper.createConnectionFactory;

public class JmsRouteUsingDifferentHeadersTest extends AbstractJMSTest {

    @Test
    public void testUsingDifferentHeaderTypes() throws Exception {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("a", Byte.valueOf("65"));
        headers.put("b", Boolean.TRUE);
        headers.put("c", Double.valueOf("44444"));
        headers.put("d", Float.valueOf("55555"));
        headers.put("e", Integer.valueOf("222"));
        headers.put("f", Long.valueOf("7777777"));
        headers.put("g", Short.valueOf("333"));
        headers.put("h", "Hello");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("a").isInstanceOf(Byte.class);
        mock.message(0).header("b").isInstanceOf(Boolean.class);
        mock.message(0).header("c").isInstanceOf(Double.class);
        mock.message(0).header("d").isInstanceOf(Float.class);
        mock.message(0).header("e").isInstanceOf(Integer.class);
        mock.message(0).header("f").isInstanceOf(Long.class);
        mock.message(0).header("g").isInstanceOf(Short.class);
        mock.message(0).header("h").isInstanceOf(String.class);

        template.sendBodyAndHeaders("activemq:queue:JmsRouteUsingDifferentHeadersTest", "Hello World", headers);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory
                = createConnectionFactory(service);
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:JmsRouteUsingDifferentHeadersTest").to("mock:result");
            }
        };
    }
}
