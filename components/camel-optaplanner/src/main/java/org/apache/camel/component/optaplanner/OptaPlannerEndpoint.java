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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.SolverManagerConfig;

/**
 * Solve planning problems with OptaPlanner.
 */
@UriEndpoint(firstVersion = "2.13.0", scheme = "optaplanner", title = "OptaPlanner", syntax = "optaplanner:problemName",
             category = { Category.WORKFLOW }, headersClass = OptaPlannerConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.NULL,
                                   description = "The body is empty: the new best solution is in the CamelOptaPlannerBestSolution"
                                                 + " header. Events come from the producers of the same CamelContext that solve"
                                                 + " a problem with the same problemId"),
           producerIn = @BodyType(value = Carrier.POJO,
                                  description = "The planning problem: an instance of the PlanningSolution class of the solver"
                                                + " configuration, or a ProblemChange to apply to the problem being solved. Any"
                                                + " other body fails to solve, and no body fails"),
           producerOut = @BodyType(value = { Carrier.POJO, Carrier.UNCHANGED },
                                   description = "The best solution found, a new instance of the problem class, with the"
                                                 + " CamelOptaPlannerIsSolving header false (also with async, the exchange waits"
                                                 + " for it); a ProblemChange leaves the body unchanged"))
public class OptaPlannerEndpoint extends DefaultEndpoint {
    private final Map<String, Solver<Object>> solvers = new ConcurrentHashMap<>();
    private final Lock solverManagerLock = new ReentrantLock();
    // the SolverManager created from the configFile, which this endpoint closes when it stops
    private volatile SolverManager<?, Long> solverManager;

    @UriParam
    private OptaPlannerConfiguration configuration;

    public OptaPlannerEndpoint(String uri, Component component, OptaPlannerConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public OptaPlannerConfiguration getConfiguration() {
        return configuration;
    }

    protected Solver<Object> getOrCreateSolver(String solverId) {
        return solvers.computeIfAbsent(solverId, k -> createSolver());
    }

    protected Solver<Object> createSolver() {
        ClassLoader classLoader = getCamelContext().getApplicationContextClassLoader();
        SolverFactory<Object> solverFactory = SolverFactory.createFromXmlResource(configuration.getConfigFile(), classLoader);
        return solverFactory.buildSolver();
    }

    protected Solver<Object> getSolver(String solverId) {
        return solvers.get(solverId);
    }

    /**
     * Gets the SolverManager created from the configFile, creating it on first use. It is shared by the exchanges of
     * this endpoint, and closed when the endpoint stops.
     */
    protected SolverManager<?, Long> getOrCreateSolverManager() {
        SolverManager<?, Long> answer = solverManager;
        if (answer == null) {
            solverManagerLock.lock();
            try {
                answer = solverManager;
                if (answer == null) {
                    SolverConfig solverConfig = SolverConfig.createFromXmlResource(configuration.getConfigFile());
                    SolverFactory<Object> solverFactory = SolverFactory.create(solverConfig);
                    answer = SolverManager.create(solverFactory, new SolverManagerConfig());
                    solverManager = answer;
                }
            } finally {
                solverManagerLock.unlock();
            }
        }
        return answer;
    }

    @Override
    public Producer createProducer() {
        return new OptaPlannerProducer(this, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        OptaPlannerConsumer consumer = new OptaPlannerConsumer(this, processor, configuration);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected void doStop() throws Exception {
        for (Map.Entry<String, Solver<Object>> solver : solvers.entrySet()) {
            solver.getValue().terminateEarly();
            solvers.remove(solver.getKey());
        }
        solverManagerLock.lock();
        try {
            if (solverManager != null) {
                solverManager.close();
                solverManager = null;
            }
        } finally {
            solverManagerLock.unlock();
        }
        super.doStop();
    }

    @Override
    public OptaPlannerComponent getComponent() {
        return (OptaPlannerComponent) super.getComponent();
    }

    // the listeners are kept on the component, so a consumer gets the events of the producers of its CamelContext only

    protected Set<OptaplannerSolutionEventListener> getSolutionEventListeners(Long problemId) {
        return getComponent().getSolutionEventListeners(problemId);
    }

    protected void addSolutionEventListener(Long problemId, OptaplannerSolutionEventListener listener) {
        getComponent().addSolutionEventListener(problemId, listener);
    }

    protected void removeSolutionEventListener(Long problemId, OptaplannerSolutionEventListener listener) {
        getComponent().removeSolutionEventListener(problemId, listener);
    }
}
