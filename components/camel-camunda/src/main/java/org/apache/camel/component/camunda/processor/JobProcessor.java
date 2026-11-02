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
package org.apache.camel.component.camunda.processor;

import java.util.Map;

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.component.camunda.CamundaConstants;
import org.apache.camel.component.camunda.CamundaEndpoint;
import org.apache.camel.component.camunda.internal.CamundaService;
import org.apache.camel.component.camunda.model.JobRequest;
import org.apache.camel.component.camunda.model.JobResponse;
import org.apache.camel.component.camunda.model.JobWorkerMessage;

public class JobProcessor extends AbstractBaseProcessor {
    public JobProcessor(CamundaEndpoint endpoint) {
        super(endpoint);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void process(Exchange exchange) throws Exception {
        JobRequest message = null;

        Long headerJobKey = exchange.getMessage().getHeader(CamundaConstants.JOB_KEY, Long.class);
        Object body = exchange.getMessage().getBody();
        if (body instanceof JobRequest) {
            message = (JobRequest) body;
        } else if (body instanceof String) {
            message = readJson(exchange, JobRequest.class, "Cannot convert body to JobRequest");
        } else if (body instanceof JobWorkerMessage) {
            JobWorkerMessage jobWorkerMessage = (JobWorkerMessage) body;
            message = new JobRequest();
            message.setJobKey(jobWorkerMessage.getKey());
        } else if (headerJobKey != null) {
            message = buildFromHeaders(exchange, headerJobKey);
        } else if (body != null) {
            message = readJson(exchange, JobRequest.class, "Cannot convert body to JobRequest");
        }
        if (message == null) {
            throw new CamelException("Job data missing");
        }
        if (message.getJobKey() == 0 && headerJobKey != null) {
            // JSON without a job_key, such as the JSON of a job worker: use the job key of the header
            message.setJobKey(headerJobKey);
        }

        JobResponse resultMessage = null;

        switch (endpoint.getOperationName()) {
            case COMPLETE_JOB:
                resultMessage = completeJob(message);
                break;
            case FAIL_JOB:
                resultMessage = failJob(message);
                break;
            case UPDATE_JOB_RETRIES:
                resultMessage = updateJobRetries(message);
                break;
            case THROW_ERROR:
                resultMessage = throwError(message);
                break;
            default:
                throw new IllegalArgumentException("Unknown Operation!");
        }

        exchange.setProperty(CamundaConstants.JOB_HANDLED, true);
        setBody(exchange, resultMessage);
        failIfNotSuccess(exchange, resultMessage);
    }

    private JobResponse completeJob(JobRequest message) {
        CamundaService camundaService = endpoint.getComponent().getCamundaService();
        return camundaService.completeJob(message);
    }

    private JobResponse failJob(JobRequest message) {
        CamundaService camundaService = endpoint.getComponent().getCamundaService();
        return camundaService.failJob(message);
    }

    private JobResponse updateJobRetries(JobRequest message) {
        CamundaService camundaService = endpoint.getComponent().getCamundaService();
        return camundaService.updateJobRetries(message);
    }

    private JobResponse throwError(JobRequest message) {
        CamundaService camundaService = endpoint.getComponent().getCamundaService();
        return camundaService.throwError(message);
    }

    @SuppressWarnings("unchecked")
    private JobRequest buildFromHeaders(Exchange exchange, long jobKey) {
        JobRequest request = new JobRequest();
        request.setJobKey(jobKey);

        String errorCode = exchange.getMessage().getHeader(CamundaConstants.ERROR_CODE, String.class);
        if (errorCode != null) {
            request.setErrorCode(errorCode);
        }
        String errorMessage = exchange.getMessage().getHeader(CamundaConstants.ERROR_MESSAGE, String.class);
        if (errorMessage != null) {
            request.setErrorMessage(errorMessage);
            request.setFailMessage(errorMessage);
        }

        Object body = exchange.getMessage().getBody();
        if (body instanceof Map) {
            request.setVariables((Map<String, Object>) body);
        }

        return request;
    }
}
