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
package org.apache.camel.language.simple.functions;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.apache.camel.language.simple.myconverter.MyCustomDate;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DateFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new DateFunctionFactory();
    }

    // --- date: ---

    @Test
    public void testDateExpressions() {
        Calendar inHeaderCalendar = Calendar.getInstance();
        inHeaderCalendar.set(1974, Calendar.APRIL, 20);
        exchange.getIn().setHeader("birthday", inHeaderCalendar.getTime());

        Calendar propertyCalendar = Calendar.getInstance();
        propertyCalendar.set(1976, Calendar.JUNE, 22);
        exchange.setProperty("birthday", propertyCalendar.getTime());

        assertEquals(inHeaderCalendar.getTime(), evaluate("date:header.birthday", Date.class));
        assertEquals("19740420", evaluate("date:header.birthday:yyyyMMdd", String.class));
        assertEquals("19740421", evaluate("date:header.birthday+24h:yyyyMMdd", String.class));

        // as long
        assertEquals(propertyCalendar.getTime().getTime(), evaluate("date:exchangeProperty.birthday", Long.class));
        // as date
        assertEquals(propertyCalendar.getTime(), evaluate("date:exchangeProperty.birthday", Date.class));
        assertEquals("19760622", evaluate("date:exchangeProperty.birthday:yyyyMMdd", String.class));
        assertEquals("19760623", evaluate("date:exchangeProperty.birthday+24h:yyyyMMdd", String.class));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> evaluate("date:yyyyMMdd", String.class));
        assertEquals("Command not supported for dateExpression: yyyyMMdd", e.getMessage());
    }

    @Test
    public void testDateAndTimeExpressions() {
        Calendar cal = Calendar.getInstance();
        cal.set(1974, Calendar.APRIL, 20, 8, 55, 47);
        cal.set(Calendar.MILLISECOND, 123);
        exchange.getIn().setHeader("birthday", cal.getTime());

        assertEquals("1974-04-20T08:55:37:123", evaluate("date:header.birthday - 10s:yyyy-MM-dd'T'HH:mm:ss:SSS", String.class));
        assertEquals("1974-04-20T08:55:47:123", evaluate("date:header.birthday:yyyy-MM-dd'T'HH:mm:ss:SSS", String.class));
    }

    @Test
    public void testDateWithConverterExpressions() {
        exchange.getIn().setHeader("birthday", new MyCustomDate(1974, Calendar.APRIL, 20));
        exchange.setProperty("birthday", new MyCustomDate(1974, Calendar.APRIL, 20));
        exchange.getIn().setHeader("other", new ArrayList<>());

        assertEquals("19740420", evaluate("date:header.birthday:yyyyMMdd", String.class));
        assertEquals("19740420", evaluate("date:exchangeProperty.birthday:yyyyMMdd", String.class));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> evaluate("date:header.other:yyyyMMdd", String.class));
        assertEquals("Cannot find Date/long object at command: header.other", e.getMessage());
    }

    @Test
    public void testDateWithTimezone() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        cal.set(1974, Calendar.APRIL, 20, 8, 55, 47);
        cal.set(Calendar.MILLISECOND, 123);
        exchange.getIn().setHeader("birthday", cal.getTime());

        assertEquals("1974-04-20T08:55:47:123",
                evaluate("date-with-timezone:header.birthday:GMT+8:yyyy-MM-dd'T'HH:mm:ss:SSS", String.class));
        assertEquals("1974-04-20T00:55:47:123",
                evaluate("date-with-timezone:header.birthday:GMT:yyyy-MM-dd'T'HH:mm:ss:SSS", String.class));
    }

    @Test
    public void testDateNow() {
        Object out = evaluate("date:now", Object.class);
        assertNotNull(out);
        assertIsInstanceOf(Date.class, out);

        assertNotNull(evaluate("date:now:hh:mm:ss a", Object.class));
        assertNotNull(evaluate("date:now:hh:mm:ss", Object.class));
        assertNotNull(evaluate("date:now-2h:hh:mm:ss", Object.class));
    }

    @Test
    public void testDateMillis() {
        Object out = evaluate("date:millis", Object.class);
        assertNotNull(out);
        assertIsInstanceOf(Long.class, out);
    }

    @Test
    public void testDateExchangeCreated() {
        Object out = evaluate("date:exchangeCreated:hh:mm:ss a", String.class);
        assertNotNull(out);
    }

    // --- date-with-timezone: ---

    @Test
    public void testCreateCodeDateMillis() {
        assertEquals("System.currentTimeMillis()", createCode("date:millis"));
    }

    @Test
    public void testCreateCodeDateNoPattern() {
        assertEquals("date(exchange, \"now\")", createCode("date:now"));
    }

    @Test
    public void testCreateCodeDateWithPattern() {
        assertEquals("date(exchange, \"now\", null, \"yyyy-MM-dd\")", createCode("date:now:yyyy-MM-dd"));
    }

    @Test
    public void testCreateCodeDateWithTimezone() {
        assertEquals("date(exchange, \"now\", \"UTC\", \"yyyy-MM-dd\")",
                createCode("date-with-timezone:now:UTC:yyyy-MM-dd"));
    }

    // --- no match ---

    @Test
    public void testNoMatch() {
        assertNull(createFactory().createFunction(context, "body", 0));
    }

    @Test
    public void testNoMatchCode() {
        assertNull(createFactory().createCode(context, "body", 0));
    }
}
