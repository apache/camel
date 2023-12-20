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
package org.apache.camel.component.dynamicrouter;

import java.util.Comparator;

import org.apache.camel.Predicate;
import org.apache.camel.processor.FilterProcessor;

/**
 * This class serves as a wrapper around a {@link FilterProcessor} to include an integer representing the priority of
 * this processor, and a {@link Comparator} to sort by priority, then by id.
 *
 * @param id        The identifier for this prioritized filter.
 * @param priority  The priority value of this processor.
 * @param predicate the rule expression
 * @param endpoint  the destination endpoint for matching exchanges
 */
public record PrioritizedFilter(String id, int priority, Predicate predicate, String endpoint)
        implements
            Comparable<PrioritizedFilter> {

    /**
     * A comparator to sort {@link PrioritizedFilter}s by their priority field.
     */
    public static final Comparator<PrioritizedFilter> COMPARATOR = Comparator
            .comparingInt(PrioritizedFilter::priority)
            .thenComparing(PrioritizedFilter::id);

    /**
     * Compare the priority of this instance to the priority of the parameter.
     *
     * @param  other the processor to compare with
     * @return       the result of the priority comparison
     */
    @Override
    public int compareTo(final PrioritizedFilter other) {
        return COMPARATOR.compare(this, other);
    }

    @Override
    public String toString() {
        return String.format("PrioritizedFilterProcessor [id: %s, priority: %s, predicate: %s]",
                this.id(), this.priority(), this.predicate());
    }

    /**
     * Create a {@link PrioritizedFilter} instance.
     */
    public static class PrioritizedFilterFactory {

        /**
         * Create this processor with all properties.
         *
         * @param id        the identifier
         * @param priority  the priority of this processor
         * @param predicate the rule expression
         * @param endpoint  the destination endpoint for matching exchanges
         */
        public PrioritizedFilter getInstance(
                final String id,
                final int priority,
                final Predicate predicate,
                final String endpoint) {
            return new PrioritizedFilter(id, priority, predicate, endpoint);
        }
    }
}
