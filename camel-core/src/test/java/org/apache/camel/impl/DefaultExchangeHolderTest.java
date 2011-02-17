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
package org.apache.camel.impl;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;

/**
 * @version 
 */
public class DefaultExchangeHolderTest extends ContextTestSupport {

    private String id;

    public void testMarshal() throws Exception {
        DefaultExchangeHolder holder = createHolder();
        assertNotNull(holder);
        assertNotNull(holder.toString());
    }

    public void testUnmarshal() throws Exception {
        id = null;
        Exchange exchange = new DefaultExchange(context);

        DefaultExchangeHolder.unmarshal(exchange, createHolder());
        assertEquals("Hello World", exchange.getIn().getBody());
        assertEquals("Bye World", exchange.getOut().getBody());
        assertEquals(123, exchange.getIn().getHeader("foo"));
        assertEquals(444, exchange.getProperty("bar"));
        assertEquals(id, exchange.getExchangeId());
    }

    private DefaultExchangeHolder createHolder() {
        Exchange exchange = new DefaultExchange(context);
        id = exchange.getExchangeId();
        exchange.getIn().setBody("Hello World");
        exchange.getIn().setHeader("foo", 123);
        exchange.setProperty("bar", 444);
        exchange.getOut().setBody("Bye World");
        return DefaultExchangeHolder.marshal(exchange);
    }

}
