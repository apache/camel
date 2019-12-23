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
package org.apache.camel.processor.resequencer;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SequenceTest extends Assert {

    private TestObject e1;
    private TestObject e2;
    private TestObject e3;

    private Sequence<TestObject> set;

    @Before
    public void setUp() throws Exception {
        e1 = new TestObject(3);
        e2 = new TestObject(4);
        e3 = new TestObject(7);
        set = new Sequence<>(new TestComparator());
        set.add(e3);
        set.add(e1);
        set.add(e2);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testPredecessor() {
        assertEquals(e1, set.predecessor(e2));
        assertEquals(null, set.predecessor(e1));
        assertEquals(null, set.predecessor(e3));
    }

    @Test
    public void testSuccessor() {
        assertEquals(e2, set.successor(e1));
        assertEquals(null, set.successor(e2));
        assertEquals(null, set.successor(e3));
    }

}
