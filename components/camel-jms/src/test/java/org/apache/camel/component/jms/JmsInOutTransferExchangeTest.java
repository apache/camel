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

import java.util.HashMap;
import java.util.Map;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsInOutTransferExchangeTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:transfer")
    protected MockEndpoint transfer;

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint result;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
    }

    @Test
    public void testTransferExchangeInOut() throws Exception {
        transfer.expectedMessageCount(1);
        result.expectedMessageCount(1);

        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(new SerializableRequestDto("Restless Camel"));
                
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("boolean", Boolean.TRUE);
                map.put("string", "hello");
                map.put("long", new Long(123));
                map.put("double", new Double(1.23));

                exchange.getIn().setHeaders(map);
            }
        });

        assertMockEndpointsSatisfied();

        Exchange transferExchange = transfer.getExchanges().get(0);
        Message transferMessage = transferExchange.getIn();
        assertNotNull(transferMessage.getBody(SerializableRequestDto.class));
        assertEquals(Boolean.TRUE, transferMessage.getHeader("boolean", Boolean.class));
        assertEquals((Long) 123L, transferMessage.getHeader("long", Long.class));
        assertEquals((Double) 1.23, transferMessage.getHeader("double", Double.class));
        assertEquals("hello", transferMessage.getHeader("string", String.class));

        Message resultMessage = result.getExchanges().get(0).getIn();
        assertNotNull(resultMessage.getBody(SerializableResponseDto.class));
        assertEquals(Boolean.TRUE, resultMessage.getHeader("boolean", Boolean.class));
        assertEquals((Long) 123L, resultMessage.getHeader("long", Long.class));
        assertEquals((Double) 1.23, resultMessage.getHeader("double", Double.class));
        assertEquals("hello", resultMessage.getHeader("string", String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .inOut("activemq:responseGenerator?transferExchange=true")
                    .to("mock:result");

                from("activemq:responseGenerator?transferExchange=true")
                    .to("mock:transfer")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            exchange.getIn().setBody(new SerializableResponseDto(true));
                        }
                    });
            }
        };
    }
   
   
    
}
