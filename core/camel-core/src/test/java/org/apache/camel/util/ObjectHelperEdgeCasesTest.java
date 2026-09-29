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
package org.apache.camel.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Ordered;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.ObjectHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ObjectHelperEdgeCasesTest {

    private CamelContext context;
    private TypeConverter tc;

    @BeforeEach
    public void setUp() {
        context = new DefaultCamelContext();
        context.start();
        tc = context.getTypeConverter();
    }

    @AfterEach
    public void tearDown() {
        context.stop();
    }

    @Test
    public void testContainsTypeCoercesCollectionElements() {
        assertTrue(ObjectHelper.typeCoerceContains(tc, List.of(1, 2, 3), "2", false));
        assertTrue(ObjectHelper.typeCoerceContains(tc, List.of(1, 2, 3), "2", true));
        assertFalse(ObjectHelper.typeCoerceContains(tc, List.of(1, 2, 3), "4", false));
    }

    @Test
    public void testContainsIgnoreCaseMatchesWholeElements() {
        assertTrue(ObjectHelper.typeCoerceContains(tc, List.of("FooBar"), "foobar", true));
        assertFalse(ObjectHelper.typeCoerceContains(tc, List.of("foobar"), "foo", true));
        assertFalse(ObjectHelper.typeCoerceContains(tc, List.of("foobar"), "foo", false));

        List<String> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add("a");
        assertTrue(ObjectHelper.typeCoerceContains(tc, withNull, "A", true));
        assertTrue(ObjectHelper.typeCoerceContains(tc, withNull, null, true));
    }

    @Test
    public void testStringIsOnlyEqualToBooleanWhenTrueOrFalse() {
        assertFalse(ObjectHelper.typeCoerceEquals(tc, "hello", Boolean.FALSE));
        assertFalse(ObjectHelper.typeCoerceEquals(tc, Boolean.FALSE, "hello"));
        assertNotEquals(0, ObjectHelper.typeCoerceCompare(tc, "hello", Boolean.FALSE));
        assertNotEquals(0, ObjectHelper.typeCoerceCompare(tc, Boolean.FALSE, "hello"));

        assertTrue(ObjectHelper.typeCoerceEquals(tc, "false", Boolean.FALSE));
        assertTrue(ObjectHelper.typeCoerceEquals(tc, Boolean.TRUE, "TRUE"));
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, "False", Boolean.FALSE));
    }

    @Test
    public void testIteratorOfOnlyTheDelimiter() {
        assertFalse(ObjectHelper.createIterator(";", ";").hasNext());
        assertFalse(ObjectHelper.createIterator(":::", "::").hasNext());
        assertFalse(ObjectHelper.createIterable(";", ";", false, false).iterator().hasNext());
        // an empty value between two delimiters is kept
        assertEquals(List.of(""), toList(ObjectHelper.createIterable(";;", ";", true, false).iterator()));
    }

    @Test
    public void testIteratorWithPatternMatchingEmpty() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertEquals(List.of("a", "b"), toList(ObjectHelper.createIterator("a b", "\\s*", false, true)));
            assertEquals(List.of("a", "b"), toList(ObjectHelper.createIterator("a;b", ";*", false, true)));
        });
    }

    @Test
    public void testCompareOrderedDoesNotOverflow() {
        Ordered highest = () -> Ordered.HIGHEST;
        Ordered one = () -> 1;
        Ordered lowest = () -> Ordered.LOWEST;
        assertTrue(ObjectHelper.compare(highest, one) < 0);
        assertTrue(ObjectHelper.compare(one, highest) > 0);
        assertTrue(ObjectHelper.compare(highest, lowest) < 0);

        List<Ordered> list = new ArrayList<>(Arrays.asList(lowest, one, highest));
        list.sort(ObjectHelper::compare);
        assertEquals(List.of(highest, one, lowest), list);
    }

    @Test
    public void testSimpleContainsOnListOfNumbers() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("ids", List.of(1, 2, 3));
        assertTrue(simple(exchange, "${header.ids} contains '2'"));
        assertFalse(simple(exchange, "${header.ids} contains '4'"));
    }

    @Test
    public void testSimpleEqualsFalse() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("hello");
        assertFalse(simple(exchange, "${body} == false"));
        assertTrue(simple(exchange, "${body} != false"));

        exchange.getMessage().setBody("false");
        assertTrue(simple(exchange, "${body} == false"));
    }

    private boolean simple(Exchange exchange, String predicate) {
        return context.resolveLanguage("simple").createPredicate(predicate).matches(exchange);
    }

    private static List<Object> toList(Iterator<?> it) {
        List<Object> answer = new ArrayList<>();
        it.forEachRemaining(answer::add);
        return answer;
    }
}
