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
package org.apache.camel.converter;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.Currency;
import java.util.Locale;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InstanceFallbackConverterTest extends ContextTestSupport {

    @Override
    protected boolean isLoadTypeConverters() {
        return true;
    }

    @Test
    public void testInstanceFallbackConverter() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Currency cur = Currency.getInstance(Locale.US);

        String money = context.getTypeConverter().convertTo(String.class, exchange, cur);
        assertEquals("Money talks says " + context.getName(), money);
    }

    @Test
    public void testInstanceFallbackMandatoryConverter() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Currency cur = Currency.getInstance(Locale.US);

        String money = context.getTypeConverter().mandatoryConvertTo(String.class, exchange, cur);
        assertEquals("Money talks says " + context.getName(), money);
    }

    @Test
    public void testInstanceFallbackMandatoryFailed() {
        Exchange exchange = new DefaultExchange(context);

        assertThrows(NoTypeConversionAvailableException.class,
                () -> context.getTypeConverter().mandatoryConvertTo(Date.class, exchange, new Timestamp(0)),
                "Should have thrown an exception");
    }

    @Test
    public void testInstanceFallbackFailed() throws Exception {
        Exchange exchange = new DefaultExchange(context);

        Date out = context.getTypeConverter().convertTo(Date.class, exchange, new Timestamp(0));
        assertNull(out);
    }

}
