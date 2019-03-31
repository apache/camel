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
import com.amazonaws.services.simpleworkflow.flow.generic.ActivityImplementation;
import com.amazonaws.services.simpleworkflow.flow.generic.ActivityImplementationFactory;
import com.amazonaws.services.simpleworkflow.flow.worker.ActivityTypeExecutionOptions;
import com.amazonaws.services.simpleworkflow.flow.worker.ActivityTypeRegistrationOptions;
import com.amazonaws.services.simpleworkflow.model.ActivityType;

public class CamelActivityImplementationFactory extends ActivityImplementationFactory {
    private SWFActivityConsumer swfWorkflowConsumer;
    private SWFConfiguration configuration;

    public CamelActivityImplementationFactory(SWFActivityConsumer swfWorkflowConsumer, SWFConfiguration configuration) {
        this.swfWorkflowConsumer = swfWorkflowConsumer;
        this.configuration = configuration;
    }

    @Override
    public Iterable<ActivityType> getActivityTypesToRegister() {
        ArrayList<ActivityType> activityTypes = new ArrayList<>(1);
        ActivityType activityType = new ActivityType();
        activityType.setName(configuration.getEventName());
        activityType.setVersion(configuration.getVersion());
        activityTypes.add(activityType);
        return activityTypes;
    }

    @Override
    public ActivityImplementation getActivityImplementation(ActivityType activityType) {
        ActivityTypeExecutionOptions activityTypeExecutionOptions = configuration.getActivityTypeExecutionOptions() != null
                ? configuration.getActivityTypeExecutionOptions() : new ActivityTypeExecutionOptions();

        ActivityTypeRegistrationOptions activityTypeRegistrationOptions = configuration.getActivityTypeRegistrationOptions() != null
                ? configuration.getActivityTypeRegistrationOptions() : new ActivityTypeRegistrationOptions();

        DataConverter dataConverter = configuration.getDataConverter() != null
                ? configuration.getDataConverter() : new JsonDataConverter();

        return new CamelActivityImplementation(swfWorkflowConsumer, activityTypeRegistrationOptions, activityTypeExecutionOptions, dataConverter);
    }
}
