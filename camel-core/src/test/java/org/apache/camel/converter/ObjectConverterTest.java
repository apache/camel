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

import java.util.Iterator;

import junit.framework.TestCase;

/**
 * @version $Revision$
 */
public class ObjectConverterTest extends TestCase {
    public void testArrayAsIterator() throws Exception {
        String[] data = {"a", "b"};

        Iterator iter = ObjectConverter.iterator(data);
        assertTrue("should have next", iter.hasNext());
        Object a = iter.next();
        assertEquals("a", "a", a);
        assertTrue("should have next", iter.hasNext());
        Object b = iter.next();
        assertEquals("b", "b", b);
        assertFalse("should not have a next", iter.hasNext());
    }

}
