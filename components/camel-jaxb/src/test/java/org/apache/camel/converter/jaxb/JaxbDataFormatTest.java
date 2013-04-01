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
package org.apache.camel.converter.jaxb;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JaxbDataFormatTest {

    private JaxbDataFormat jaxbDataFormat;
    private CamelContext camelContext;

    @Before
    public void setUp() throws Exception {
        camelContext = new DefaultCamelContext();

        jaxbDataFormat = new JaxbDataFormat();
        jaxbDataFormat.setCamelContext(camelContext);
        jaxbDataFormat.doStart();
    }

    @Test
    public void testNeedFilteringDisabledFiltering() {
        jaxbDataFormat.setFilterNonXmlChars(false);
        Exchange exchange = new DefaultExchange(camelContext);

        assertFalse(jaxbDataFormat.needFiltering(exchange));
    }

    @Test
    public void testNeedFilteringEnabledFiltering() {
        jaxbDataFormat.setFilterNonXmlChars(true);
        Exchange exchange = new DefaultExchange(camelContext);

        assertTrue(jaxbDataFormat.needFiltering(exchange));
    }

    @Test
    public void testNeedFilteringTruePropagates() {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.setProperty(Exchange.FILTER_NON_XML_CHARS, Boolean.TRUE);

        assertTrue(jaxbDataFormat.needFiltering(exchange));
    }

    @Test
    public void testNeedFilteringFalsePropagates() {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.setProperty(Exchange.FILTER_NON_XML_CHARS, Boolean.FALSE);

        assertFalse(jaxbDataFormat.needFiltering(exchange));
    }
}
