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

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.TimeZone;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

public class StaticFallbackConverterTest extends ContextTestSupport {

    @Test
    public void testStaticFallbackConverter() {
        Exchange exchange = new DefaultExchange(context);
        TimeZone tz = TimeZone.getDefault();

        String money = context.getTypeConverter().convertTo(String.class, exchange, tz);
        assertEquals("Time talks", money);
    }

    @Test
    public void testStaticFallbackMandatoryConverter() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        TimeZone tz = TimeZone.getDefault();

        String money = context.getTypeConverter().mandatoryConvertTo(String.class, exchange, tz);
        assertEquals("Time talks", money);
    }

    @Test
    public void testStaticFallbackMandatoryFailed() {
        Exchange exchange = new DefaultExchange(context);

        assertThrows(
                NoTypeConversionAvailableException.class,
                () -> context.getTypeConverter().mandatoryConvertTo(Date.class, exchange, new Timestamp(0)),
                "Should have thrown an exception");
    }

    @Test
    public void testStaticFallbackFailed() {
        Exchange exchange = new DefaultExchange(context);

        Date out = context.getTypeConverter().convertTo(Date.class, exchange, new Timestamp(0));
        assertNull(out);
    }
}
