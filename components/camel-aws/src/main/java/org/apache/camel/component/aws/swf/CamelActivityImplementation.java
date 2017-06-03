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

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CancellationException;

import com.amazonaws.services.simpleworkflow.flow.ActivityExecutionContext;
import com.amazonaws.services.simpleworkflow.flow.ActivityExecutionContextProviderImpl;
import com.amazonaws.services.simpleworkflow.flow.ActivityFailureException;
import com.amazonaws.services.simpleworkflow.flow.DataConverter;
import com.amazonaws.services.simpleworkflow.flow.DataConverterException;
import com.amazonaws.services.simpleworkflow.flow.common.WorkflowExecutionUtils;
import com.amazonaws.services.simpleworkflow.flow.generic.ActivityImplementationBase;
import com.amazonaws.services.simpleworkflow.flow.worker.ActivityTypeExecutionOptions;
import com.amazonaws.services.simpleworkflow.flow.worker.ActivityTypeRegistrationOptions;
import com.amazonaws.services.simpleworkflow.flow.worker.CurrentActivityExecutionContext;

public class CamelActivityImplementation extends ActivityImplementationBase {
    private final ActivityTypeExecutionOptions executionOptions;
    private final ActivityTypeRegistrationOptions registrationOptions;
    private final DataConverter converter;
    private final SWFActivityConsumer swfWorkflowConsumer;
    private final ActivityExecutionContextProviderImpl contextProvider = new ActivityExecutionContextProviderImpl();

    public CamelActivityImplementation(SWFActivityConsumer swfWorkflowConsumer, ActivityTypeRegistrationOptions activityTypeRegistrationOptions,
                                       ActivityTypeExecutionOptions activityTypeExecutionOptions, DataConverter converter) {
        this.swfWorkflowConsumer = swfWorkflowConsumer;
        this.registrationOptions = activityTypeRegistrationOptions;
        this.executionOptions = activityTypeExecutionOptions;
        this.converter = converter;
    }

    @Override
    public ActivityTypeRegistrationOptions getRegistrationOptions() {
        return registrationOptions;
    }

    @Override
    public ActivityTypeExecutionOptions getExecutionOptions() {
        return executionOptions;
    }

    @Override
    protected String execute(String input, ActivityExecutionContext context) throws ActivityFailureException, CancellationException {
        Object[] inputParameters = converter.fromData(input, Object[].class);
        CurrentActivityExecutionContext.set(context);
        Object result = null;

        ActivityExecutionContext executionContext = contextProvider.getActivityExecutionContext();
        String taskToken = executionContext.getTaskToken();

        try {
            result = swfWorkflowConsumer.processActivity(inputParameters, taskToken);
        } catch (InvocationTargetException invocationException) {
            throwActivityFailureException(invocationException.getTargetException() != null ? invocationException.getTargetException() : invocationException);
        } catch (IllegalArgumentException illegalArgumentException) {
            throwActivityFailureException(illegalArgumentException);
        } catch (IllegalAccessException illegalAccessException) {
            throwActivityFailureException(illegalAccessException);
        } catch (Exception e) {
            throwActivityFailureException(e);
        } finally {
            CurrentActivityExecutionContext.unset();
        }
        String resultSerialized = converter.toData(result);
        return resultSerialized;
    }

    void throwActivityFailureException(Throwable exception) throws ActivityFailureException, CancellationException {
        if (exception instanceof CancellationException) {
            throw (CancellationException) exception;
        }

        String reason = WorkflowExecutionUtils.truncateReason(exception.getMessage());
        String details = null;
        try {
            details = converter.toData(exception);
        } catch (DataConverterException dataConverterException) {
            if (dataConverterException.getCause() == null) {
                dataConverterException.initCause(exception);
            }
            throw dataConverterException;
        }

        throw new ActivityFailureException(reason, details);
    }
}