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
package org.apache.camel.builder;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.Assert;
import org.junit.Test;

public class ExchangeBuilderTest extends Assert {
    private static final DefaultCamelContext CONTEXT = new DefaultCamelContext();
    private static final String BODY = "Message Body";
    private static final String KEY = "Header key";
    private static final String VALUE = "Header value";
    private static final String PROPERTY_KEY = "Property key";
    private static final String PROPERTY_VALUE = "Property value";

    @Test
    public void testBuildAnExchangeWithDefaultPattern() {
        Exchange exchange = new DefaultExchange(CONTEXT);
        Exchange builtExchange = ExchangeBuilder.anExchange(CONTEXT).build();

        assertEquals(exchange.getPattern(), builtExchange.getPattern());
    }

    @Test
    public void testBuildAnExchangeWithBodyHeaderAndPattern() throws Exception {

        Exchange exchange = ExchangeBuilder.anExchange(CONTEXT).withBody(BODY).withHeader(KEY, VALUE).withProperty(PROPERTY_KEY, PROPERTY_VALUE).withPattern(ExchangePattern.InOut)
            .build();

        assertEquals(exchange.getIn().getBody(), BODY);
        assertEquals(exchange.getIn().getHeader(KEY), VALUE);
        assertEquals(exchange.getPattern(), ExchangePattern.InOut);
        assertEquals(exchange.getProperty(PROPERTY_KEY), PROPERTY_VALUE);
    }

}
