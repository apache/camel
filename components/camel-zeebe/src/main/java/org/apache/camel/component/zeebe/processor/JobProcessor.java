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

package org.apache.camel.component.zeebe.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.component.zeebe.ZeebeConstants;
import org.apache.camel.component.zeebe.ZeebeEndpoint;
import org.apache.camel.component.zeebe.internal.ZeebeService;
import org.apache.camel.component.zeebe.model.JobRequest;
import org.apache.camel.component.zeebe.model.JobResponse;
import org.apache.camel.component.zeebe.model.JobWorkerMessage;

public class JobProcessor extends AbstractBaseProcessor {
    public JobProcessor(ZeebeEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        JobRequest message = null;

        Long headerJobKey = exchange.getMessage().getHeader(ZeebeConstants.JOB_KEY, Long.class);
        if (exchange.getMessage().getBody() instanceof JobRequest) {
            message = exchange.getMessage().getBody(JobRequest.class);
        } else if (exchange.getMessage().getBody() instanceof String) {
            try {
                String bodyString = exchange.getMessage().getBody(String.class);

                message = objectMapper.readValue(bodyString, JobRequest.class);
            } catch (JsonProcessingException jsonProcessingException) {
                throw new IllegalArgumentException("Cannot convert body to JobMessage", jsonProcessingException);
            }
        } else if (exchange.getMessage().getBody() instanceof JobWorkerMessage) {
            JobWorkerMessage jobWorkerMessage = exchange.getMessage().getBody(JobWorkerMessage.class);

            message = new JobRequest();
            message.setJobKey(jobWorkerMessage.getKey());
        } else if (headerJobKey != null) {
            message = new JobRequest();
            message.setJobKey(headerJobKey);
        } else {
            throw new CamelException("Job data missing");
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

        setBody(exchange, resultMessage, endpoint.isFormatJSON());
    }

    private JobResponse completeJob(JobRequest message) {
        ZeebeService zeebeService = endpoint.getComponent().getZeebeService();

        return zeebeService.completeJob(message);
    }

    private JobResponse failJob(JobRequest message) {
        ZeebeService zeebeService = endpoint.getComponent().getZeebeService();

        return zeebeService.failJob(message);
    }

    private JobResponse updateJobRetries(JobRequest message) {
        ZeebeService zeebeService = endpoint.getComponent().getZeebeService();

        return zeebeService.updateJobRetries(message);
    }

    private JobResponse throwError(JobRequest message) {
        ZeebeService zeebeService = endpoint.getComponent().getZeebeService();

        return zeebeService.throwError(message);
    }
}
