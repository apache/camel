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
package org.apache.camel.component.reactive.streams;

import java.util.Collection;
import java.util.Collections;
import java.util.Deque;

/**
 * A list of possible backpressure strategy to use when the emission of upstream items cannot respect backpressure.
 */
public enum ReactiveStreamsBackpressureStrategy {

    /**
     * Buffers <em>all</em> onNext values until the downstream consumes it.
     */
    BUFFER {
        @Override
        public <T> Collection<T> update(Deque<T> buffer, T element) {
            buffer.addLast(element);
            return Collections.emptySet();
        }
    },

    /**
     * Drops the most recent onNext value if the downstream can't keep up.
     */
    DROP {
        @Override
        public <T> Collection<T> update(Deque<T> buffer, T element) {
            if (buffer.size() > 0) {
                return Collections.singletonList(element);
            } else {
                buffer.addLast(element);
                return Collections.emptySet();
            }
        }
    },

    /**
     * Keeps only the latest onNext value, overwriting any previous value if the
     * downstream can't keep up.
     */
    LATEST {
        @Override
        public <T> Collection<T> update(Deque<T> buffer, T element) {
            Collection<T> discarded = Collections.emptySet();
            if (buffer.size() > 0) {
                discarded = Collections.singletonList(buffer.removeLast());
            }

            buffer.addLast(element);
            return discarded;
        }
    },

    /**
     * Keeps only the oldest onNext value, overwriting any previous value if the
     * downstream can't keep up.
     */
    OLDEST {
        @Override
        public <T> Collection<T> update(Deque<T> buffer, T element) {
            Collection<T> discarded = Collections.emptySet();
            if (buffer.size() > 0) {
                discarded = Collections.singletonList(buffer.removeFirst());
            }

            buffer.addLast(element);
            return discarded;
        }
    };

    /**
     * Updates the buffer and returns a list of discarded elements (if any).
     *
     * @param buffer the buffer to update
     * @param element the elment that should possibly be inserted
     * @param <T> the generic type of the element
     * @return the list of discarded elements
     */
    public abstract <T> Collection<T> update(Deque<T> buffer, T element);

}
