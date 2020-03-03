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
package org.apache.camel.component.optaplanner;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.event.BestSolutionChangedEvent;
import org.optaplanner.core.api.solver.event.SolverEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OptaPlanner component for Camel
 */
public class OptaPlannerConsumer extends DefaultConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(OptaPlannerConsumer.class);
    private final OptaPlannerEndpoint endpoint;
    private final OptaPlannerConfiguration configuration;
    private final SolverEventListener<Object> listener;

    public OptaPlannerConsumer(OptaPlannerEndpoint endpoint, Processor processor, OptaPlannerConfiguration configuration) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.configuration = configuration;
        listener = new SolverEventListener<Object>() {
            @Override
            public void bestSolutionChanged(BestSolutionChangedEvent<Object> event) {
                if (event.isEveryProblemFactChangeProcessed() && event.getNewBestScore().isSolutionInitialized()) {
                    processEvent(event);
                }
            }
        };
    }

    public void processEvent(BestSolutionChangedEvent<Object> event) {
        Exchange exchange = getEndpoint().createExchange();
        exchange.getOut().setHeader(OptaPlannerConstants.BEST_SOLUTION, event.getNewBestSolution());
        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            LOG.error("Error processing event ", e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        Solver<Object> solver = endpoint.getOrCreateSolver(configuration.getSolverId());
        solver.addEventListener(listener);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        Solver<Object> solver = endpoint.getOrCreateSolver(configuration.getSolverId());
        solver.removeEventListener(listener);
        super.doStop();
    }
}
