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
package org.apache.camel.util;

import junit.framework.TestCase;

/**
 * Unit test for StringHelper
 */
public class StringHelperTest extends TestCase {

    public void testSimpleSanitized() {
        String out = StringHelper.sanitize("hello");
        assertTrue("Should not contain : ", out.indexOf(':') == -1);
        assertTrue("Should not contain . ", out.indexOf('.') == -1);
    }

    public void testNotFileFriendlySimpleSanitized() {
        String out = StringHelper.sanitize("c:\\helloworld");
        assertTrue("Should not contain : ", out.indexOf(':') == -1);
        assertTrue("Should not contain . ", out.indexOf('.') == -1);
    }

    public void testCountChar() {
        assertEquals(0, StringHelper.countChar("Hello World", 'x'));
        assertEquals(1, StringHelper.countChar("Hello World", 'e'));
        assertEquals(3, StringHelper.countChar("Hello World", 'l'));
        assertEquals(1, StringHelper.countChar("Hello World", ' '));
        assertEquals(0, StringHelper.countChar("", ' '));
        assertEquals(0, StringHelper.countChar(null, ' '));
    }

    public void testRemoveQuotes() throws Exception {
        assertEquals("Hello World", StringHelper.removeQuotes("Hello World"));
        assertEquals("", StringHelper.removeQuotes(""));
        assertEquals(null, StringHelper.removeQuotes(null));
        assertEquals(" ", StringHelper.removeQuotes(" "));
        assertEquals("foo", StringHelper.removeQuotes("'foo'"));
        assertEquals("foo", StringHelper.removeQuotes("'foo"));
        assertEquals("foo", StringHelper.removeQuotes("foo'"));
        assertEquals("foo", StringHelper.removeQuotes("\"foo\""));
        assertEquals("foo", StringHelper.removeQuotes("\"foo"));
        assertEquals("foo", StringHelper.removeQuotes("foo\""));
        assertEquals("foo", StringHelper.removeQuotes("'foo\""));
    }

}
