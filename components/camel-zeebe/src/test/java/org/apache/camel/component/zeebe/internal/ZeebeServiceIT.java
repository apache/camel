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

package org.apache.camel.component.zeebe.internal;

import java.io.IOException;

import org.apache.camel.component.zeebe.ZeebeConstants;
import org.apache.camel.component.zeebe.model.DeploymentRequest;
import org.apache.camel.component.zeebe.model.DeploymentResponse;
import org.apache.camel.component.zeebe.model.MessageRequest;
import org.apache.camel.component.zeebe.model.MessageResponse;
import org.apache.camel.component.zeebe.model.ProcessDeploymentResponse;
import org.apache.camel.component.zeebe.model.ProcessRequest;
import org.apache.camel.component.zeebe.model.ProcessResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "zeebe.test.integration.enable", matches = "true",
                         disabledReason = "Requires locally installed test system")
public class ZeebeServiceIT {

    public static final String TEST_1_DEFINITION_BPMN = "test1_definition.bpmn";

    private ZeebeService service;

    @BeforeAll
    void initAll() {
        String gatewayHost = ZeebeConstants.DEFAULT_GATEWAY_HOST;
        int gatewayPort = ZeebeConstants.DEFAULT_GATEWAY_PORT;

        service = new ZeebeService(gatewayHost, gatewayPort);

        service.doStart();
    }

    @AfterAll
    void tearDownAll() {
        service.doStop();
    }

    @Test
    public void testDeployResource() throws IOException {
        DeploymentRequest deployProcessMessage = new DeploymentRequest();
        deployProcessMessage.setName(TEST_1_DEFINITION_BPMN);
        deployProcessMessage
                .setContent(this.getClass().getClassLoader().getResourceAsStream("data/test1_definition.bpmn").readAllBytes());

        DeploymentResponse deploymentResponse = service.deployResource(deployProcessMessage);
        assertTrue(deploymentResponse.isSuccess());
        assertTrue(deploymentResponse instanceof ProcessDeploymentResponse);

        ProcessDeploymentResponse processDeploymentResult = (ProcessDeploymentResponse) deploymentResponse;
        assertTrue(processDeploymentResult.getVersion() > 0);
        assertEquals(TEST_1_DEFINITION_BPMN, processDeploymentResult.getResourceName());
        assertNotNull(processDeploymentResult.getBpmnProcessId());
    }

    @Test
    public void testProcessLifecycleWithCancel() throws IOException {

        DeploymentRequest deployProcessMessage = new DeploymentRequest();
        deployProcessMessage.setName(TEST_1_DEFINITION_BPMN);
        deployProcessMessage
                .setContent(this.getClass().getClassLoader().getResourceAsStream("data/test1_definition.bpmn").readAllBytes());

        DeploymentResponse deploymentResponse = service.deployResource(deployProcessMessage);
        assertTrue(deploymentResponse instanceof ProcessDeploymentResponse);

        ProcessRequest processRequest = new ProcessRequest();
        processRequest.setProcessId(((ProcessDeploymentResponse) deploymentResponse).getBpmnProcessId());
        ProcessResponse processResponse = service.startProcess(processRequest);
        assertTrue(processResponse.isSuccess());

        processRequest = new ProcessRequest();
        processRequest.setProcessInstanceKey(processResponse.getProcessInstanceKey());
        // Terminate just started process
        processResponse = service.cancelProcessInstance(processRequest);
        assertTrue(processResponse.isSuccess());

        // Terminate terminated process should fail
        processResponse = service.cancelProcessInstance(processRequest);
        assertFalse(processResponse.isSuccess());
    }

    @Test
    public void testPublishMessage() {
        // Sending message without CorrelationKey should fail
        MessageRequest message = new MessageRequest();
        MessageResponse response = service.publishMessage(message);
        assertFalse(response.isSuccess());

        // Sending message with CorrelationKey should succeed
        message = new MessageRequest();
        message.setCorrelationKey("testKey");
        response = service.publishMessage(message);
        assertTrue(response.isSuccess());
    }
}
