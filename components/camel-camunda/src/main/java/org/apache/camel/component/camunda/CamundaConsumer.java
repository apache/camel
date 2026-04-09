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

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.api.worker.JobWorker;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.camunda.internal.OperationName;
import org.apache.camel.component.camunda.model.JobWorkerMessage;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaConsumer extends DefaultConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(CamundaConsumer.class);

    private final CamundaEndpoint endpoint;

    private JobWorker jobWorker;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public CamundaConsumer(CamundaEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        final OperationName operationName = getEndpoint().getOperationName();
        switch (operationName) {
            case REGISTER_JOB_WORKER:
                ObjectHelper.notNull(getEndpoint().getJobType(), "jobType");

                jobWorker = getEndpoint().getCamundaService().registerJobHandler(new ConsumerJobHandler(),
                        getEndpoint().getJobType(), getEndpoint().getTimeout());
                break;
            default:
                throw new CamelException(String.format("Invalid Operation for Consumer %s", operationName.value()));
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (jobWorker != null && jobWorker.isOpen()) {
            jobWorker.close();
        }
    }

    private class ConsumerJobHandler implements JobHandler {
        @Override
        @SuppressWarnings("unchecked")
        public void handle(JobClient client, ActivatedJob job) throws Exception {
            final Exchange exchange = createExchange(false);

            JobWorkerMessage message = new JobWorkerMessage();
            message.setKey(job.getKey());
            message.setType(job.getType());
            message.setCustomHeaders(job.getCustomHeaders());
            message.setProcessInstanceKey(job.getProcessInstanceKey());
            message.setBpmnProcessId(job.getBpmnProcessId());
            message.setProcessDefinitionVersion(job.getProcessDefinitionVersion());
            message.setProcessDefinitionKey(job.getProcessDefinitionKey());
            message.setElementId(job.getElementId());
            message.setElementInstanceKey(job.getElementInstanceKey());
            message.setWorker(job.getWorker());
            message.setRetries(job.getRetries());
            message.setDeadline(job.getDeadline());
            message.setVariables(job.getVariablesAsMap());

            if (LOG.isDebugEnabled()) {
                LOG.debug("New Job Message: {}", job.toJson());
            }

            exchange.getMessage().setHeader(CamundaConstants.JOB_KEY, job.getKey());

            if (getEndpoint().isFormatJSON()) {
                try {
                    exchange.getMessage().setBody(objectMapper.writeValueAsString(message));
                } catch (JsonProcessingException jsonProcessingException) {
                    throw new IllegalArgumentException("Cannot convert result", jsonProcessingException);
                }
            } else {
                exchange.getMessage().setBody(message);
            }

            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }

            try {
                Boolean jobHandled = exchange.getProperty(CamundaConstants.JOB_HANDLED, Boolean.class);
                if (Boolean.TRUE.equals(jobHandled)) {
                    LOG.debug("Job {} already handled by route", job.getKey());
                } else if (exchange.getException() != null) {
                    LOG.debug("Auto-failing job {} due to: {}", job.getKey(), exchange.getException().getMessage());
                    int retries = Math.max(job.getRetries() - 1, 0);
                    client.newFailCommand(job.getKey())
                            .retries(retries)
                            .errorMessage(exchange.getException().getMessage())
                            .send()
                            .join();
                } else {
                    LOG.debug("Auto-completing job {}", job.getKey());
                    var cmd = client.newCompleteCommand(job.getKey());
                    Object body = exchange.getMessage().getBody();
                    if (body instanceof Map) {
                        cmd.variables((Map<String, Object>) body);
                    }
                    cmd.send().join();
                }
            } catch (Exception e) {
                getExceptionHandler().handleException("Error auto-completing/failing job " + job.getKey(), exchange, e);
            } finally {
                releaseExchange(exchange, false);
            }
        }
    }

    @Override
    public CamundaEndpoint getEndpoint() {
        return endpoint;
    }
}
