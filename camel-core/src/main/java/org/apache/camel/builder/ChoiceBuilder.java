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

import org.apache.camel.processor.ChoiceProcessor;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.Predicate;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.List;
import java.util.ArrayList;

/**
 * @version $Revision$
 */
public class ChoiceBuilder<E extends Exchange> extends DestinationBuilder<E> {

    private final DestinationBuilder<E> parent;
    private List<ChoicePredicateBuilder<E>> predicateBuilders = new ArrayList<ChoicePredicateBuilder<E>>();
    private DestinationBuilder<E> otherwise;

    public ChoiceBuilder(DestinationBuilder<E> parent) {
        super(parent);
        this.parent = parent;
    }

    /**
     * Adds a predicate which if it is true then the message exchange is sent to the given destination
     *
     * @return a builder for creating a when predicate clause and action
     */
    public ChoicePredicateBuilder<E> when(Predicate<E> predicate) {
        ChoicePredicateBuilder<E> answer = new ChoicePredicateBuilder<E>(this, predicate);
        predicateBuilders.add(answer);
        return answer;
    }

    public DestinationBuilder<E> otherwise() {
        this.otherwise = new DestinationBuilder<E>(parent);
        return otherwise;
    }

    public List<ChoicePredicateBuilder<E>> getPredicateBuilders() {
        return predicateBuilders;
    }

    public DestinationBuilder<E> getOtherwise() {
        return otherwise;
    }

    @Override
    public Processor<E> createProcessor() {
        List<FilterProcessor<E>> filters = new ArrayList<FilterProcessor<E>>();
        for (ChoicePredicateBuilder<E> predicateBuilder : predicateBuilders) {
            filters.add(predicateBuilder.createProcessor());
        }
        Processor<E> otherwiseProcessor = null;
        if (otherwise != null) {
            otherwiseProcessor = otherwise.createProcessor();
        }
        return new ChoiceProcessor<E>(filters, otherwiseProcessor);
    }
}
