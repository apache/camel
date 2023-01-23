/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.camel.component.zeebe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import org.apache.camel.*;
import org.apache.camel.component.zeebe.internal.OperationName;
import org.apache.camel.component.zeebe.model.JobWorkerMessage;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

public class ZeebeConsumer extends DefaultConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(ZeebeConsumer.class);
    private final ZeebeEndpoint endpoint;

    private ExecutorService executorService;

    ObjectMapper objectMapper = new ObjectMapper();
    private JobWorker jobWorker;

    public ZeebeConsumer(ZeebeEndpoint endpoint, Processor processor) throws CamelException {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        final OperationName operationName = getEndpoint().getOperationName();
        switch (operationName) {
            case REGISTER_JOB_WORKER:
                if (getEndpoint().getJobKey() == null) {
                    LOG.error("Missing JobKey");
                    throw new CamelException("Missing JobKey");
                }
                jobWorker = getEndpoint().getZeebeService().registerJobHandler(new ConsumerJobHandler(), getEndpoint().getJobKey(), getEndpoint().getTimeout());
                break;
            default:
                LOG.error("Invalid Operation %s", operationName.value());
                throw new CamelException(String.format("Invalid Operation for Consumer %s", operationName.value()));
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if ((jobWorker != null) && jobWorker.isOpen()) {
            jobWorker.close();
        }
    }

    private class ConsumerJobHandler implements JobHandler {
        @Override
        public void handle(JobClient client, ActivatedJob job) throws Exception {
            final Exchange exchange = createExchange(true);
            final Message in = exchange.getIn();

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

            LOG.info(job.toJson());

            if (getEndpoint().isFormatJSON()) {
                try {
                    exchange.getIn().setBody(objectMapper.writeValueAsString(message));
                } catch (JsonProcessingException jsonProcessingException) {
                    throw new IllegalArgumentException("Cannot convert result", jsonProcessingException);
                }
            } else {
                exchange.getIn().setBody(message);
            }

            AsyncCallback cb = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, cb);
        }
    }

    @Override
    public ZeebeEndpoint getEndpoint() {
        return endpoint;
    }
}
