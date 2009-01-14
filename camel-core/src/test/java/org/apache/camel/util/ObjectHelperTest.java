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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import junit.framework.TestCase;

/**
 * @version $Revision$
 */
public class ObjectHelperTest extends TestCase {
    public void testRemoveInitialCharacters() throws Exception {
        assertEquals(ObjectHelper.removeStartingCharacters("foo", '/'), "foo");
        assertEquals(ObjectHelper.removeStartingCharacters("/foo", '/'), "foo");
        assertEquals(ObjectHelper.removeStartingCharacters("//foo", '/'), "foo");
    }

    public void testGetPropertyName() throws Exception {
        Method method = getClass().getMethod("setCheese", String.class);
        assertNotNull("should have found a method!", method);

        String name = ObjectHelper.getPropertyName(method);
        assertEquals("Property name", "cheese", name);
    }

    public void setCheese(String cheese) {
        // used in the above unit test
    }

    public void testContains() throws Exception {
        String[] array = {"foo", "bar"};
        Collection<String> collection = Arrays.asList(array);

        assertTrue(ObjectHelper.contains(array, "foo"));
        assertTrue(ObjectHelper.contains(collection, "foo"));
        assertTrue(ObjectHelper.contains("foo", "foo"));

        assertFalse(ObjectHelper.contains(array, "xyz"));
        assertFalse(ObjectHelper.contains(collection, "xyz"));
        assertFalse(ObjectHelper.contains("foo", "xyz"));
    }

    public void testEqual() {
        assertTrue(ObjectHelper.equal(null, null));
        assertTrue(ObjectHelper.equal("", ""));
        assertTrue(ObjectHelper.equal(" ", " "));
        assertTrue(ObjectHelper.equal("Hello", "Hello"));
        assertTrue(ObjectHelper.equal(123, 123));
        assertTrue(ObjectHelper.equal(true, true));

        assertFalse(ObjectHelper.equal(null, ""));
        assertFalse(ObjectHelper.equal("", null));
        assertFalse(ObjectHelper.equal(" ", "    "));
        assertFalse(ObjectHelper.equal("Hello", "World"));
        assertFalse(ObjectHelper.equal(true, false));
        assertFalse(ObjectHelper.equal(new Object(), new Object()));

        byte[] a = new byte[] {40, 50, 60};
        byte[] b = new byte[] {40, 50, 60};
        assertTrue(ObjectHelper.equal(a, b));

        a = new byte[] {40, 50, 60};
        b = new byte[] {40, 50, 60, 70};
        assertFalse(ObjectHelper.equal(a, b));
    }

    public void testEqualByteArray() {
        assertTrue(ObjectHelper.equalByteArray("Hello".getBytes(), "Hello".getBytes()));
        assertFalse(ObjectHelper.equalByteArray("Hello".getBytes(), "World".getBytes()));

        assertTrue(ObjectHelper.equalByteArray("Hello Thai Elephant \u0E08".getBytes(), "Hello Thai Elephant \u0E08".getBytes()));
        assertTrue(ObjectHelper.equalByteArray(null, null));

        byte[] empty = new byte[0];
        assertTrue(ObjectHelper.equalByteArray(empty, empty));

        byte[] a = new byte[] {40, 50, 60};
        byte[] b = new byte[] {40, 50, 60};
        assertTrue(ObjectHelper.equalByteArray(a, b));

        a = new byte[] {40, 50, 60};
        b = new byte[] {40, 50, 60, 70};
        assertFalse(ObjectHelper.equalByteArray(a, b));

        a = new byte[] {40, 50, 60, 70};
        b = new byte[] {40, 50, 60};
        assertFalse(ObjectHelper.equalByteArray(a, b));

        a = new byte[] {40, 50, 60};
        b = new byte[0];
        assertFalse(ObjectHelper.equalByteArray(a, b));

        a = new byte[0];
        b = new byte[] {40, 50, 60};
        assertFalse(ObjectHelper.equalByteArray(a, b));

        a = new byte[] {40, 50, 60};
        b = null;
        assertFalse(ObjectHelper.equalByteArray(a, b));

        a = null;
        b = new byte[] {40, 50, 60};
        assertFalse(ObjectHelper.equalByteArray(a, b));

        a = null;
        b = null;
        assertTrue(ObjectHelper.equalByteArray(a, b));
    }

    public void testCreateIterator() {
        List<String> list = new ArrayList<String>();
        Iterator<String> iterator = list.iterator();
        assertSame("Should return the same iterator", iterator, ObjectHelper.createIterator(iterator));
    }

    public void testCreateIteratorWithStringAndCommaSeparator() {
        String s = "a,b,c";
        Iterator it = ObjectHelper.createIterator(s);
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertEquals("c", it.next());
    }

}
