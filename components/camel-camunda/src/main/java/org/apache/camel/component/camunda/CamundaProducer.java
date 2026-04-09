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
package org.apache.camel.component.camunda;

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.component.camunda.internal.OperationName;
import org.apache.camel.component.camunda.processor.CamundaProcessor;
import org.apache.camel.component.camunda.processor.DeploymentProcessor;
import org.apache.camel.component.camunda.processor.JobProcessor;
import org.apache.camel.component.camunda.processor.MessageProcessor;
import org.apache.camel.component.camunda.processor.ProcessProcessor;
import org.apache.camel.support.DefaultProducer;

public class CamundaProducer extends DefaultProducer {
    private CamundaProcessor processor;

    public CamundaProducer(CamundaEndpoint endpoint) {
        super(endpoint);

        final OperationName operationName = endpoint.getOperationName();
        if (isProcessOperation(operationName)) {
            processor = new ProcessProcessor(endpoint);
        } else if (isMessageOperation(operationName)) {
            processor = new MessageProcessor(endpoint);
        } else if (isJobOperation(operationName)) {
            processor = new JobProcessor(endpoint);
        } else if (isDeploymentOperation(operationName)) {
            processor = new DeploymentProcessor(endpoint);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (processor == null) {
            throw new CamelException("No processor set");
        }
    }

    public void process(Exchange exchange) throws Exception {
        if (processor != null) {
            processor.process(exchange);
        } else {
            throw new CamelException("No Processor Found");
        }
    }

    @Override
    public CamundaEndpoint getEndpoint() {
        return (CamundaEndpoint) super.getEndpoint();
    }

    private static boolean isProcessOperation(OperationName operationName) {
        switch (operationName) {
            case START_PROCESS:
            case CANCEL_PROCESS:
                return true;
            default:
                return false;
        }
    }

    private static boolean isMessageOperation(OperationName operationName) {
        switch (operationName) {
            case PUBLISH_MESSAGE:
                return true;
            default:
                return false;
        }
    }

    private static boolean isJobOperation(OperationName operationName) {
        switch (operationName) {
            case COMPLETE_JOB:
            case FAIL_JOB:
            case UPDATE_JOB_RETRIES:
            case THROW_ERROR:
                return true;
            default:
                return false;
        }
    }

    private static boolean isDeploymentOperation(OperationName operationName) {
        switch (operationName) {
            case DEPLOY_RESOURCE:
                return true;
            default:
                return false;
        }
    }
}
