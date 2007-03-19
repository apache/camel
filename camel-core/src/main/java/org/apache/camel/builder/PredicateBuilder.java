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
package org.apache.camel.builder;

import org.apache.camel.Predicate;
import org.apache.camel.Exchange;
import org.apache.camel.Predicates;
import org.apache.camel.Processor;
import org.apache.camel.FilterProcessor;

/**
 * @version $Revision$
 */
public class PredicateBuilder<E extends Exchange> extends DestinationBuilder<E> {
    private Predicate<E> predicate;

    public PredicateBuilder(DestinationBuilder<E> builder, Predicate<E> predicate) {
        super(builder);
        this.predicate = predicate;
    }

    /**
     * Adds another predicate using a logican AND
     */
    public PredicateBuilder<E> and(Predicate<E> predicate) {
        this.predicate = Predicates.and(this.predicate, predicate);
        return this;
    }

    /**
     * Adds another predicate using a logican OR
     */
    public PredicateBuilder<E> or(Predicate<E> predicate) {
        this.predicate = Predicates.or(this.predicate, predicate);
        return this;
    }

    public Predicate<E> getPredicate() {
        return predicate;
    }

    public FilterProcessor<E> createProcessor() {
        // lets create a single processor for all child predicates
        Processor<E> childProcessor = super.createProcessor();
        return new FilterProcessor<E>(predicate, childProcessor);
    }

    public void addProcessor(Processor<E> processor) {
        getParent().addProcessor(new FilterProcessor<E>(predicate, processor));
    }
}
