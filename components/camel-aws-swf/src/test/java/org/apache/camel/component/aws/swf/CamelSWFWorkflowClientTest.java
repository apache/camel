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
package org.apache.camel.component.aws.swf;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient;
import com.amazonaws.services.simpleworkflow.flow.DynamicWorkflowClientExternal;
import com.amazonaws.services.simpleworkflow.flow.DynamicWorkflowClientExternalImpl;
import com.amazonaws.services.simpleworkflow.model.DescribeWorkflowExecutionRequest;
import com.amazonaws.services.simpleworkflow.model.WorkflowExecution;
import com.amazonaws.services.simpleworkflow.model.WorkflowExecutionDetail;
import com.amazonaws.services.simpleworkflow.model.WorkflowExecutionInfo;
import org.junit.Before;
import org.junit.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CamelSWFWorkflowClientTest {

    private SWFConfiguration configuration;
    private AmazonSimpleWorkflowClient swClient;
    private SWFEndpoint endpoint;
    private CamelSWFWorkflowClient camelSWFWorkflowClient;
    private DynamicWorkflowClientExternal clientExternal;

    @Before
    public void setUp() throws Exception {
        configuration = new SWFConfiguration();
        configuration.setDomainName("testDomain");
        swClient = mock(AmazonSimpleWorkflowClient.class);
        configuration.setAmazonSWClient(swClient);
        configuration.setStartWorkflowOptionsParameters(Collections.<String, Object>emptyMap());

        endpoint = new SWFEndpoint();
        endpoint.setConfiguration(configuration);
        clientExternal = mock(DynamicWorkflowClientExternalImpl.class);

        camelSWFWorkflowClient = new CamelSWFWorkflowClient(endpoint, configuration) {
            @Override
            DynamicWorkflowClientExternal getDynamicWorkflowClient(String workflowId, String runId) {
                return clientExternal;
            }
        };
    }

    @Test
    public void testDescribeWorkflowInstance() throws Exception {
        WorkflowExecutionInfo executionInfo = new WorkflowExecutionInfo();
        executionInfo.setCloseStatus("COMPLETED");
        Date closeTimestamp = new Date();
        executionInfo.setCloseTimestamp(closeTimestamp);
        executionInfo.setExecutionStatus("CLOSED");
        executionInfo.setTagList(Collections.emptyList());

        WorkflowExecutionDetail workflowExecutionDetail = new WorkflowExecutionDetail();
        workflowExecutionDetail.setExecutionInfo(executionInfo);

        when(swClient.describeWorkflowExecution(any(DescribeWorkflowExecutionRequest.class))).thenReturn(workflowExecutionDetail);
        Map<String, Object> description = camelSWFWorkflowClient.describeWorkflowInstance("123", "run1");

        DescribeWorkflowExecutionRequest describeRequest = new DescribeWorkflowExecutionRequest();
        describeRequest.setDomain(configuration.getDomainName());
        describeRequest.setExecution(new WorkflowExecution().withWorkflowId("123").withRunId("run1"));


        verify(swClient).describeWorkflowExecution(describeRequest);
        assertThat((String) description.get("closeStatus"), is("COMPLETED"));
        assertThat((Date) description.get("closeTimestamp"), is(closeTimestamp));
        assertThat((String) description.get("executionStatus"), is("CLOSED"));
        assertThat((List<?>) description.get("tagList"), is(Collections.EMPTY_LIST));
        assertThat((WorkflowExecutionDetail) description.get("executionDetail"), is(workflowExecutionDetail));
    }

    @Test
    public void testSignalWorkflowExecution() throws Exception {

        camelSWFWorkflowClient.signalWorkflowExecution("123", "run1", "signalMethod", "Hi");
        verify(clientExternal).signalWorkflowExecution("signalMethod", new Object[] {"Hi"});
    }

    @Test
    public void testGetWorkflowExecutionState() throws Throwable {

        Class<String> stateType = String.class;
        when(clientExternal.getWorkflowExecutionState(stateType)).thenReturn("some state");
        String state = (String) camelSWFWorkflowClient.getWorkflowExecutionState("123", "run1", stateType);

        verify(clientExternal).getWorkflowExecutionState(stateType);
        assertThat(state, is("some state"));
    }

    @Test
    public void testRequestCancelWorkflowExecution() throws Throwable {
        camelSWFWorkflowClient.requestCancelWorkflowExecution("123", "run1");

        verify(clientExternal).requestCancelWorkflowExecution();
    }

    @Test
    public void testTerminateWorkflowExecution() throws Throwable {
        camelSWFWorkflowClient.terminateWorkflowExecution("123", "run1", "reason", "details", null);

        verify(clientExternal).terminateWorkflowExecution("reason", "details", null);
    }

    @Test
    public void testStartWorkflowExecution() throws Throwable {

        WorkflowExecution workflowExecution = new WorkflowExecution();
        workflowExecution.setWorkflowId("123");
        workflowExecution.setRunId("run1");
        when(clientExternal.getWorkflowExecution()).thenReturn(workflowExecution);


        String[] ids = camelSWFWorkflowClient.startWorkflowExecution(null, null, "eventName", "version", null, Collections.singletonList("camelTest"));

        verify(clientExternal).startWorkflowExecution(new Object[]{null});
        assertThat("123", is(ids[0]));
        assertThat("run1", is(ids[1]));
    }
}
