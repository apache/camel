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

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.Message;

/**
 * @version 
 */
public class DefaultMessageHeaderTest extends TestCase {
    
    private CamelContext camelContext = new DefaultCamelContext();

    public void testLookupCaseAgnostic() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("foo", "cheese");

        assertEquals("cheese", msg.getHeader("foo"));
        assertEquals("cheese", msg.getHeader("Foo"));
        assertEquals("cheese", msg.getHeader("FOO"));
    }

    public void testLookupCaseAgnosticAddHeader() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("foo", "cheese");

        assertEquals("cheese", msg.getHeader("foo"));
        assertEquals("cheese", msg.getHeader("Foo"));
        assertEquals("cheese", msg.getHeader("FOO"));
        assertNull(msg.getHeader("unknown"));

        msg.setHeader("bar", "beer");

        assertEquals("beer", msg.getHeader("bar"));
        assertEquals("beer", msg.getHeader("Bar"));
        assertEquals("beer", msg.getHeader("BAR"));
        assertNull(msg.getHeader("unknown"));
    }

    public void testLookupCaseAgnosticAddHeader2() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("foo", "cheese");

        assertEquals("cheese", msg.getHeader("FOO"));
        assertEquals("cheese", msg.getHeader("foo"));
        assertEquals("cheese", msg.getHeader("Foo"));
        assertNull(msg.getHeader("unknown"));

        msg.setHeader("bar", "beer");

        assertEquals("beer", msg.getHeader("BAR"));
        assertEquals("beer", msg.getHeader("bar"));
        assertEquals("beer", msg.getHeader("Bar"));
        assertNull(msg.getHeader("unknown"));
    }

    public void testLookupCaseAgnosticAddHeaderRemoveHeader() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("foo", "cheese");

        assertEquals("cheese", msg.getHeader("foo"));
        assertEquals("cheese", msg.getHeader("Foo"));
        assertEquals("cheese", msg.getHeader("FOO"));
        assertNull(msg.getHeader("unknown"));

        msg.setHeader("bar", "beer");

        assertEquals("beer", msg.getHeader("bar"));
        assertEquals("beer", msg.getHeader("Bar"));
        assertEquals("beer", msg.getHeader("BAR"));
        assertNull(msg.getHeader("unknown"));

        msg.removeHeader("bar");
        assertNull(msg.getHeader("bar"));
        assertNull(msg.getHeader("unknown"));
    }

    public void testSetWithDifferentCase() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("foo", "cheese");
        msg.setHeader("Foo", "bar");

        assertEquals("bar", msg.getHeader("FOO"));
        assertEquals("bar", msg.getHeader("foo"));
        assertEquals("bar", msg.getHeader("Foo"));
    }

    public void testRemoveWithDifferentCase() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("foo", "cheese");
        msg.setHeader("Foo", "bar");

        assertEquals("bar", msg.getHeader("FOO"));
        assertEquals("bar", msg.getHeader("foo"));
        assertEquals("bar", msg.getHeader("Foo"));

        msg.removeHeader("FOO");

        assertEquals(null, msg.getHeader("foo"));
        assertEquals(null, msg.getHeader("Foo"));
        assertEquals(null, msg.getHeader("FOO"));

        assertTrue(msg.getHeaders().isEmpty());
    }

    public void testRemoveHeaderWithNullValue() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("tick", null);
        msg.removeHeader("tick");

        assertTrue(msg.getHeaders().isEmpty());
    }

    public void testRemoveHeadersWithWildcard() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("tick", "bla");
        msg.setHeader("tack", "blaa");
        msg.setHeader("tock", "blaaa");

        assertEquals("bla", msg.getHeader("tick"));
        assertEquals("blaa", msg.getHeader("tack"));
        assertEquals("blaaa", msg.getHeader("tock"));

        msg.removeHeaders("t*");

        assertTrue(msg.getHeaders().isEmpty());
    }

    public void testRemoveHeadersAllWithWildcard() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("tick", "bla");
        msg.setHeader("tack", "blaa");
        msg.setHeader("tock", "blaaa");

        assertEquals("bla", msg.getHeader("tick"));
        assertEquals("blaa", msg.getHeader("tack"));
        assertEquals("blaaa", msg.getHeader("tock"));

        msg.removeHeaders("*");

        assertTrue(msg.getHeaders().isEmpty());
    }

    public void testRemoveHeadersWithExclude() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("tick", "bla");
        msg.setHeader("tiack", "blaa");
        msg.setHeader("tiock", "blaaa");
        msg.setHeader("tiuck", "blaaaa");

        msg.removeHeaders("ti*", "tiuck", "tiack");

        assertEquals(2, msg.getHeaders().size());
        assertEquals("blaa", msg.getHeader("tiack"));
        assertEquals("blaaaa", msg.getHeader("tiuck"));
    }

    public void testRemoveHeadersAllWithExclude() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("tick", "bla");
        msg.setHeader("tack", "blaa");
        msg.setHeader("tock", "blaaa");

        assertEquals("bla", msg.getHeader("tick"));
        assertEquals("blaa", msg.getHeader("tack"));
        assertEquals("blaaa", msg.getHeader("tock"));

        msg.removeHeaders("*", "tick", "tock", "toe");

        // new message headers
        assertEquals("bla", msg.getHeader("tick"));
        assertEquals(null, msg.getHeader("tack"));
        assertEquals("blaaa", msg.getHeader("tock"));
    }

    public void testRemoveHeadersWithWildcardInExclude() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("tick", "bla");
        msg.setHeader("tack", "blaa");
        msg.setHeader("taick", "blaa");
        msg.setHeader("tock", "blaaa");

        msg.removeHeaders("*", "ta*");

        assertEquals(2, msg.getHeaders().size());
        assertEquals("blaa", msg.getHeader("tack"));
        assertEquals("blaa", msg.getHeader("taick"));
    }

    public void testRemoveHeadersWithNulls() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("tick", "bla");
        msg.setHeader("tack", "blaa");
        msg.setHeader("tock", "blaaa");
        msg.setHeader("taack", "blaaaa");

        assertEquals("bla", msg.getHeader("tick"));
        assertEquals("blaa", msg.getHeader("tack"));
        assertEquals("blaaa", msg.getHeader("tock"));
        assertEquals("blaaaa", msg.getHeader("taack"));

        msg.removeHeaders(null, null, null, null);

        assertFalse(msg.getHeaders().isEmpty());
    }

    public void testRemoveHeadersWithNonExcludeHeaders() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("tick", "bla");
        msg.setHeader("tack", "blaa");
        msg.setHeader("tock", "blaaa");

        msg.removeHeaders("*", "camels", "are", "fun");

        assertTrue(msg.getHeaders().isEmpty());
    }

    public void testWithDefaults() {
        DefaultMessage msg = new DefaultMessage(camelContext);
        // must have exchange so to leverage the type converters
        msg.setExchange(new DefaultExchange(new DefaultCamelContext()));

        assertNull(msg.getHeader("foo"));

        msg.setHeader("foo", "cheese");

        assertEquals("cheese", msg.getHeader("foo"));
        assertEquals("cheese", msg.getHeader("foo", "foo"));
        assertEquals("cheese", msg.getHeader("foo", "foo", String.class));

        assertEquals(null, msg.getHeader("beer"));
        assertEquals("foo", msg.getHeader("beer", "foo"));
        assertEquals(Integer.valueOf(123), msg.getHeader("beer", "123", Integer.class));
    }

}
