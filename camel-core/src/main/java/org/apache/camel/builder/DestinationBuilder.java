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

import org.apache.camel.CompositeProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;

import java.util.ArrayList;
import java.util.List;

/**
 * @version $Revision$
 */
public class DestinationBuilder<E extends Exchange> implements ProcessorBuilder<E> {
    private DestinationBuilder<E> parent;
    private RouteBuilder<E> builder;
    private Endpoint<E> from;
    private List<Processor<E>> processors = new ArrayList<Processor<E>>();
    private List<ProcessorBuilder<E>> processBuilders = new ArrayList<ProcessorBuilder<E>>();

    public DestinationBuilder(RouteBuilder<E> builder, Endpoint<E> from) {
        this.builder = builder;
        this.from = from;
        this.parent = this;
    }

    public DestinationBuilder(DestinationBuilder<E> parent) {
        this.parent = parent;
        this.builder = parent.getBuilder();
        this.from = parent.getFrom();
    }

    /**
     * Resolves the given URI to an endpoint
     */
    public Endpoint<E> endpoint(String uri) {
        return getBuilder().endpoint(uri);
    }

    public DestinationBuilder<E> getParent() {
        return parent;
    }

    /**
     * Sends the exchange to the given endpoint URI
     */
    public ProcessorBuilder<E> to(String uri) {
        return to(endpoint(uri));
    }

    /**
     * Sends the exchange to the given endpoint
     */
    public ProcessorBuilder<E> to(Endpoint<E> endpoint) {
        ConfiguredDestinationBuilder<E> answer = new ConfiguredDestinationBuilder<E>(this, endpoint);
        addProcessBuilder(answer);
        return answer;
    }


    /**
     * Adds the custom processor to this destination
     */
    public ProcessorBuilder<E> process(Processor<E> processor) {
        ConstantProcessorBuilder<E> answer = new ConstantProcessorBuilder<E>(processor);
        addProcessBuilder(answer);
        return answer;
    }



    /**
     * Creates a predicate which is applied and only if it is true then
     * the exchange is forwarded to the destination
     *
     * @return the builder for a predicate
     */
    public PredicateBuilder<E> filter(Predicate<E> predicate) {
        PredicateBuilder<E> answer = new PredicateBuilder<E>(this, predicate);
        addProcessBuilder(answer);
        return answer;
    }

    /**
     * Creates a choice of one or more predicates with an otherwise clause
     *
     * @return the builder for a choice expression
     */
    public ChoiceBuilder<E> choice() {
        ChoiceBuilder<E> answer = new ChoiceBuilder<E>(this);
        addProcessBuilder(answer);
        return answer;
    }

    public RouteBuilder<E> getBuilder() {
        return builder;
    }

    public Endpoint<E> getFrom() {
        return from;
    }

    public void addProcessBuilder(ProcessorBuilder<E> processBuilder) {
        processBuilders.add(processBuilder);
    }

    public void addProcessor(Processor<E> processor) {
        processors.add(processor);
    }

    public Processor<E> createProcessor() {
        List<Processor<E>> answer = new ArrayList<Processor<E>>();

        for (ProcessorBuilder<E> processBuilder : processBuilders) {
            Processor<E> processor = processBuilder.createProcessor();
            if (processor == null) {
                throw new IllegalArgumentException("No processor created for processBuilder: " + processBuilder);
            }
            answer.add(processor);
        }
        if (answer.size() == 0) {
            return null;
        }
        if (answer.size() == 1) {
            return answer.get(0);
        }
        else {
            return new CompositeProcessor<E>(answer);
        }
    }

    public List<Processor<E>> getProcessors() {
        return processors;
    }
}
