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
package org.apache.camel.support;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.camel.RuntimeCamelException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class StreamListIteratorTest {

    @Test
    void mapsEachRow() {
        StreamListIterator<String, Integer> rows = new StreamListIterator<>(Integer::valueOf, List.of("1", "2").iterator());

        List<Integer> mapped = new ArrayList<>();
        rows.forEachRemaining(mapped::add);

        assertThat(mapped).containsExactly(1, 2);
        assertThat(rows.hasNext()).isFalse();
        assertThatThrownBy(rows::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void keepsFailingOnceMappingARowHasFailed() {
        StreamListIterator<String, Integer> rows
                = new StreamListIterator<>(Integer::valueOf, List.of("1", "two", "3").iterator());
        assertThat(rows.next()).isEqualTo(1);

        // the first failure is the one of the row mapper
        Throwable failure = catchThrowable(rows::next);
        assertThat(failure).isInstanceOf(NumberFormatException.class);

        // the row that failed has been taken from the delegate: going on would skip it, so the iterator keeps failing
        assertThatThrownBy(rows::hasNext).isInstanceOf(RuntimeCamelException.class).cause().isSameAs(failure);
        assertThatThrownBy(rows::next).isInstanceOf(RuntimeCamelException.class).cause().isSameAs(failure);
        assertThatThrownBy(rows::hasNext).isInstanceOf(RuntimeCamelException.class).cause().isSameAs(failure);
    }
}
