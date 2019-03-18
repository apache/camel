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

import com.amazonaws.services.simpleworkflow.flow.DataConverter;
import com.amazonaws.services.simpleworkflow.flow.DecisionContext;
import com.amazonaws.services.simpleworkflow.flow.WorkflowTypeRegistrationOptions;
import com.amazonaws.services.simpleworkflow.flow.generic.WorkflowDefinition;
import com.amazonaws.services.simpleworkflow.flow.generic.WorkflowDefinitionFactory;
import com.amazonaws.services.simpleworkflow.flow.worker.CurrentDecisionContext;
import com.amazonaws.services.simpleworkflow.model.WorkflowType;

public class CamelWorkflowDefinitionFactory extends WorkflowDefinitionFactory {
    private SWFWorkflowConsumer swfWorkflowConsumer;
    private WorkflowType workflowType;
    private WorkflowTypeRegistrationOptions registrationOptions;
    private DataConverter dataConverter;

    public CamelWorkflowDefinitionFactory(SWFWorkflowConsumer swfWorkflowConsumer, WorkflowType workflowType, WorkflowTypeRegistrationOptions registrationOptions, DataConverter dataConverter) {
        this.swfWorkflowConsumer = swfWorkflowConsumer;
        this.workflowType = workflowType;
        this.registrationOptions = registrationOptions;
        this.dataConverter = dataConverter;
    }

    @Override
    public WorkflowTypeRegistrationOptions getWorkflowRegistrationOptions() {
        return registrationOptions;
    }

    @Override
    public WorkflowDefinition getWorkflowDefinition(DecisionContext context) throws Exception {
        CurrentDecisionContext.set(context);
        return new CamelWorkflowDefinition(swfWorkflowConsumer, context, dataConverter);
    }

    @Override
    public void deleteWorkflowDefinition(WorkflowDefinition instance) {
        CurrentDecisionContext.unset();
    }

    @Override
    public WorkflowType getWorkflowType() {
        return workflowType;
    }
}
