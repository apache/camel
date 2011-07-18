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
package org.apache.camel.converter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.ObjectHelper;

/**
 * @version 
 */
public class ObjectHelperTest extends TestCase {

    public void testArrayAsIterator() throws Exception {
        String[] data = {"a", "b"};

        Iterator iter = ObjectHelper.createIterator(data);
        assertTrue("should have next", iter.hasNext());
        Object a = iter.next();
        assertEquals("a", "a", a);
        assertTrue("should have next", iter.hasNext());
        Object b = iter.next();
        assertEquals("b", "b", b);
        assertFalse("should not have a next", iter.hasNext());
    }

    public void testIsEmpty() {
        assertTrue(ObjectHelper.isEmpty(null));
        assertTrue(ObjectHelper.isEmpty(""));
        assertTrue(ObjectHelper.isEmpty(" "));
        assertFalse(ObjectHelper.isEmpty("A"));
        assertFalse(ObjectHelper.isEmpty(" A"));
        assertFalse(ObjectHelper.isEmpty(" A "));
        assertFalse(ObjectHelper.isEmpty(new Object()));
    }

    public void testIsNotEmpty() {
        assertFalse(ObjectHelper.isNotEmpty(null));
        assertFalse(ObjectHelper.isNotEmpty(""));
        assertFalse(ObjectHelper.isNotEmpty(" "));
        assertTrue(ObjectHelper.isNotEmpty("A"));
        assertTrue(ObjectHelper.isNotEmpty(" A"));
        assertTrue(ObjectHelper.isNotEmpty(" A "));
        assertTrue(ObjectHelper.isNotEmpty(new Object()));
    }

    public void testIteratorWithComma() {
        Iterator it = ObjectHelper.createIterator("Claus,Jonathan");
        assertEquals("Claus", it.next());
        assertEquals("Jonathan", it.next());
        assertEquals(false, it.hasNext());
    }

    public void testIteratorWithOtherDelimiter() {
        Iterator it = ObjectHelper.createIterator("Claus#Jonathan", "#");
        assertEquals("Claus", it.next());
        assertEquals("Jonathan", it.next());
        assertEquals(false, it.hasNext());
    }

    public void testIteratorEmpty() {
        Iterator it = ObjectHelper.createIterator("");
        assertEquals(false, it.hasNext());

        it = ObjectHelper.createIterator("    ");
        assertEquals(false, it.hasNext());

        it = ObjectHelper.createIterator(null);
        assertEquals(false, it.hasNext());
    }

    public void testIteratorIdempotentNext() {
        Iterator<Object> it = ObjectHelper.createIterator("a");
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        it.next();
        assertFalse(it.hasNext());
    }

    public void testIteratorIdempotentNextWithNodeList() {
        NodeList nodeList = new NodeList() {

            public Node item(int index) {
                return null;
            }

            public int getLength() {
                return 1;
            }
        };

        Iterator<Object> it = ObjectHelper.createIterator(nodeList);
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        it.next();
        assertFalse(it.hasNext());
    }

    public void testGetCamelContextPropertiesWithPrefix() {
        CamelContext context = new DefaultCamelContext();
        Map<String, String> properties = context.getProperties();
        properties.put("camel.object.helper.test1", "test1");
        properties.put("camel.object.helper.test2", "test2");
        properties.put("camel.object.test", "test");
        
        Properties result = ObjectHelper.getCamelPropertiesWithPrefix("camel.object.helper.", context);
        assertEquals("Get a wrong size properties", 2, result.size());
        assertEquals("It should contain the test1", "test1", result.get("test1"));
        assertEquals("It should contain the test2", "test2", result.get("test2"));
    }

    public void testEvaluateAsPredicate() throws Exception {
        assertEquals(false, ObjectHelper.evaluateValuePredicate(null));
        assertEquals(true, ObjectHelper.evaluateValuePredicate(123));

        assertEquals(true, ObjectHelper.evaluateValuePredicate("true"));
        assertEquals(true, ObjectHelper.evaluateValuePredicate("TRUE"));
        assertEquals(false, ObjectHelper.evaluateValuePredicate("false"));
        assertEquals(false, ObjectHelper.evaluateValuePredicate("FALSE"));
        assertEquals(true, ObjectHelper.evaluateValuePredicate("foobar"));
        assertEquals(true, ObjectHelper.evaluateValuePredicate(""));
        assertEquals(true, ObjectHelper.evaluateValuePredicate(" "));

        List<String> list = new ArrayList<String>();
        assertEquals(false, ObjectHelper.evaluateValuePredicate(list));
        list.add("foo");
        assertEquals(true, ObjectHelper.evaluateValuePredicate(list));
    }

}
