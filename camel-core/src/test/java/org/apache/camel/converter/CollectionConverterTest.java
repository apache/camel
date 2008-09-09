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

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import junit.framework.TestCase;

/**
 * Test cases for {@link CollectionConverter}
 */
@SuppressWarnings("unchecked")
public class CollectionConverterTest extends TestCase {
    
    private static final List<String> SMURFS = Arrays.asList("Papa smurf", "Smurfette", "Hefty smurf", "Jokey smurf");

    public void testIteratorToList() throws Exception {
        assertSmurfs(CollectionConverter.toArrayList(SMURFS.iterator()));
    }
    
    public void testIterableToList() throws Exception {
        assertSmurfs(CollectionConverter.toList(new Iterable() {
            public Iterator iterator() {
                return SMURFS.iterator();
            }
        }));
        // no conversion should occur for the list itself
        assertSame(SMURFS, CollectionConverter.toList((Iterable) SMURFS));
    }

    private void assertSmurfs(Collection<String> result) {
        assertEquals(SMURFS.size(), result.size());
        for (String key : result) {
            assertTrue(SMURFS.contains(key));
        }
    }
}
