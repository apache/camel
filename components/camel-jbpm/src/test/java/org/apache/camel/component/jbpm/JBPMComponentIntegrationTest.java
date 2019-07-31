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
package org.apache.camel.component.jbpm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jbpm.JBPMProducer.Operation;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.kie.server.api.model.instance.TaskSummary;

/**
 * To run this example you need jBPM to run locally, easiest is to use single
 * zip distribution - download from jbpm.org Next, start it and import
 * Evaluation sample project, build and deploy. Once done this test can be ran
 * out of the box.
 */
@Ignore("This is an integration test that needs jBPM running on the local machine")
public class JBPMComponentIntegrationTest extends CamelTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    public void interactsOverRest() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        // let's start process instance for evaluation process
        Map<String, Object> params = new HashMap<>();
        params.put("employee", "wbadmin");
        params.put("reason", "Camel asks for it");

        Map<String, Object> headers = new HashMap<>();
        headers.put(JBPMConstants.PROCESS_ID, "evaluation");
        headers.put(JBPMConstants.PARAMETERS, params);

        template.sendBodyAndHeaders("direct:start", null, headers);
        assertMockEndpointsSatisfied();
        Long processInstanceId = (Long)getMockEndpoint("mock:result").getExchanges().get(0).getIn().getBody();
        assertNotNull(processInstanceId);

        // now let's collect user tasks
        headers = new HashMap<>();
        headers.put(JBPMConstants.OPERATION, JBPMConstants.OPERATION + Operation.getTasksOwned);

        template.sendBodyAndHeaders("direct:start", null, headers);
        getMockEndpoint("mock:result").expectedMessageCount(2);
        assertMockEndpointsSatisfied();

        List<TaskSummary> tasks = (List<TaskSummary>)getMockEndpoint("mock:result").getExchanges().get(1).getIn().getBody();
        assertEquals(1, tasks.size());

        // let's complete first user task
        headers = new HashMap<>();
        headers.put(JBPMConstants.TASK_ID, tasks.get(0).getId());
        headers.put(JBPMConstants.OPERATION, JBPMConstants.OPERATION + Operation.completeTask);

        template.sendBodyAndHeaders("direct:start", null, headers);
        getMockEndpoint("mock:result").expectedMessageCount(3);
        assertMockEndpointsSatisfied();

        // lastly let's abort process instance we just created
        headers = new HashMap<>();
        headers.put(JBPMConstants.PROCESS_INSTANCE_ID, processInstanceId);
        headers.put(JBPMConstants.OPERATION, JBPMConstants.OPERATION + Operation.abortProcessInstance);

        template.sendBodyAndHeaders("direct:start", null, headers);
        getMockEndpoint("mock:result").expectedMessageCount(4);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("jbpm:http://localhost:8080/kie-server/services/rest/server?userName=wbadmin&password=wbadmin" + "&deploymentId=evaluation")
                    .to("mock:result");
            }
        };
    }
}
