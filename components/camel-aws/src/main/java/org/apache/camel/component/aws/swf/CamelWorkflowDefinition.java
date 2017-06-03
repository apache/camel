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

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

import com.amazonaws.services.simpleworkflow.flow.DataConverter;
import com.amazonaws.services.simpleworkflow.flow.DataConverterException;
import com.amazonaws.services.simpleworkflow.flow.DecisionContext;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProvider;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProviderImpl;
import com.amazonaws.services.simpleworkflow.flow.WorkflowClock;
import com.amazonaws.services.simpleworkflow.flow.WorkflowException;
import com.amazonaws.services.simpleworkflow.flow.common.WorkflowExecutionUtils;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.core.Settable;
import com.amazonaws.services.simpleworkflow.flow.core.TryCatchFinally;
import com.amazonaws.services.simpleworkflow.flow.generic.WorkflowDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelWorkflowDefinition extends WorkflowDefinition {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(CamelWorkflowDefinition.class);

    private SWFWorkflowConsumer swfWorkflowConsumer;
    private DecisionContext decisionContext;
    private DataConverter dataConverter;

    private final DecisionContextProvider contextProvider = new DecisionContextProviderImpl();
    private final WorkflowClock workflowClock = contextProvider.getDecisionContext().getWorkflowClock();

    public CamelWorkflowDefinition(SWFWorkflowConsumer swfWorkflowConsumer, DecisionContext decisionContext, DataConverter dataConverter) {
        this.swfWorkflowConsumer = swfWorkflowConsumer;
        this.decisionContext = decisionContext;
        this.dataConverter = dataConverter;
    }

    @Override
    public Promise<String> execute(final String input) throws WorkflowException {
        final Settable<String> result = new Settable<String>();
        final AtomicReference<Promise<?>> methodResult = new AtomicReference<Promise<?>>();
        new TryCatchFinally() {

            @Override
            protected void doTry() throws Throwable {
                Object[] parameters = dataConverter.fromData(input, Object[].class);
                long startTime = workflowClock.currentTimeMillis();
                boolean replaying = contextProvider.getDecisionContext().getWorkflowClock().isReplaying();
                LOGGER.debug("Processing workflow execute");

                Object r = swfWorkflowConsumer.processWorkflow(parameters, startTime, replaying);
                if (r instanceof Promise) {
                    methodResult.set((Promise<?>) r);
                } else if (r != null) {
                    methodResult.set(new Settable<Object>(r));
                }
            }

            @Override
            protected void doCatch(Throwable e) throws Throwable {
                if (!(e instanceof CancellationException) || !decisionContext.getWorkflowContext().isCancelRequested()) {
                    throwWorkflowException(dataConverter, e);
                }
            }

            @Override
            protected void doFinally() throws Throwable {
                Promise<?> r = methodResult.get();
                if (r == null || r.isReady()) {
                    Object workflowResult = r == null ? null : r.get();
                    String convertedResult = dataConverter.toData(workflowResult);
                    result.set(convertedResult);
                }
            }
        };

        return result;
    }

    @Override
    public void signalRecieved(String signalName, String input) throws WorkflowException {
        Object[] parameters = dataConverter.fromData(input, Object[].class);
        try {
            LOGGER.debug("Processing workflow signalRecieved");

            swfWorkflowConsumer.signalRecieved(parameters);
        } catch (Throwable e) {
            throwWorkflowException(dataConverter, e);
            throw new IllegalStateException("Unreacheable");
        }
    }

    @Override
    public String getWorkflowState() throws WorkflowException {
        try {
            LOGGER.debug("Processing workflow getWorkflowState");

            Object result = swfWorkflowConsumer.getWorkflowState(null);
            return dataConverter.toData(result);
        } catch (Throwable e) {
            throwWorkflowException(dataConverter, e);
            throw new IllegalStateException("Unreachable");
        }
    }

    private void throwWorkflowException(DataConverter c, Throwable exception) throws WorkflowException {
        if (exception instanceof WorkflowException) {
            throw (WorkflowException) exception;
        }
        String reason = WorkflowExecutionUtils.truncateReason(exception.getMessage());
        String details = null;
        try {
            details = c.toData(exception);
        } catch (DataConverterException dataConverterException) {
            if (dataConverterException.getCause() == null) {
                dataConverterException.initCause(exception);
            }
            throw dataConverterException;
        }

        throw new WorkflowException(reason, details);
    }
}
