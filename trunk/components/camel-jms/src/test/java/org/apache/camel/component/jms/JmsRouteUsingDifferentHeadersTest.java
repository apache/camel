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
package org.apache.camel.component.jms;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version 
 */
public class JmsRouteUsingDifferentHeadersTest extends CamelTestSupport {

    @Test
    public void testUsingDifferentHeaderTypes() throws Exception {
        Map<String, Object> headers = new LinkedHashMap<String, Object>();
        headers.put("a", new Byte("65"));
        headers.put("b", Boolean.TRUE);
        headers.put("c", new Double("44444"));
        headers.put("d", new Float("55555"));
        headers.put("e", new Integer("222"));
        headers.put("f", new Long("7777777"));
        headers.put("g", new Short("333"));
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

        template.sendBodyAndHeaders("activemq:queue:foo", "Hello World", headers);

        assertMockEndpointsSatisfied();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:queue:foo").to("mock:result");
            }
        };
    }
}
