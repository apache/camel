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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultAsyncProducer;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.SolverJob;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.api.solver.change.ProblemChange;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.SolverManagerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptaPlannerProducer extends DefaultAsyncProducer {

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
            executor = endpoint.getCamelContext().getExecutorServiceManager().newFixedThreadPool(this,
                    endpoint.getEndpointUri(), configuration.getThreadPoolSize());
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

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            final Object body = exchange.getIn().getMandatoryBody();
            // using Solver Manager :: Optaplanner creates the Solver under the hood
            final SolverManager solverManager = getSolverManager(exchange);
            final String solverId = getSolverId(exchange);

            Long problemId = endpoint.getConfiguration().getProblemId();
            LOGGER.debug("Asynchronously solving problem: [{}] with id [{}]", body, problemId);

            if (body instanceof ProblemChange<?>) {
                solverManager.addProblemChange(problemId, (ProblemChange) body);
            } else if (isAsync(exchange)) {
                executor.submit(() -> {
                    try {
                        // create a consumer for best solution
                        OptaplannerEventSupport eventSupport = new OptaplannerEventSupport(endpoint, problemId);
                        // start solving :: Solver Job is a thread
                        SolverJob solverJob
                                = solverManager.solveAndListen(problemId, t -> body, eventSupport::updateBestSolution);

                        // wait for result
                        populateResult(exchange, solverJob);
                    } catch (Exception e) {
                        exchange.setException(e);
                    } finally {
                        callback.done(false);
                    }
                });
                return false;
            } else {
                // no need for a consumer for sync call
                SolverJob solverJob = solverManager.solve(problemId, body);
                // wait for result
                populateResult(exchange, solverJob);
            }

            // synchronous or wrong type of body
            callback.done(true);
            return true;
        } catch (Exception e) {
            exchange.setException(e);
        }
        callback.done(true);
        return true;
    }

    private void populateResult(Exchange exchange, SolverJob<?, ?> solverJob)
            throws InterruptedException, ExecutionException {
        exchange.getIn().setBody(solverJob.getFinalBestSolution());
        exchange.getIn().setHeader(OptaPlannerConstants.IS_SOLVING, false);
    }

    private String getSolverId(Exchange exchange) {
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

    private SolverManager<?, Long> getSolverManager(Exchange exchange) {
        // prioritize the solverManager from configuration
        if (configuration.getSolverManager() != null) {
            return configuration.getSolverManager();
        }
        // if no Solver Manager, check on headers
        var solverManager = exchange.getIn().getHeader(OptaPlannerConstants.SOLVER_MANAGER, SolverManager.class);
        // if no SolverManager, check if configFile exists and create one based on it
        if (solverManager == null) {
            if (configuration.getConfigFile() == null) {
                return null;
            }
            SolverConfig solverConfig
                    = SolverConfig.createFromXmlResource(configuration.getConfigFile());
            SolverFactory<?> solverFactory = SolverFactory.create(solverConfig);
            solverManager = SolverManager.create(solverFactory, new SolverManagerConfig());
        }
        return solverManager;
    }

}
