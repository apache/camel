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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.optaplanner.core.impl.solver.ProblemFactChange;
import org.optaplanner.examples.cloudbalancing.domain.CloudBalance;
import org.optaplanner.examples.cloudbalancing.domain.CloudComputer;
import org.optaplanner.examples.cloudbalancing.domain.CloudProcess;
import org.optaplanner.examples.cloudbalancing.persistence.CloudBalancingGenerator;

/**
 * OptaPlanner unit test with Camel
 */
public class OptaPlannerDaemonSolverTest extends CamelTestSupport {

    @Test
    public void testAsynchronousProblemSolving() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.setExpectedCount(1);
        CloudBalancingGenerator generator = new CloudBalancingGenerator(true);
        final CloudBalance planningProblem = generator.createCloudBalance(4, 12);
        assertNull(planningProblem.getScore());
        assertNull(planningProblem.getProcessList().get(0).getComputer());

        template.requestBody("direct:in", planningProblem);
        mockEndpoint.assertIsSatisfied();
        mockEndpoint.reset();
        mockEndpoint.setExpectedCount(1);

        CloudComputer firstComputer = planningProblem.getComputerList().get(0);
        assertNotNull(firstComputer);
        template.requestBody("direct:in", new RemoveComputerChange(firstComputer));

        mockEndpoint.assertIsSatisfied();
        CloudBalance bestSolution = (CloudBalance) template.requestBody("direct:in", "foo");
        assertEquals(3, bestSolution.getComputerList().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in").
                        to("optaplanner:org/apache/camel/component/optaplanner/daemonSolverConfig.xml?async=true");

                from("optaplanner:org/apache/camel/component/optaplanner/daemonSolverConfig.xml").
                        to("log:com.mycompany.order?showAll=true&multiline=true").
                        to("mock:result");
            }
        };
    }

    private static class RemoveComputerChange implements ProblemFactChange<Object> {

        private final CloudComputer removingComputer;

        RemoveComputerChange(CloudComputer removingComputer) {
            this.removingComputer = removingComputer;
        }

        @Override
        public void doChange(ScoreDirector<Object> scoreDirector) {
            CloudBalance cloudBalance = (CloudBalance) scoreDirector.getWorkingSolution();
            for (CloudProcess process : cloudBalance.getProcessList()) {
                if (Objects.equals(process.getComputer(), removingComputer)) {
                    scoreDirector.beforeVariableChanged(process, "computer");
                    process.setComputer(null);
                    scoreDirector.afterVariableChanged(process, "computer");
                }
            }
            cloudBalance.setComputerList(new ArrayList<>(cloudBalance.getComputerList()));
            for (Iterator<CloudComputer> it = cloudBalance.getComputerList().iterator(); it.hasNext();) {
                CloudComputer workingComputer = it.next();
                if (Objects.equals(workingComputer, removingComputer)) {
                    scoreDirector.beforeProblemFactRemoved(workingComputer);
                    it.remove(); // remove from list
                    scoreDirector.beforeProblemFactRemoved(workingComputer);
                    scoreDirector.triggerVariableListeners();
                    break;
                }
            }
        }
    }
}
