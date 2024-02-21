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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Date;

import jakarta.jms.JMSException;

import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class JmsBindingTest {

    private final Instant instant = Instant.ofEpochMilli(1519672338000L);

    @Mock
    private JmsConfiguration mockJmsConfiguration;
    @Mock
    private JmsEndpoint mockJmsEndpoint;

    private JmsBinding jmsBindingUnderTest;

    @BeforeEach
    public void setup() {
        lenient().when(mockJmsConfiguration.isFormatDateHeadersToIso8601()).thenReturn(false);
        lenient().when(mockJmsConfiguration.isMapJmsMessage()).thenReturn(true);
        lenient().when(mockJmsEndpoint.getConfiguration()).thenReturn(mockJmsConfiguration);
        jmsBindingUnderTest = new JmsBinding(mockJmsEndpoint);
    }

    @Test
    public void noEndpointTest() throws Exception {
        JmsBinding testBindingWithoutEndpoint = new JmsBinding();

        ActiveMQTextMessage message = mock(ActiveMQTextMessage.class);
        message.setText("test");
        DefaultCamelContext camelContext = new DefaultCamelContext();
        Exchange exchange = camelContext.getEndpoint("jms:queue:foo").createExchange();
        exchange.getIn().setBody("test");
        exchange.getIn().setHeader("JMSCorrelationID", null);
        assertDoesNotThrow(() -> testBindingWithoutEndpoint.appendJmsProperties(message, exchange));
    }

    @Test
    public void testExtractNullBodyFromJmsShouldReturnNull() throws JMSException {
        ActiveMQTextMessage message = mock(ActiveMQTextMessage.class);

        assertNull(jmsBindingUnderTest.extractBodyFromJms(null, message));
    }

    @Test
    public void testGetValidJmsHeaderValueWithBigIntegerShouldSucceed() {
        Object value = jmsBindingUnderTest.getValidJMSHeaderValue("foo", new BigInteger("12345"));
        assertEquals("12345", value);
    }

    @Test
    public void testGetValidJmsHeaderValueWithBigDecimalShouldSucceed() {
        Object value = jmsBindingUnderTest.getValidJMSHeaderValue("foo", new BigDecimal("123.45"));
        assertEquals("123.45", value);
    }

    @Test
    public void testGetValidJmsHeaderValueWithDateShouldSucceed() {
        Object value = jmsBindingUnderTest.getValidJMSHeaderValue("foo", Date.from(instant));
        assertNotNull(value);
        // We can't assert further as the returned value is bound to the machine time zone and locale
    }

    @Test
    public void testGetValidJmsHeaderValueWithIso8601DateShouldSucceed() {
        when(mockJmsConfiguration.isFormatDateHeadersToIso8601()).thenReturn(true);
        Object value = jmsBindingUnderTest.getValidJMSHeaderValue("foo", Date.from(instant));
        assertEquals("2018-02-26T19:12:18Z", value);
    }
}
