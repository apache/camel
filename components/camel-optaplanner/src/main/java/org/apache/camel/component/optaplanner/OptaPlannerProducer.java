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

import java.util.concurrent.ExecutorService;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.Solution;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.impl.solver.ProblemFactChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptaPlannerProducer extends DefaultProducer {

    private static final transient Logger LOGGER = LoggerFactory.getLogger(OptaPlannerProducer.class);

    private ExecutorService executor;
    private final OptaPlannerEndpoint endpoint;
    private final OptaPlannerConfiguration configuration;

    public OptaPlannerProducer(OptaPlannerEndpoint endpoint, OptaPlannerConfiguration configuration) {
        super(endpoint);
        this.endpoint = endpoint;
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        if (configuration.isAsync()) {
            executor = endpoint.getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, endpoint.getEndpointUri(), configuration.getThreadPoolSize());
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (executor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdown(executor);
            executor = null;
        }
        super.doStop();
    }

    @SuppressWarnings("unchecked")
    @Override
    public synchronized void process(Exchange exchange) throws Exception {
        final Object body = exchange.getIn().getMandatoryBody();
        final String solverId = getSolverId(exchange);

        /*
         * Keep for backward compatibility untill optaplanner version 8.0.0 not
         * released After that the code '|| body instanceof Solution' need to be
         * removed
         */
        if (body.getClass().isAnnotationPresent(PlanningSolution.class) || body instanceof Solution) {
            if (isAsync(exchange)) {
                LOGGER.debug("Asynchronously solving problem: [{}] with id [{}]", body, solverId);
                final Solver<Object> solver = endpoint.getOrCreateSolver(solverId);
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            solver.solve(body);
                        } catch (Throwable e) {
                            LOGGER.error("Asynchronously solving failed for solverId ({})", solverId, e);
                        }
                    }
                });
            } else {
                LOGGER.debug("Synchronously solving problem: [{}] with id [{}]", body, solverId);
                Solver<Object> solver = endpoint.getSolver(solverId);
                if (solver == null) {
                    solver = endpoint.createSolver();
                }
                solver.solve(body);
                populateResult(exchange, solver);
            }
        } else if (body instanceof ProblemFactChange) {
            LOGGER.debug("Adding ProblemFactChange to solver: [{}] with id [{}]", body, solverId);
            Solver<Object> solver = endpoint.getOrCreateSolver(solverId);
            solver.addProblemFactChange((ProblemFactChange<Object>)body);
            if (!isAsync(exchange)) {
                while (!solver.isEveryProblemFactChangeProcessed()) {
                    Thread.sleep(OptaPlannerConstants.IS_EVERY_PROBLEM_FACT_CHANGE_DELAY);
                }
            }
            populateResult(exchange, solver);
        } else {
            LOGGER.debug("Retrieving best score for solver: [{}]", solverId);
            Solver<Object> solver = endpoint.getSolver(solverId);
            if (solver == null) {
                throw new RuntimeException("Solver not found: " + solverId);
            }
            populateResult(exchange, solver);
        }
    }

    private void populateResult(Exchange exchange, Solver<Object> solver) {
        exchange.getIn().setBody(solver.getBestSolution());
        exchange.getIn().setHeader(OptaPlannerConstants.TIME_SPENT, solver.getTimeMillisSpent());
        exchange.getIn().setHeader(OptaPlannerConstants.IS_EVERY_PROBLEM_FACT_CHANGE_PROCESSED, solver.isEveryProblemFactChangeProcessed());
        exchange.getIn().setHeader(OptaPlannerConstants.IS_TERMINATE_EARLY, solver.isTerminateEarly());
        exchange.getIn().setHeader(OptaPlannerConstants.IS_SOLVING, solver.isSolving());
    }

    private String getSolverId(Exchange exchange) throws Exception {
        String solverId = exchange.getIn().getHeader(OptaPlannerConstants.SOLVER_ID, String.class);
        if (solverId == null) {
            solverId = configuration.getSolverId();
        }
        LOGGER.debug("SolverId: [{}]", solverId);
        return solverId;
    }

    private boolean isAsync(Exchange exchange) {
        Boolean isAsync = exchange.getIn().getHeader(OptaPlannerConstants.IS_ASYNC, Boolean.class);
        return isAsync != null ? isAsync : configuration.isAsync();
    }
}
