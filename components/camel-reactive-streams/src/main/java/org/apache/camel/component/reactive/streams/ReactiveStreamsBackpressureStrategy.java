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
        public <T> Collection<T> update(Deque<T> buffer, T newItem) {
            // always buffer
            buffer.addLast(newItem);
            // never discard
            return Collections.emptySet();
        }
    },

    /**
     * Keeps only the oldest onNext value, discarding any future value
     * until it's consumed by the downstream subscriber.
     */
    OLDEST {
        @Override
        public <T> Collection<T> update(Deque<T> buffer, T newItem) {
            if (buffer.size() > 0) {
                // the buffer has another item, so discarding the incoming one
                return Collections.singletonList(newItem);
            } else {
                // add the new item to the buffer, since it was empty
                buffer.addLast(newItem);
                // nothing is discarded
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
        public <T> Collection<T> update(Deque<T> buffer, T newItem) {
            Collection<T> discarded = Collections.emptySet();
            if (buffer.size() > 0) {
                // there should be an item in the buffer,
                // so removing it to overwrite
                discarded = Collections.singletonList(buffer.removeFirst());
            }
            // add the new item to the buffer
            // (it should be the only item in the buffer now)
            buffer.addLast(newItem);
            return discarded;
        }
    };

    /**
     * Updates the buffer and returns a list of discarded elements (if any).
     *
     * @param buffer the buffer to update
     * @param newItem the elment that should possibly be inserted
     * @param <T> the generic type of the element
     * @return the list of discarded elements
     */
    public abstract <T> Collection<T> update(Deque<T> buffer, T newItem);

}
