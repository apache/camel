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
import org.apache.camel.Message;

/**
 * @version $Revision$
 */
public class DefaultMessageHeaderTest extends TestCase {

    public void testLookupCaseAgnostic() {
        Message msg = new DefaultMessage();
        assertNull(msg.getHeader("foo"));

        msg.setHeader("foo", "cheese");

        assertEquals("cheese", msg.getHeader("foo"));
        assertEquals("cheese", msg.getHeader("Foo"));
        assertEquals("cheese", msg.getHeader("FOO"));
    }

    public void testLookupCaseAgnosticAddHeader() {
        Message msg = new DefaultMessage();
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
        Message msg = new DefaultMessage();
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
        Message msg = new DefaultMessage();
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
        Message msg = new DefaultMessage();
        assertNull(msg.getHeader("foo"));

        msg.setHeader("foo", "cheese");
        msg.setHeader("Foo", "bar");

        assertEquals("bar", msg.getHeader("FOO"));
        assertEquals("bar", msg.getHeader("foo"));
        assertEquals("bar", msg.getHeader("Foo"));
    }

    public void testRemoveWithDifferentCase() {
        Message msg = new DefaultMessage();
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

}