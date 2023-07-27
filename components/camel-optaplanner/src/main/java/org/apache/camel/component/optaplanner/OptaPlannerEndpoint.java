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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;

/**
 * Solve planning problems with OptaPlanner.
 */
@UriEndpoint(firstVersion = "2.13.0", scheme = "optaplanner", title = "OptaPlanner", syntax = "optaplanner:problemName",
             category = { Category.WORKFLOW }, headersClass = OptaPlannerConstants.class)
public class OptaPlannerEndpoint extends DefaultEndpoint {
    private static final Map<String, Solver<Object>> SOLVERS = new HashMap<>();
    private static final Map<Long, Set<OptaplannerSolutionEventListener>> SOLUTION_LISTENER = new HashMap<>();

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
        synchronized (SOLVERS) {
            return SOLVERS.computeIfAbsent(solverId, k -> createSolver());
        }
    }

    protected Solver<Object> createSolver() {
        ClassLoader classLoader = getCamelContext().getApplicationContextClassLoader();
        SolverFactory<Object> solverFactory = SolverFactory.createFromXmlResource(configuration.getConfigFile(), classLoader);
        return solverFactory.buildSolver();
    }

    protected Solver<Object> getSolver(String solverId) {
        synchronized (SOLVERS) {
            return SOLVERS.get(solverId);
        }
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
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        synchronized (SOLVERS) {
            for (Map.Entry<String, Solver<Object>> solver : SOLVERS.entrySet()) {
                solver.getValue().terminateEarly();
                SOLVERS.remove(solver.getKey());
            }
        }
        super.doStop();
    }

    protected Set<OptaplannerSolutionEventListener> getSolutionEventListeners(Long problemId) {
        return SOLUTION_LISTENER.get(problemId);
    }

    protected synchronized void addSolutionEventListener(Long problemId, OptaplannerSolutionEventListener listener) {
        Set<OptaplannerSolutionEventListener> listeners = SOLUTION_LISTENER.get(problemId);
        if (listeners == null) {
            listeners = new HashSet<>();
            listeners.add(listener);
            SOLUTION_LISTENER.put(problemId, listeners);
        } else {
            listeners.add(listener);
        }
    }

    protected synchronized void removeSolutionEventListener(Long problemId, OptaplannerSolutionEventListener listener) {
        Set<OptaplannerSolutionEventListener> listeners = SOLUTION_LISTENER.get(problemId);
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            SOLUTION_LISTENER.remove(problemId);
        }
    }
}
