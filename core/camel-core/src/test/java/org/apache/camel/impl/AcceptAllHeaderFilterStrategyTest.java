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
package org.apache.camel.impl;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.AcceptAllHeaderFilterStrategy;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class AcceptAllHeaderFilterStrategyTest extends ContextTestSupport {

    @Test
    public void testAcceptAll() {
        HeaderFilterStrategy comp = new AcceptAllHeaderFilterStrategy();

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("bar", 123);
        exchange.getIn().setHeader("foo", "cheese");
        exchange.getIn().setHeader("CamelVersion", "3.7");
        exchange.getIn().setHeader("org.apache.camel.component.jetty.session", "true");

        assertFalse(comp.applyFilterToExternalHeaders("bar", 123, exchange));
        assertFalse(comp.applyFilterToExternalHeaders("foo", "cheese", exchange));
        assertFalse(comp.applyFilterToExternalHeaders("CamelVersion", "3.7", exchange));
        assertFalse(comp.applyFilterToExternalHeaders("org.apache.camel.component.jetty.session", "true", exchange));
    }

}
