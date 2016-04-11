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
package org.apache.camel.component.netty4.http;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NettyHttpHeaderFilterStrategyTest {
    
    private NettyHttpHeaderFilterStrategy filter;
    private Exchange exchange;
    
    @Before
    public void setUp() {
        filter = new NettyHttpHeaderFilterStrategy();
        exchange = new DefaultExchange(new DefaultCamelContext());
    }

    @Test
    public void applyFilterToExternalHeaders() {
        assertFalse(filter.applyFilterToExternalHeaders("content-length", 10, exchange));
        assertFalse(filter.applyFilterToExternalHeaders("Content-Length", 10, exchange));
        assertFalse(filter.applyFilterToExternalHeaders("content-type", "text/xml", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("Content-Type", "text/xml", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("cache-control", "no-cache", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("Cache-Control", "no-cache", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("connection", "close", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("Connection", "close", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("date", "close", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("Data", "close", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("pragma", "no-cache", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("Pragma", "no-cache", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("trailer", "Max-Forwards", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("Trailer", "Max-Forwards", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("transfer-encoding", "chunked", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("Transfer-Encoding", "chunked", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("upgrade", "HTTP/2.0", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("Upgrade", "HTTP/2.0", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("via", "1.1 nowhere.com", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("Via", "1.1 nowhere.com", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("warning", "199 Miscellaneous warning", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("Warning", "199 Miscellaneous warning", exchange));

        // any Camel header should be filtered
        assertTrue(filter.applyFilterToExternalHeaders("CamelHeader", "test", exchange));
        assertTrue(filter.applyFilterToExternalHeaders("org.apache.camel.header", "test", exchange));

        assertFalse(filter.applyFilterToExternalHeaders("notFilteredHeader", "test", exchange));

        assertFalse(filter.applyFilterToExternalHeaders("host", "dummy.host.com", exchange));
        assertFalse(filter.applyFilterToExternalHeaders("Host", "dummy.host.com", exchange));
    }

    @Test
    public void applyFilterToCamelHeaders() {
        assertTrue(filter.applyFilterToCamelHeaders("content-length", 10, exchange));
        assertTrue(filter.applyFilterToCamelHeaders("Content-Length", 10, exchange));
        assertTrue(filter.applyFilterToCamelHeaders("content-type", "text/xml", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("Content-Type", "text/xml", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("cache-control", "no-cache", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("Cache-Control", "no-cache", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("connection", "close", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("Connection", "close", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("date", "close", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("Date", "close", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("pragma", "no-cache", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("Pragma", "no-cache", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("trailer", "Max-Forwards", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("Trailer", "Max-Forwards", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("transfer-encoding", "chunked", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("Transfer-Encoding", "chunked", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("upgrade", "HTTP/2.0", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("Upgrade", "HTTP/2.0", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("via", "1.1 nowhere.com", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("Via", "1.1 nowhere.com", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("warning", "199 Miscellaneous warning", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("Warning", "199 Miscellaneous warning", exchange));

        // any Camel header should be filtered
        assertTrue(filter.applyFilterToCamelHeaders("CamelHeader", "test", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("org.apache.camel.header", "test", exchange));

        assertFalse(filter.applyFilterToCamelHeaders("notFilteredHeader", "test", exchange));

        assertTrue(filter.applyFilterToCamelHeaders("host", "dummy.host.com", exchange));
        assertTrue(filter.applyFilterToCamelHeaders("Host", "dummy.host.com", exchange));
    }
}
