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
package org.apache.camel.processor.aggregate.jdbc;

import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.junit.Test;

public class JdbcExchangeSerializationTest extends AbstractJdbcAggregationTestSupport {

    @Test
    public void testExchangeSerialization() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello World");
        exchange.getIn().setHeader("name", "Olivier");
        exchange.getIn().setHeader("number", 123);
        exchange.setProperty("quote", "Camel rocks");

        Date now = new Date();
        exchange.getIn().setHeader("date", now);

        repo.add(context, "foo", exchange);

        Exchange actual = repo.get(context, "foo");
        assertEquals("Hello World", actual.getIn().getBody());
        assertEquals("Olivier", actual.getIn().getHeader("name"));
        assertEquals(123, actual.getIn().getHeader("number"));
        Date date = actual.getIn().getHeader("date", Date.class);
        assertNotNull(date);
        assertEquals(now.getTime(), date.getTime());
        // we do not serialize properties to avoid storing all kind of not needed information
        assertNull(actual.getProperty("quote"));
        assertSame(context, actual.getContext());

        // change something
        exchange.getIn().setBody("Bye World");
        exchange.getIn().setHeader("name", "Thomas");
        exchange.getIn().removeHeader("date");

        repo.add(context, "foo", exchange);

        actual = repo.get(context, "foo");
        assertEquals("Bye World", actual.getIn().getBody());
        assertEquals("Thomas", actual.getIn().getHeader("name"));
        assertEquals(123, actual.getIn().getHeader("number"));
        date = actual.getIn().getHeader("date", Date.class);
        assertNull(date);
        assertSame(context, actual.getContext());
    }
}