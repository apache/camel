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

import java.util.Scanner;

import org.apache.camel.CamelContext;
import org.apache.camel.TestSupport;
import org.apache.camel.impl.DefaultCamelContext;

/**
 *
 */
public class GroupIteratorTest extends TestSupport {

    private CamelContext context;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        context = new DefaultCamelContext();
        context.start();
    }

    @Override
    public void tearDown() throws Exception {
        context.stop();
        super.tearDown();
    }

    public void testGroupIterator() throws Exception {
        String s = "ABC\nDEF\nGHI\nJKL\nMNO\nPQR\nSTU\nVW";
        Scanner scanner = new Scanner(s);
        scanner.useDelimiter("\n");

        GroupIterator gi = new GroupIterator(context, scanner, "\n", 3);

        assertTrue(gi.hasNext());
        assertEquals("ABC\nDEF\nGHI", gi.next());
        assertEquals("JKL\nMNO\nPQR", gi.next());
        assertEquals("STU\nVW", gi.next());
        assertFalse(gi.hasNext());

        IOHelper.close(gi);
    }

}
