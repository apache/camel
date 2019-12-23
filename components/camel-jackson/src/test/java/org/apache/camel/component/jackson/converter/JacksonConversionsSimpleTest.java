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
package org.apache.camel.component.jackson.converter;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JacksonConversionsSimpleTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // enable jackson type converter by setting this property on
        // CamelContext
        context.getGlobalOptions().put(JacksonConstants.ENABLE_TYPE_CONVERTER, "true");
        return context;
    }

    @Test
    public void shouldNotConvertMapToString() {
        Exchange exchange = new DefaultExchange(context);

        Map<String, String> body = new HashMap<>();
        Object convertedObject = context.getTypeConverter().convertTo(String.class, exchange, body);
        // will do a toString which is an empty map
        assertEquals(body.toString(), convertedObject);
    }

    @Test
    public void shouldNotConvertMapToNumber() {
        Exchange exchange = new DefaultExchange(context);

        Object convertedObject = context.getTypeConverter().convertTo(Long.class, exchange, new HashMap<String, String>());
        assertNull(convertedObject);
    }

    @Test
    public void shouldNotConvertMapToPrimitive() {
        Exchange exchange = new DefaultExchange(context);
        Object convertedObject = context.getTypeConverter().convertTo(long.class, exchange, new HashMap<String, String>());

        assertNull(convertedObject);
    }

    @Test
    public void shouldNotConvertStringToEnum() {
        Exchange exchange = new DefaultExchange(context);

        Object convertedObject = context.getTypeConverter().convertTo(ExchangePattern.class, exchange, "InOnly");

        assertEquals(ExchangePattern.InOnly, convertedObject);
    }

}
