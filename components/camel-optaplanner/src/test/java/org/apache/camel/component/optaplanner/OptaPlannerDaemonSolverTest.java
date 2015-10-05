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

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.lang.ObjectUtils;
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
        getMockEndpoint("mock:result").setExpectedCount(1);
        CloudBalancingGenerator generator = new CloudBalancingGenerator(true);
        final CloudBalance planningProblem = generator.createCloudBalance(4, 12);
        assertNull(planningProblem.getScore());
        assertNull(planningProblem.getProcessList().get(0).getComputer());

        template.requestBody("direct:in", planningProblem);
        getMockEndpoint("mock:result").assertIsSatisfied();
        getMockEndpoint("mock:result").reset();
        getMockEndpoint("mock:result").setExpectedCount(1);

        template.requestBody("direct:in", new ProblemFactChange() {
            @Override
            public void doChange(ScoreDirector scoreDirector) {
                CloudBalance cloudBalance = (CloudBalance) scoreDirector.getWorkingSolution();
                CloudComputer computer = null;
                for (CloudProcess process : cloudBalance.getProcessList()) {
                    computer = process.getComputer();
                    if (ObjectUtils.equals(process.getComputer(), computer)) {
                        scoreDirector.beforeVariableChanged(process, "computer");
                        process.setComputer(null);
                        scoreDirector.afterVariableChanged(process, "computer");
                    }
                }
                cloudBalance.setComputerList(new ArrayList<CloudComputer>(cloudBalance.getComputerList()));
                for (Iterator<CloudComputer> it = cloudBalance.getComputerList().iterator(); it.hasNext();) {
                    CloudComputer workingComputer = it.next();
                    if (ObjectUtils.equals(workingComputer, computer)) {
                        scoreDirector.beforeProblemFactRemoved(workingComputer);
                        it.remove(); // remove from list
                        scoreDirector.beforeProblemFactRemoved(workingComputer);
                        break;
                    }
                }
            }
        });

        getMockEndpoint("mock:result").assertIsSatisfied();
        CloudBalance bestSolution = (CloudBalance) template.requestBody("direct:in", "foo");

        assertEquals(3, bestSolution.getComputerList().size());
    }

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

}
