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
package org.apache.camel.component.smpp.integration;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.smpp.SmppConstants;
import org.apache.camel.component.smpp.SmppMessageType;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Spring based integration test for the smpp component. To run this test, ensure that the SMSC is running on: host:
 * localhost port: 2775 user: smppclient password: password <br/>
 * A SMSC for test is available here: http://www.seleniumsoftware.com/downloads.html
 */
@Disabled("Must be manually tested")
public class SmppComponentSpringManualIT extends CamelSpringTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @EndpointInject("direct:start")
    private Endpoint start;

    @Test
    public void sendSubmitSMInOut() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = start.createExchange(ExchangePattern.InOut);
        exchange.getIn().setBody("Hello SMPP World!");

        template.send(start, exchange);

        MockEndpoint.assertIsSatisfied(context);
        Exchange resultExchange = result.getExchanges().get(0);
        assertEquals(SmppMessageType.DeliveryReceipt.toString(), resultExchange.getIn().getHeader(SmppConstants.MESSAGE_TYPE));
        assertEquals("Hello SMPP World!", resultExchange.getIn().getBody());
        assertNotNull(resultExchange.getIn().getHeader(SmppConstants.ID));
        assertEquals(1, resultExchange.getIn().getHeader(SmppConstants.SUBMITTED));
        assertEquals(1, resultExchange.getIn().getHeader(SmppConstants.DELIVERED));
        assertNotNull(resultExchange.getIn().getHeader(SmppConstants.DONE_DATE));
        assertNotNull(resultExchange.getIn().getHeader(SmppConstants.SUBMIT_DATE));
        assertNull(resultExchange.getIn().getHeader(SmppConstants.ERROR));

        assertNotNull(exchange.getMessage().getHeader(SmppConstants.ID));
        assertEquals(1, exchange.getMessage().getHeader(SmppConstants.SENT_MESSAGE_COUNT));
    }

    @Test
    public void sendSubmitSMInOnly() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = start.createExchange(ExchangePattern.InOnly);
        exchange.getIn().setBody("Hello SMPP World!");

        template.send(start, exchange);

        MockEndpoint.assertIsSatisfied(context);
        Exchange resultExchange = result.getExchanges().get(0);
        assertEquals(SmppMessageType.DeliveryReceipt.toString(), resultExchange.getIn().getHeader(SmppConstants.MESSAGE_TYPE));
        assertEquals("Hello SMPP World!", resultExchange.getIn().getBody());
        assertNotNull(resultExchange.getIn().getHeader(SmppConstants.ID));
        assertEquals(1, resultExchange.getIn().getHeader(SmppConstants.SUBMITTED));
        assertEquals(1, resultExchange.getIn().getHeader(SmppConstants.DELIVERED));
        assertNotNull(resultExchange.getIn().getHeader(SmppConstants.DONE_DATE));
        assertNotNull(resultExchange.getIn().getHeader(SmppConstants.SUBMIT_DATE));
        assertNull(resultExchange.getIn().getHeader(SmppConstants.ERROR));

        assertNotNull(exchange.getIn().getHeader(SmppConstants.ID));
        assertEquals(1, exchange.getIn().getHeader(SmppConstants.SENT_MESSAGE_COUNT));
    }

    @Test
    public void sendLongSubmitSM() throws Exception {
        result.expectedMessageCount(2);

        Exchange exchange = start.createExchange(ExchangePattern.InOnly);
        exchange.getIn().setBody("Hello SMPP World! Hello SMPP World! Hello SMPP World! Hello SMPP World! Hello SMPP World! "
                                 + "Hello SMPP World! Hello SMPP World! Hello SMPP World! Hello SMPP World! Hello SMPP World! "
                                 + "Hello SMPP World! Hello SMPP World! Hello SMPP World! Hello SMPP World! Hello SMPP World! "); // 270 chars

        template.send(start, exchange);

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(SmppMessageType.DeliveryReceipt.toString(),
                result.getExchanges().get(0).getIn().getHeader(SmppConstants.MESSAGE_TYPE));
        assertEquals(SmppMessageType.DeliveryReceipt.toString(),
                result.getExchanges().get(1).getIn().getHeader(SmppConstants.MESSAGE_TYPE));

        assertNotNull(exchange.getIn().getHeader(SmppConstants.ID));
        assertEquals(2, exchange.getIn().getHeader(SmppConstants.SENT_MESSAGE_COUNT));
    }

    @Test
    public void sendCancelSM() {
        Exchange exchange = start.createExchange(ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "CancelSm");
        exchange.getIn().setHeader(SmppConstants.ID, "1");

        template.send(start, exchange);

        assertEquals("1", exchange.getMessage().getHeader(SmppConstants.ID));
    }

    @Test
    public void sendQuerySM() {
        Exchange exchange = start.createExchange(ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "QuerySm");
        exchange.getIn().setHeader(SmppConstants.ID, "1");

        template.send(start, exchange);

        assertEquals("1", exchange.getMessage().getHeader(SmppConstants.ID));
        assertEquals((byte) 0, exchange.getMessage().getHeader(SmppConstants.ERROR));
        assertNotNull(exchange.getMessage().getHeader(SmppConstants.FINAL_DATE));
        assertEquals("DELIVERED", exchange.getMessage().getHeader(SmppConstants.MESSAGE_STATE));
    }

    @Test
    public void sendReplaceSM() {
        Exchange exchange = start.createExchange(ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "ReplaceSm");
        exchange.getIn().setBody("Hello Camel World!");
        exchange.getIn().setHeader(SmppConstants.ID, "1");

        template.send(start, exchange);

        assertEquals("1", exchange.getMessage().getHeader(SmppConstants.ID));
    }

    @Test
    public void sendDataSM() {
        Exchange exchange = start.createExchange(ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "DataSm");

        template.send(start, exchange);

        assertNotNull(exchange.getMessage().getHeader(SmppConstants.ID));
    }

    @Test
    public void sendSubmitMultiSM() {
        Exchange exchange = start.createExchange(ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "SubmitMulti");
        exchange.getIn().setBody("Hello SMPP World! Hello SMPP World! Hello SMPP World! Hello SMPP World! Hello SMPP World! "
                                 + "Hello SMPP World! Hello SMPP World! Hello SMPP World! Hello SMPP World! Hello SMPP World! "
                                 + "Hello SMPP World! Hello SMPP World! Hello SMPP World! Hello SMPP World! Hello SMPP World! "); // 270 chars

        template.send(start, exchange);

        assertNotNull(exchange.getMessage().getHeader(SmppConstants.ID));
        assertEquals(2, exchange.getMessage().getHeader(SmppConstants.SENT_MESSAGE_COUNT));
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/component/smpp/integration/SmppComponentSpringIntegrationTest-context.xml");
    }
}
