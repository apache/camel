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
import java.util.concurrent.ExecutionException;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.SolverManagerConfig;
import org.optaplanner.examples.cloudbalancing.domain.CloudBalance;
import org.optaplanner.examples.cloudbalancing.persistence.CloudBalancingGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OptaplannerSolverManagerAsyncTest extends CamelTestSupport {

    private static SolverManager solverManager;

    @BeforeEach
    public void beforeEach() {
        ClassLoader classLoader = this.context().getApplicationContextClassLoader();
        SolverConfig solverConfig
                = SolverConfig.createFromXmlResource("org/apache/camel/component/optaplanner/solverConfig.xml", classLoader);
        SolverFactory solverFactory = SolverFactory.create(solverConfig);
        solverManager = SolverManager.create(solverFactory, new SolverManagerConfig());
    }

    @Test
    public void testWithSolverManagerInHeader() throws Exception {
        final CloudBalance planningProblem = getCloudBalance();

        CompletableFuture<CloudBalance> solution
                = template.asyncRequestBodyAndHeader("optaplanner:doesntmatter?async=true", planningProblem,
                        OptaPlannerConstants.SOLVER_MANAGER, solverManager,
                        CloudBalance.class);

        assertResults(solution);
    }

    @Test
    public void testWithSolverManagerBinded() throws Exception {
        final CloudBalance planningProblem = getCloudBalance();

        this.context.getRegistry().bind("mySolverManager", solverManager);

        CompletableFuture<CloudBalance> solution = template.asyncRequestBody(
                "optaplanner:doesntmatter?async=true&solverManager=#mySolverManager", planningProblem,
                CloudBalance.class);

        assertResults(solution);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                from("optaplanner:doesntmatter")
                        .to("mock:result");
            }
        };
    }

    private void assertResults(CompletableFuture<CloudBalance> solution) throws InterruptedException, ExecutionException {
        // consumer
        getMockEndpoint("mock:result").assertIsSatisfied();

        // producer
        CloudBalance bestSolution = solution.get();
        assertEquals(4, bestSolution.getComputerList().size());
        assertEquals(12, bestSolution.getProcessList().size());
        assertNotNull(bestSolution.getScore());
        assertTrue(bestSolution.getScore().isFeasible());
        assertNotNull(bestSolution.getProcessList().get(0).getComputer());
    }

    private CloudBalance getCloudBalance() {
        getMockEndpoint("mock:result").setExpectedCount(1);

        CloudBalancingGenerator generator = new CloudBalancingGenerator(true);
        final CloudBalance planningProblem = generator.createCloudBalance(4, 12);
        assertNull(planningProblem.getScore());
        assertNull(planningProblem.getProcessList().get(0).getComputer());
        return planningProblem;
    }
}
