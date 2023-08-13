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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class serves as a wrapper around a {@link FilterProcessor} to include an integer representing the priority of
 * this processor, and a {@link Comparator} to sort by priority, then by id.
 */
public class PrioritizedFilter implements Comparable<PrioritizedFilter> {

    /**
     * A comparator to sort {@link PrioritizedFilter}s by their priority field.
     */
    public static final Comparator<PrioritizedFilter> COMPARATOR = Comparator
            .comparingInt(PrioritizedFilter::getPriority)
            .thenComparing(PrioritizedFilter::getId);

    private static final Logger LOG = LoggerFactory.getLogger(PrioritizedFilter.class);

    /**
     * The priority value of this processor.
     */
    private final int priority;

    /**
     * The identifier for this prioritized filter.
     */
    private final String id;

    private final Predicate predicate;

    private final String endpoint;

    /**
     * Create this processor with all properties.
     *
     * @param id        the identifier
     * @param priority  the priority of this processor
     * @param predicate the rule expression
     * @param endpoint  the destination endpoint for matching exchanges
     */
    public PrioritizedFilter(final String id,
                             final int priority,
                             final Predicate predicate,
                             final String endpoint) {
        this.id = id;
        this.priority = priority;
        this.predicate = predicate;
        this.endpoint = endpoint;
        LOG.debug("Created Dynamic Router Prioritized Filter Processor");
    }

    /**
     * Get the filter priority.
     *
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Get the filter id.
     *
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * Get the filter predicate.
     *
     * @return the predicate
     */
    public Predicate getPredicate() {
        return predicate;
    }

    /**
     * Get the filter endpoint.
     *
     * @return the endpoint
     */
    public String getEndpoint() {
        return endpoint;
    }

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
                this.getId(), this.getPriority(), this.getPredicate());
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
