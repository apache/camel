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

import org.apache.camel.CamelContext;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.processor.FilterProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class serves as a wrapper around a {@link FilterProcessor} to include an integer representing the priority of
 * this processor, and a {@link Comparator} to sort by priority, then by id.
 */
public class PrioritizedFilterProcessor extends FilterProcessor implements Comparable<PrioritizedFilterProcessor> {

    /**
     * A comparator to sort {@link PrioritizedFilterProcessor}s by their priority field.
     */
    public static final Comparator<PrioritizedFilterProcessor> COMPARATOR = Comparator
            .comparingInt(PrioritizedFilterProcessor::getPriority)
            .thenComparing(PrioritizedFilterProcessor::getId);

    private static final Logger LOG = LoggerFactory.getLogger(PrioritizedFilterProcessor.class);

    /**
     * The priority value of this processor.
     */
    private final int priority;

    /**
     * Create this processor with all properties.
     *
     * @param id        the identifier
     * @param priority  the priority of this processor
     * @param context   the camel context
     * @param predicate the rule expression
     * @param processor the processor to invoke if the predicate matches
     */
    public PrioritizedFilterProcessor(
                                      final String id,
                                      final int priority,
                                      final CamelContext context,
                                      final Predicate predicate,
                                      final Processor processor) {
        super(context, predicate, processor);
        this.setId(id);
        this.priority = priority;
        LOG.debug("Created Dynamic Router Prioritized Filter Processor");
    }

    /**
     * Get the processor priority.
     *
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Compare the priority of this instance to the priority of the parameter.
     *
     * @param  other the processor to compare with
     * @return       the result of the priority comparison
     */
    @Override
    public int compareTo(final PrioritizedFilterProcessor other) {
        return COMPARATOR.compare(this, other);
    }

    @Override
    public String toString() {
        return String.format("PrioritizedFilterProcessor [id: %s, priority: %s, predicate: %s]",
                this.getId(), this.getPriority(), this.getPredicate());
    }

    /**
     * Create a {@link PrioritizedFilterProcessor} instance.
     */
    public static class PrioritizedFilterProcessorFactory {

        /**
         * Create this processor with all properties.
         *
         * @param id        the identifier
         * @param priority  the priority of this processor
         * @param context   the camel context
         * @param predicate the rule expression
         * @param processor the processor to invoke if the predicate matches
         */
        public PrioritizedFilterProcessor getInstance(
                final String id,
                final int priority,
                final CamelContext context,
                final Predicate predicate,
                final Processor processor) {
            return new PrioritizedFilterProcessor(id, priority, context, predicate, processor);
        }
    }
}
