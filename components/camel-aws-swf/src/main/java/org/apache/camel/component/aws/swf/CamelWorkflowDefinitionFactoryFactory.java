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
package org.apache.camel.component.aws.swf;

import java.util.ArrayList;

import com.amazonaws.services.simpleworkflow.flow.DataConverter;
import com.amazonaws.services.simpleworkflow.flow.JsonDataConverter;
import com.amazonaws.services.simpleworkflow.flow.WorkflowTypeRegistrationOptions;
import com.amazonaws.services.simpleworkflow.flow.generic.WorkflowDefinitionFactory;
import com.amazonaws.services.simpleworkflow.flow.generic.WorkflowDefinitionFactoryFactory;
import com.amazonaws.services.simpleworkflow.model.WorkflowType;

public class CamelWorkflowDefinitionFactoryFactory extends WorkflowDefinitionFactoryFactory {
    private final SWFWorkflowConsumer swfWorkflowConsumer;
    private final SWFConfiguration configuration;

    public CamelWorkflowDefinitionFactoryFactory(SWFWorkflowConsumer swfWorkflowConsumer, SWFConfiguration configuration) {
        this.swfWorkflowConsumer = swfWorkflowConsumer;
        this.configuration = configuration;
    }

    @Override
    public WorkflowDefinitionFactory getWorkflowDefinitionFactory(WorkflowType workflowType) {
        WorkflowTypeRegistrationOptions registrationOptions = configuration.getWorkflowTypeRegistrationOptions() != null
                ? configuration.getWorkflowTypeRegistrationOptions() : new WorkflowTypeRegistrationOptions();
        DataConverter dataConverter = configuration.getDataConverter() != null
                ? configuration.getDataConverter() : new JsonDataConverter();
        return new CamelWorkflowDefinitionFactory(swfWorkflowConsumer, workflowType, registrationOptions, dataConverter);
    }

    @Override
    public Iterable<WorkflowType> getWorkflowTypesToRegister() {
        ArrayList<WorkflowType> workflowTypes = new ArrayList<>(1);
        WorkflowType workflowType = new WorkflowType();
        workflowType.setName(configuration.getEventName());
        workflowType.setVersion(configuration.getVersion());
        workflowTypes.add(workflowType);
        return workflowTypes;
    }
}
