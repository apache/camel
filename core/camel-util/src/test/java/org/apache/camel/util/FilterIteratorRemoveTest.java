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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * remove() removes the element last returned by next(); hasNext() looks ahead in the underlying iterator, so after it
 * that element can no longer be removed and remove() must fail instead of removing the element looked at. The same
 * holds for a next() that fails after it looked at further elements (they were all rejected, or the filter threw).
 */
class FilterIteratorRemoveTest {

    @Test
    void removeAfterHasNextFailsAndRemovesNothing() {
        List<String> list = new ArrayList<>(List.of("a", "b", "c"));
        FilterIterator<String> it = new FilterIterator<>(list.iterator());

        assertEquals("a", it.next());
        assertTrue(it.hasNext());
        assertThrows(IllegalStateException.class, it::remove);
        assertEquals(List.of("a", "b", "c"), list);
        assertEquals("b", it.next());
    }

    @Test
    void removeBeforeNextFails() {
        List<String> list = new ArrayList<>(List.of("a", "b"));
        FilterIterator<String> it = new FilterIterator<>(list.iterator());

        assertThrows(IllegalStateException.class, it::remove);
        assertTrue(it.hasNext());
        assertThrows(IllegalStateException.class, it::remove);
        assertEquals(List.of("a", "b"), list);
    }

    @Test
    void removeTwiceFails() {
        List<String> list = new ArrayList<>(List.of("a", "b"));
        FilterIterator<String> it = new FilterIterator<>(list.iterator());

        assertEquals("a", it.next());
        it.remove();
        assertThrows(IllegalStateException.class, it::remove);
        assertEquals(List.of("b"), list);
    }

    @Test
    void removeDirectlyAfterNextRemovesTheReturnedElement() {
        List<String> list = new ArrayList<>(List.of("a", "b", "c", "d"));
        FilterIterator<String> it = new FilterIterator<>(list.iterator(), s -> !"b".equals(s));

        List<String> seen = new ArrayList<>();
        while (it.hasNext()) {
            String s = it.next();
            seen.add(s);
            if (!"d".equals(s)) {
                it.remove();
            }
        }
        assertEquals(List.of("a", "c", "d"), seen);
        assertEquals(List.of("b", "d"), list);
        assertFalse(it.hasNext());
    }

    @Test
    void removeAfterNextThatSkippedFilteredElements() {
        List<String> list = new ArrayList<>(List.of("a", "b", "c"));
        FilterIterator<String> it = new FilterIterator<>(list.iterator(), s -> !"b".equals(s));

        assertEquals("a", it.next());
        assertEquals("c", it.next());
        it.remove();
        assertEquals(List.of("a", "b"), list);
    }

    @Test
    void removeAfterNextFoundOnlyRejectedElementsFailsAndRemovesNothing() {
        List<String> list = new ArrayList<>(List.of("a", "b", "c"));
        FilterIterator<String> it = new FilterIterator<>(list.iterator(), "a"::equals);

        assertEquals("a", it.next());
        assertThrows(NoSuchElementException.class, it::next);
        assertThrows(IllegalStateException.class, it::remove);
        assertEquals(List.of("a", "b", "c"), list);
        assertFalse(it.hasNext());
    }

    @Test
    void removeAfterTheFilterThrowsFailsAndRemovesNothing() {
        List<String> list = new ArrayList<>(List.of("a", "b", "c"));
        FilterIterator<String> it = new FilterIterator<>(list.iterator(), s -> {
            if ("b".equals(s)) {
                throw new IllegalArgumentException("cannot filter b");
            }
            return true;
        });

        assertEquals("a", it.next());
        assertThrows(IllegalArgumentException.class, it::next);
        assertThrows(IllegalStateException.class, it::remove);
        assertEquals(List.of("a", "b", "c"), list);

        // the iterator goes on after the element the filter failed on, and the next element can be removed
        assertEquals("c", it.next());
        it.remove();
        assertEquals(List.of("a", "b"), list);
    }

    @Test
    void removeAfterNextSkippedANullElementFailsAndRemovesNothing() {
        List<String> list = new ArrayList<>(Arrays.asList("a", null));
        FilterIterator<String> it = new FilterIterator<>(list.iterator());

        assertEquals("a", it.next());
        assertThrows(NoSuchElementException.class, it::next);
        assertThrows(IllegalStateException.class, it::remove);
        assertEquals(Arrays.asList("a", null), list);
    }

    // control: at the end of the underlying iterator next() fails without moving it, so the element returned last can
    // still be removed (as with the iterator of a list)
    @Test
    void removeAfterNextAtTheEndRemovesTheLastReturnedElement() {
        List<String> list = new ArrayList<>(List.of("a", "b"));
        FilterIterator<String> it = new FilterIterator<>(list.iterator());

        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertThrows(NoSuchElementException.class, it::next);
        it.remove();
        assertEquals(List.of("a"), list);
    }

    // control: hasNext() looked at the rejected elements before next() failed
    @Test
    void removeAfterHasNextFoundOnlyRejectedElementsFails() {
        List<String> list = new ArrayList<>(List.of("a", "b"));
        FilterIterator<String> it = new FilterIterator<>(list.iterator(), "a"::equals);

        assertEquals("a", it.next());
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
        assertThrows(IllegalStateException.class, it::remove);
        assertEquals(List.of("a", "b"), list);
    }
}
