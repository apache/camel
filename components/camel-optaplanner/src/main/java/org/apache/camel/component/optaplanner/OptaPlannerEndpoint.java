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

import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.optaplanner.core.api.domain.solution.Solution;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;

/**
 * OptaPlanner endpoint for Camel
 */
@UriEndpoint(scheme = "optaplanner", title = "OptaPlanner", syntax = "optaplanner:resourceUri", producerOnly = true, label = "engine,planning")
public class OptaPlannerEndpoint extends ResourceEndpoint {

    private SolverFactory solverFactory;

    public OptaPlannerEndpoint() {
    }

    public OptaPlannerEndpoint(String uri, Component component, String resourceUri) {
        super(uri, component, resourceUri);
    }

    public SolverFactory getSolverFactory() {
        return solverFactory;
    }

    public void setSolverFactory(SolverFactory solverFactory) {
        this.solverFactory = solverFactory;
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    protected String createEndpointUri() {
        return "optaplanner:" + getResourceUri();
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        ObjectHelper.notNull(solverFactory, "solverFactory");
        Solver solver = solverFactory.buildSolver();

        Solution planningProblem = exchange.getIn().getMandatoryBody(Solution.class);

        solver.solve(planningProblem);
        Solution bestSolution = solver.getBestSolution();

        exchange.getOut().setBody(bestSolution);
        // propagate headers and attachments
        exchange.getOut().setHeaders(exchange.getIn().getHeaders());
        exchange.getOut().setAttachments(exchange.getIn().getAttachments());
    }

}
