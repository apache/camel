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

import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.ProcessorBuilder;

import java.util.List;
import java.util.ArrayList;

/**
 * @version $Revision$
 */
public class DestinationBuilder<E extends Exchange> implements ProcessorBuilder<E> {
    private RouteBuilder<E> builder;
    private Endpoint<E> from;
    private List<ProcessorBuilder<E>> processBuilders = new ArrayList<ProcessorBuilder<E>>();

    public DestinationBuilder(DestinationBuilder<E> parent) {
        this(parent.getBuilder(), parent.getFrom());
    }

    public DestinationBuilder(RouteBuilder builder, Endpoint<E> from) {
        this.builder = builder;
        this.from = from;
    }

    /**
     * Resolves the given URI to an endpoint
     */
    public Endpoint<E> endpoint(String uri) {
        return getBuilder().endpoint(uri);
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
        builder.addRoute(answer);
        return answer;
    }

    /**
     * Creates a predicate which is applied and only if it is true then
     * the exchange is forwarded to the destination
     */
    public PredicateBuilder<E> filter(Predicate predicate) {
        return new PredicateBuilder<E>(this, predicate);
    }

    /**
     * Creates a choice of one or more predicates with an otherwise clause
     */
    public ChoiceBuilder<E> choice() {
        return new ChoiceBuilder<E>(this);
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

    public Processor<E> createProcessor() {
        throw new UndefinedDestinationException();
    }
}
