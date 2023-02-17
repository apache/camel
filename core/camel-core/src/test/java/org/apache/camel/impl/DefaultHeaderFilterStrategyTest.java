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

import java.util.HashSet;
import java.util.Set;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.junit.jupiter.api.Test;

import static org.apache.camel.support.DefaultHeaderFilterStrategy.CAMEL_FILTER_STARTS_WITH;
import static org.junit.jupiter.api.Assertions.*;

public class DefaultHeaderFilterStrategyTest extends ContextTestSupport {

    @Test
    public void testSimpleDefaultHeaderFilterStrategy() {
        DefaultHeaderFilterStrategy comp = new DefaultHeaderFilterStrategy();

        comp.setAllowNullValues(true);
        assertTrue(comp.isAllowNullValues());

        comp.setLowerCase(true);
        assertTrue(comp.isLowerCase());

        comp.setCaseInsensitive(true);
        assertTrue(comp.isCaseInsensitive());

        comp.setFilterOnMatch(false);
        assertFalse(comp.isFilterOnMatch());
    }

    @Test
    public void testInFilterDefaultHeaderFilterStrategy() {
        DefaultHeaderFilterStrategy comp = new DefaultHeaderFilterStrategy();

        Set<String> set = new HashSet<>();
        set.add("foo");
        comp.setInFilter(set);

        assertEquals(set, comp.getInFilter());
    }

    @Test
    public void testInFilterDoFilterDefaultHeaderFilterStrategy() {
        DefaultHeaderFilterStrategy comp = new DefaultHeaderFilterStrategy();

        Set<String> set = new HashSet<>();
        set.add("foo");
        comp.setInFilter(set);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("bar", 123);
        exchange.getIn().setHeader("foo", "cheese");

        assertFalse(comp.applyFilterToExternalHeaders("bar", 123, exchange));
        assertTrue(comp.applyFilterToExternalHeaders("foo", "cheese", exchange));
    }

    @Test
    public void testOutFilterDefaultHeaderFilterStrategy() {
        DefaultHeaderFilterStrategy comp = new DefaultHeaderFilterStrategy();

        Set<String> set = new HashSet<>();
        set.add("foo");
        comp.setOutFilter(set);

        assertEquals(set, comp.getOutFilter());
    }

    @Test
    public void testOutFilterDoFilterDefaultHeaderFilterStrategy() {
        DefaultHeaderFilterStrategy comp = new DefaultHeaderFilterStrategy();

        Set<String> set = new HashSet<>();
        set.add("foo");
        comp.setOutFilter(set);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("bar", 123);
        exchange.getIn().setHeader("foo", "cheese");

        assertFalse(comp.applyFilterToCamelHeaders("bar", 123, exchange));
        assertTrue(comp.applyFilterToCamelHeaders("foo", "cheese", exchange));
    }

    @Test
    public void testOutReverseFilterDefaultHeaderFilterStrategy() {
        DefaultHeaderFilterStrategy comp = new DefaultHeaderFilterStrategy();

        comp.setFilterOnMatch(false);

        Set<String> set = new HashSet<>();
        set.add("foo");
        comp.setOutFilter(set);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("bar", 123);
        exchange.getIn().setHeader("foo", "cheese");

        assertTrue(comp.applyFilterToCamelHeaders("bar", 123, exchange));
        assertFalse(comp.applyFilterToCamelHeaders("foo", "cheese", exchange));
    }

    @Test
    public void testCaseInsensitiveHeaderNameDoFilterDefaultHeaderFilterStrategy() {
        DefaultHeaderFilterStrategy comp = new DefaultHeaderFilterStrategy();
        comp.setCaseInsensitive(true);

        Set<String> set = new HashSet<>();
        set.add("Content-Type");
        comp.setOutFilter(set);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("content-type", "application/xml");
        exchange.getIn().setHeader("Content-Type", "application/json");

        assertTrue(comp.applyFilterToCamelHeaders("content-type", "application/xml", exchange));
        assertTrue(comp.applyFilterToCamelHeaders("Content-Type", "application/json", exchange));
    }

    @Test
    public void testOutStartsWith() {
        DefaultHeaderFilterStrategy comp = new DefaultHeaderFilterStrategy();

        comp.setOutFilterStartsWith(CAMEL_FILTER_STARTS_WITH);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("bar", 123);
        exchange.getIn().setHeader("foo", "cheese");
        exchange.getIn().setHeader("CamelName", "Apache Camel");
        exchange.getIn().setHeader("camelVersion", "3.7");
        exchange.getIn().setHeader("org.apache.camel.id", "foo");

        assertFalse(comp.applyFilterToCamelHeaders("bar", 123, exchange));
        assertFalse(comp.applyFilterToCamelHeaders("foo", "cheese", exchange));
        assertTrue(comp.applyFilterToCamelHeaders("CamelName", "Apache Camel", exchange));
        assertTrue(comp.applyFilterToCamelHeaders("camelVersion", "3.7", exchange));
        assertTrue(comp.applyFilterToCamelHeaders("camelNextVersion", "3.8", exchange));
        assertTrue(comp.applyFilterToCamelHeaders("org.apache.camel.component.seda.queueSize", "123", exchange));
    }

    @Test
    public void testInStartsWith() {
        DefaultHeaderFilterStrategy comp = new DefaultHeaderFilterStrategy();

        comp.setInFilterStartsWith(CAMEL_FILTER_STARTS_WITH);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("bar", 123);
        exchange.getIn().setHeader("foo", "cheese");
        exchange.getIn().setHeader("CamelVersion", "3.7");
        exchange.getIn().setHeader("org.apache.camel.component.jetty.session", "true");

        assertFalse(comp.applyFilterToExternalHeaders("bar", 123, exchange));
        assertFalse(comp.applyFilterToExternalHeaders("foo", "cheese", exchange));
        assertTrue(comp.applyFilterToExternalHeaders("CamelVersion", "3.7", exchange));
        assertTrue(comp.applyFilterToExternalHeaders("org.apache.camel.component.jetty.session", "true", exchange));
    }

}
