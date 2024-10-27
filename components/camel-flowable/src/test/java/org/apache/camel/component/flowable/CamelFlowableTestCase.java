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
package org.apache.camel.component.flowable;

import java.util.List;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.eventregistry.api.EventDeployment;
import org.flowable.eventregistry.impl.EventRegistryEngineConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class CamelFlowableTestCase extends CamelTestSupport {

    protected ProcessEngineConfiguration processEngineConfiguration;
    protected EventRegistryEngineConfiguration eventRegistryEngineConfiguration;
    protected RepositoryService repositoryService;
    protected RuntimeService runtimeService;
    protected TaskService taskService;

    @BeforeEach
    public void initialize() {
        ProcessEngine processEngine = FlowableEngineUtil.getProcessEngine(context);
        processEngineConfiguration = processEngine.getProcessEngineConfiguration();
        eventRegistryEngineConfiguration = FlowableEngineUtil.getEventRegistryEngineConfiguration();
        repositoryService = processEngine.getRepositoryService();
        runtimeService = processEngine.getRuntimeService();
        taskService = processEngine.getTaskService();
    }

    @AfterEach
    public void tearDown() throws Exception {
        List<EventDeployment> eventDeployments
                = eventRegistryEngineConfiguration.getEventRepositoryService().createDeploymentQuery().list();
        for (EventDeployment eventDeployment : eventDeployments) {
            eventRegistryEngineConfiguration.getEventRepositoryService().deleteDeployment(eventDeployment.getId());
        }
    }

    protected String deployProcessDefinition(String resource) {
        return repositoryService.createDeployment().addClasspathResource(resource).deploy().getId();
    }

    protected void deleteDeployment(String id) {
        repositoryService.deleteDeployment(id);
    }
}
