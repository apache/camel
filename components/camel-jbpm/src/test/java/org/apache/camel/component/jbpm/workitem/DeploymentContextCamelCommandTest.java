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
package org.apache.camel.component.jbpm.workitem;

import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.jbpm.JBPMConstants;
import org.drools.core.process.instance.impl.WorkItemImpl;
import org.jbpm.services.api.service.ServiceRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.executor.Command;
import org.kie.api.executor.CommandContext;
import org.kie.api.executor.ExecutionResults;
import org.kie.api.runtime.manager.RuntimeManager;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DeploymentContextCamelCommandTest {

    @Mock
    ProducerTemplate producerTemplate;

    @Mock
    Exchange outExchange;

    @Mock
    Message outMessage;

    @Mock
    ExtendedCamelContext camelContext;

    @Mock
    RuntimeManager runtimeManager;

    @Mock
    CommandContext commandContext;

    @Test
    public void testExecuteCommandDeploymentCamelContext() throws Exception {

        String camelEndpointId = "testCamelRoute";
        String camelRouteUri = "direct:" + camelEndpointId;

        String testReponse = "testResponse";

        String deploymentId = "testDeployment";

        when(producerTemplate.send(eq(camelRouteUri), any(Exchange.class))).thenReturn(outExchange);
        when(producerTemplate.getCamelContext()).thenReturn(camelContext);

        when(camelContext.createProducerTemplate()).thenReturn(producerTemplate);

        when(outExchange.getOut()).thenReturn(outMessage);
        when(outMessage.getBody()).thenReturn(testReponse);

        // Register the RuntimeManager bound camelcontext.
        try {
            ServiceRegistry.get().register(deploymentId + JBPMConstants.DEPLOYMENT_CAMEL_CONTEXT_SERVICE_KEY_POSTFIX, camelContext);

            WorkItemImpl workItem = new WorkItemImpl();
            workItem.setParameter(JBPMConstants.CAMEL_ENDPOINT_ID_WI_PARAM, camelEndpointId);
            workItem.setParameter("Request", "someRequest");

            when(commandContext.getData("workItem")).thenReturn(workItem);
            when(commandContext.getData("deploymentId")).thenReturn(deploymentId);

            Command command = new DeploymentContextCamelCommand();
            ExecutionResults results = command.execute(commandContext);

            assertNotNull(results);
            assertEquals(2, results.getData().size());
            assertEquals(testReponse, results.getData().get(JBPMConstants.RESPONSE_WI_PARAM));
        } finally {
            ServiceRegistry.get().remove(deploymentId + JBPMConstants.DEPLOYMENT_CAMEL_CONTEXT_SERVICE_KEY_POSTFIX);
        }
    }
}
