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
package org.apache.camel.support.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DefaultExchangeFormatterTest {
    private DefaultCamelContext camelContext;
    private Exchange exchange;
    private DefaultExchangeFormatter exchangeFormatter;

    @Before
    public void setUp() {
        camelContext = new DefaultCamelContext();
        Message message = new DefaultMessage(camelContext);
        message.setBody("This is the message body");
        exchange = new DefaultExchange(camelContext);
        exchange.setIn(message);
        exchangeFormatter = new DefaultExchangeFormatter();
    }

    @Test
    public void testDefaultFormat() {
        String formattedExchange = exchangeFormatter.format(exchange);
        assertTrue(formattedExchange.contains("This is the message body"));
    }

    @Test
    /*
     * The formatted exchange without limitation is Exchange[ExchangePattern:
     * InOnly, BodyType: String, Body: This is the message body] The
     * "Exchange[", the "...", and the "]" do not count here, but the leading
     * ", " that is removed later does count...
     */
    public void testFormatWithMaxCharsParameter() {
        exchangeFormatter.setMaxChars(60);
        String formattedExchange = exchangeFormatter.format(exchange);
        assertEquals(60 + "Exchange[...]".length() - ", ".length(), formattedExchange.length());
    }

    @Test
    /*
     * This limitation is really the length of the printed message body, not the
     * one of the message
     */
    public void testFormatWithBodyMaxChars() {
        camelContext.getGlobalOptions().put(Exchange.LOG_DEBUG_BODY_MAX_CHARS, "7");
        String formattedExchange = exchangeFormatter.format(exchange);
        assertFalse(formattedExchange.contains("This is "));
        assertTrue(formattedExchange.contains("This is"));
        camelContext.getGlobalOptions().remove(Exchange.LOG_DEBUG_BODY_MAX_CHARS);
    }

    @Test
    /*
     * These two limitations will first truncate the message body and then the
     * total message.
     */
    public void testFormatWithBoth() {
        camelContext.getGlobalOptions().put(Exchange.LOG_DEBUG_BODY_MAX_CHARS, "7");
        exchangeFormatter.setMaxChars(60);
        String formattedExchange = exchangeFormatter.format(exchange);
        assertEquals(60 + "Exchange[...]".length() - ", ".length(), formattedExchange.length());
        assertFalse(formattedExchange.contains("This is "));
        camelContext.getGlobalOptions().remove(Exchange.LOG_DEBUG_BODY_MAX_CHARS);
    }
}
