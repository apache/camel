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
package org.apache.camel.component.jbpm.workitem;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.jbpm.JBPMConstants;
import org.apache.camel.impl.DefaultHeadersMapFactory;
import org.apache.camel.spi.HeadersMapFactory;
import org.drools.core.process.instance.impl.WorkItemImpl;
import org.jbpm.process.workitem.core.TestWorkItemManager;
import org.jbpm.services.api.service.ServiceRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.runtime.manager.RuntimeManager;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class InOutCamelWorkItemHandlerTest {

    @Mock
    ProducerTemplate producerTemplate;

    @Mock
    Exchange outExchange;

    @Mock
    Message outMessage;

    @Mock
    CamelContext camelContext;

    @Mock
    RuntimeManager runtimeManager;

    @Test
    public void testExecuteInOutGlobalCamelContext() throws Exception {

        String camelEndpointId = "testCamelRoute";
        String camelRouteUri = "direct:" + camelEndpointId;

        String testReponse = "testResponse";

        when(producerTemplate.send(eq(camelRouteUri), ArgumentMatchers.any(Exchange.class))).thenReturn(outExchange);
        when(producerTemplate.getCamelContext()).thenReturn(camelContext);

        when(camelContext.createProducerTemplate()).thenReturn(producerTemplate);
        HeadersMapFactory hmf = new DefaultHeadersMapFactory();
        when(camelContext.getHeadersMapFactory()).thenReturn(hmf);

        when(outExchange.getOut()).thenReturn(outMessage);
        when(outMessage.getBody()).thenReturn(testReponse);

        try {
            ServiceRegistry.get().register("GlobalCamelService", camelContext);

            TestWorkItemManager manager = new TestWorkItemManager();
            WorkItemImpl workItem = new WorkItemImpl();
            workItem.setParameter("CamelEndpointId", camelEndpointId);
            workItem.setParameter("Request", "someRequest");
            workItem.setDeploymentId("testDeploymentId");
            workItem.setProcessInstanceId(1L);
            workItem.setId(1L);

            AbstractCamelWorkItemHandler handler = new InOutCamelWorkItemHandler();

            handler.executeWorkItem(workItem, manager);
            assertThat(manager.getResults(), is(notNullValue()));
            assertThat(manager.getResults().size(), equalTo(1));
            assertThat(manager.getResults().containsKey(workItem.getId()), is(true));
            Map<String, Object> results = manager.getResults(workItem.getId());
            assertThat(results.size(), equalTo(2));
            assertThat(results.get("Response"), equalTo(testReponse));

        } finally {
            ServiceRegistry.get().remove("GlobalCamelService");
        }

    }

    @Test
    public void testExecuteInOutLocalCamelContext() throws Exception {

        String camelEndpointId = "testCamelRoute";
        String camelRouteUri = "direct:" + camelEndpointId;

        String testReponse = "testResponse";

        String runtimeManagerId = "testRuntimeManager";

        when(runtimeManager.getIdentifier()).thenReturn(runtimeManagerId);

        when(producerTemplate.send(eq(camelRouteUri), ArgumentMatchers.any(Exchange.class))).thenReturn(outExchange);
        when(producerTemplate.getCamelContext()).thenReturn(camelContext);

        when(camelContext.createProducerTemplate()).thenReturn(producerTemplate);
        HeadersMapFactory hmf = new DefaultHeadersMapFactory();
        when(camelContext.getHeadersMapFactory()).thenReturn(hmf);

        when(outExchange.getOut()).thenReturn(outMessage);
        when(outMessage.getBody()).thenReturn(testReponse);

        // Register the RuntimeManager bound camelcontext.
        try {
            ServiceRegistry.get().register(runtimeManagerId + "_CamelService", camelContext);

            WorkItemImpl workItem = new WorkItemImpl();
            workItem.setParameter(JBPMConstants.CAMEL_ENDPOINT_ID_WI_PARAM, camelEndpointId);
            workItem.setParameter("Request", "someRequest");
            workItem.setDeploymentId("testDeploymentId");
            workItem.setProcessInstanceId(1L);
            workItem.setId(1L);

            AbstractCamelWorkItemHandler handler = new InOutCamelWorkItemHandler(runtimeManager);

            TestWorkItemManager manager = new TestWorkItemManager();
            handler.executeWorkItem(workItem, manager);
            assertThat(manager.getResults(), is(notNullValue()));
            assertThat(manager.getResults().size(), equalTo(1));
            assertThat(manager.getResults().containsKey(workItem.getId()), is(true));

            Map<String, Object> results = manager.getResults(workItem.getId());
            assertThat(results.size(), equalTo(2));
            assertThat(results.get(JBPMConstants.RESPONSE_WI_PARAM), equalTo(testReponse));
        } finally {
            ServiceRegistry.get().remove(runtimeManagerId + "_CamelService");
        }
    }

    @Test
    public void testExecuteInOutLocalCamelContextLazyInit() throws Exception {

        String camelEndpointId = "testCamelRoute";
        String camelRouteUri = "direct:" + camelEndpointId;

        String testReponse = "testResponse";

        String runtimeManagerId = "testRuntimeManager";

        when(runtimeManager.getIdentifier()).thenReturn(runtimeManagerId);

        when(producerTemplate.send(eq(camelRouteUri), ArgumentMatchers.any(Exchange.class))).thenReturn(outExchange);
        when(producerTemplate.getCamelContext()).thenReturn(camelContext);

        when(camelContext.createProducerTemplate()).thenReturn(producerTemplate);
        HeadersMapFactory hmf = new DefaultHeadersMapFactory();
        when(camelContext.getHeadersMapFactory()).thenReturn(hmf);

        when(outExchange.getOut()).thenReturn(outMessage);
        when(outMessage.getBody()).thenReturn(testReponse);

        WorkItemImpl workItem = new WorkItemImpl();
        workItem.setParameter(JBPMConstants.CAMEL_ENDPOINT_ID_WI_PARAM, camelEndpointId);
        workItem.setParameter("Request", "someRequest");
        workItem.setDeploymentId("testDeploymentId");
        workItem.setProcessInstanceId(1L);
        workItem.setId(1L);

        AbstractCamelWorkItemHandler handler = new InOutCamelWorkItemHandler(runtimeManager);

        // Register the context after we've created the WIH to test lazy-init.
        try {
            ServiceRegistry.get().register(runtimeManagerId + "_CamelService", camelContext);

            TestWorkItemManager manager = new TestWorkItemManager();
            handler.executeWorkItem(workItem, manager);
            assertThat(manager.getResults(), is(notNullValue()));
            assertThat(manager.getResults().size(), equalTo(1));
            assertThat(manager.getResults().containsKey(workItem.getId()), is(true));

            Map<String, Object> results = manager.getResults(workItem.getId());
            assertThat(results.size(), equalTo(2));
            assertThat(results.get(JBPMConstants.RESPONSE_WI_PARAM), equalTo(testReponse));
        } finally {
            ServiceRegistry.get().remove(runtimeManagerId + "_CamelService");
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExecuteInOutLocalCamelContextLazyInitFail() throws Exception {

        String camelEndpointId = "testCamelRoute";
        String camelRouteUri = "direct:" + camelEndpointId;

        String testReponse = "testResponse";

        String runtimeManagerId = "testRuntimeManager";

        when(runtimeManager.getIdentifier()).thenReturn(runtimeManagerId);

        WorkItemImpl workItem = new WorkItemImpl();
        workItem.setParameter(JBPMConstants.CAMEL_ENDPOINT_ID_WI_PARAM, camelEndpointId);
        workItem.setParameter("Request", "someRequest");
        workItem.setDeploymentId("testDeploymentId");
        workItem.setProcessInstanceId(1L);
        workItem.setId(1L);

        AbstractCamelWorkItemHandler handler = new InOutCamelWorkItemHandler(runtimeManager);

        TestWorkItemManager manager = new TestWorkItemManager();
        // This is expected to throw an exception.
        handler.executeWorkItem(workItem, manager);

    }

}
