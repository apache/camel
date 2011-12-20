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
 * @version 
 */
public class KeyValueHolderTest extends TestCase {

    @SuppressWarnings("unchecked")
    public void testKeyValueHolder() {
        KeyValueHolder foo = new KeyValueHolder("foo", 123);

        assertEquals("foo", foo.getKey());
        assertEquals(123, foo.getValue());

        KeyValueHolder bar = new KeyValueHolder("bar", 456);

        assertFalse("Should not be equals", foo.equals(bar));

        assertNotSame(foo.hashCode(), bar.hashCode());

        KeyValueHolder bar2 = new KeyValueHolder("bar", 456);
        assertTrue("Should be equals", bar.equals(bar2));

        assertEquals("foo -> 123", foo.toString());
    }

}
