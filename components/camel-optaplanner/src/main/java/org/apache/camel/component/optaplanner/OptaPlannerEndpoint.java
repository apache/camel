/**
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
import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;

/**
 * Solves the planning problem contained in a message with OptaPlanner.
 */
@UriEndpoint(firstVersion = "2.13.0", scheme = "optaplanner", title = "OptaPlanner", syntax = "optaplanner:configFile", label = "engine,planning")
public class OptaPlannerEndpoint extends DefaultEndpoint {
    private static final Map<String, Solver<Object>> SOLVERS = new HashMap<String, Solver<Object>>();

    @UriParam
    private OptaPlannerConfiguration configuration;
    private SolverFactory<Object> solverFactory;

    public OptaPlannerEndpoint() {
    }

    public OptaPlannerEndpoint(String uri, Component component, OptaPlannerConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
        ClassLoader classLoader = getCamelContext().getApplicationContextClassLoader();
        solverFactory = SolverFactory.createFromXmlResource(configuration.getConfigFile(), classLoader);
    }

    protected Solver<Object> getOrCreateSolver(String solverId) throws Exception {
        synchronized (SOLVERS) {
            Solver<Object> solver = SOLVERS.get(solverId);
            if (solver == null) {
                solver = createSolver();
                SOLVERS.put(solverId, solver);
            }
            return solver;
        }
    }

    protected Solver<Object> createSolver() {
        return solverFactory.buildSolver();
    }

    protected Solver<Object> getSolver(String solverId) {
        synchronized (SOLVERS) {
            return SOLVERS.get(solverId);
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        return new OptaPlannerProducer(this, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new OptaPlannerConsumer(this, processor, configuration);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    protected void doStop() throws Exception {
        synchronized (SOLVERS) {
            for (Solver<Object> solver : SOLVERS.values()) {
                solver.terminateEarly();
                SOLVERS.remove(solver);
            }
        }
        super.doStop();
    }
}
