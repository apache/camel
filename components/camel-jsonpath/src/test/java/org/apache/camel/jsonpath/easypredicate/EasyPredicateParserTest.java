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
package org.apache.camel.jsonpath.easypredicate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EasyPredicateParserTest {

    private final EasyPredicateParser parser = new EasyPredicateParser();

    @Test
    public void testWithoutRoot() {
        assertEquals("$..*[?(@.price < 10)]", parser.parse("price < 10"));
        assertEquals("$.store.book[?(@.price < 10)]", parser.parse("store.book.price < 10"));
    }

    @Test
    public void testWithRoot() {
        // CAMEL-24841: the comparison written on the path, the way people write it first
        assertEquals("$[?(@.status == 'paid')]", parser.parse("$.status == 'paid'"));
        assertEquals("$.store.book[?(@.price < 10)]", parser.parse("$.store.book.price < 10"));
    }

    @Test
    public void testRegularPathsAreKept() {
        assertEquals("$.status", parser.parse("$.status"));
        assertEquals("$..book[?(@.price < 10)]", parser.parse("$..book[?(@.price < 10)]"));
        assertEquals("$[?(@.status == 'paid')]", parser.parse("$[?(@.status == 'paid')]"));
    }
}
