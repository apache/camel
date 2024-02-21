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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.SolverManagerConfig;
import org.optaplanner.examples.cloudbalancing.domain.CloudBalance;
import org.optaplanner.examples.cloudbalancing.domain.CloudComputer;
import org.optaplanner.examples.cloudbalancing.persistence.CloudBalancingGenerator;
import org.optaplanner.examples.cloudbalancing.swingui.realtime.DeleteComputerProblemChange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.optaplanner.core.api.solver.SolverStatus.NOT_SOLVING;

/**
 * OptaPlanner unit test with Camel
 */
public class OptaPlannerProblemChangeTest extends CamelTestSupport {

    @Test
    public void testAsynchronousProblemSolving() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");

        CloudBalancingGenerator generator = new CloudBalancingGenerator(true);
        final CloudBalance planningProblem = generator.createCloudBalance(4, 12);
        assertNull(planningProblem.getScore());
        assertNull(planningProblem.getProcessList().get(0).getComputer());

        ClassLoader classLoader = this.context().getApplicationContextClassLoader();
        SolverConfig solverConfig
                = SolverConfig.createFromXmlResource("org/apache/camel/component/optaplanner/solverConfig.xml", classLoader);
        SolverFactory solverFactory = SolverFactory.create(solverConfig);
        SolverManager solverManager = SolverManager.create(solverFactory, new SolverManagerConfig());

        CompletableFuture<CloudBalance> solution = template.asyncRequestBodyAndHeader("direct:in", planningProblem,
                OptaPlannerConstants.SOLVER_MANAGER, solverManager,
                CloudBalance.class);

        CloudComputer firstComputer = planningProblem.getComputerList().get(0);
        assertNotNull(firstComputer);

        // wait for Solver to be at least scheduled or started
        Awaitility.with()
                .pollInterval(10, TimeUnit.MILLISECONDS).atMost(1000, TimeUnit.MILLISECONDS)
                .until(() -> !NOT_SOLVING.equals(solverManager.getSolverStatus(1L)));

        // update the Problem
        template.requestBodyAndHeader("direct:in", new DeleteComputerProblemChange(firstComputer),
                OptaPlannerConstants.SOLVER_MANAGER, solverManager);
        mockEndpoint.assertIsSatisfied();

        // wait for the solution
        CloudBalance bestSolution = solution.get();
        // check the solution has been changed
        assertEquals(3, bestSolution.getComputerList().size());
        assertEquals(12, bestSolution.getProcessList().size());
        assertNotNull(bestSolution.getScore());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in").to("optaplanner:cloudBalance?async=true");

                from("optaplanner:cloudBalance")
                        .to("mock:result");
            }
        };
    }

}
