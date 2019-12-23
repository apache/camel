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

import org.apache.activemq.command.ActiveMQBlobMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JmsBindingTest {

    private final Instant instant = Instant.ofEpochMilli(1519672338000L);

    @Mock
    private JmsConfiguration mockJmsConfiguration;
    @Mock
    private JmsEndpoint mockJmsEndpoint;

    private JmsBinding jmsBindingUnderTest;

    @Before
    public void setup() {
        when(mockJmsConfiguration.isFormatDateHeadersToIso8601()).thenReturn(false);
        when(mockJmsConfiguration.isMapJmsMessage()).thenReturn(true);
        when(mockJmsEndpoint.getConfiguration()).thenReturn(mockJmsConfiguration);
        jmsBindingUnderTest = new JmsBinding(mockJmsEndpoint);
    }

    @Test
    public void noEndpointTest() throws Exception {
        JmsBinding testBindingWithoutEndpoint = new JmsBinding();
        ActiveMQTextMessage message = new ActiveMQTextMessage();
        message.setText("test");
        DefaultCamelContext camelContext = new DefaultCamelContext();
        Exchange exchange = camelContext.getEndpoint("jms:queue:foo").createExchange();
        exchange.getIn().setBody("test");
        exchange.getIn().setHeader("JMSCorrelationID", null);
        testBindingWithoutEndpoint.appendJmsProperties(message, exchange);
    }

    @Test
    public void testExtractNullBodyFromJmsShouldReturnNull() throws Exception {
        assertNull(jmsBindingUnderTest.extractBodyFromJms(null, new ActiveMQBlobMessage()));
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
