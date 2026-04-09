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
package org.apache.camel.component.camunda.internal;

import java.time.Duration;
import java.util.List;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.response.PublishMessageResponse;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.api.worker.JobWorker;
import org.apache.camel.component.camunda.model.DeploymentRequest;
import org.apache.camel.component.camunda.model.DeploymentResponse;
import org.apache.camel.component.camunda.model.JobRequest;
import org.apache.camel.component.camunda.model.JobResponse;
import org.apache.camel.component.camunda.model.MessageRequest;
import org.apache.camel.component.camunda.model.MessageResponse;
import org.apache.camel.component.camunda.model.ProcessDeploymentResponse;
import org.apache.camel.component.camunda.model.ProcessRequest;
import org.apache.camel.component.camunda.model.ProcessResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaService {
    private static final Logger LOG = LoggerFactory.getLogger(CamundaService.class);

    private final CamundaClient client;

    public CamundaService(CamundaClient client) {
        this.client = client;
    }

    public void doStop() {
        if (client != null) {
            client.close();
        }
    }

    public ProcessResponse startProcess(ProcessRequest processMessage) {
        ProcessResponse resultMessage = new ProcessResponse();
        resultMessage.setProcessId(processMessage.getProcessId());

        try {
            CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 step3;

            CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep2 step2
                    = client.newCreateInstanceCommand()
                            .bpmnProcessId(processMessage.getProcessId());

            if (processMessage.getProcessVersion() > 0) {
                step3 = step2.version(processMessage.getProcessVersion());
            } else {
                step3 = step2.latestVersion();
            }

            if (processMessage.getVariables() != null && !processMessage.getVariables().isEmpty()) {
                step3.variables(processMessage.getVariables());
            }

            ProcessInstanceEvent event = step3.send().join();

            resultMessage.setProcessId(event.getBpmnProcessId());
            resultMessage.setProcessKey(event.getProcessDefinitionKey());
            resultMessage.setProcessVersion(event.getVersion());
            resultMessage.setProcessInstanceKey(event.getProcessInstanceKey());
            resultMessage.setSuccess(true);
        } catch (Exception exception) {
            LOG.error("Camunda Error", exception);
            resultMessage.setProcessVersion(processMessage.getProcessVersion());
            resultMessage.setErrorMessage(exception.getMessage());
            resultMessage.setSuccess(false);
        }

        return resultMessage;
    }

    public ProcessResponse cancelProcessInstance(ProcessRequest processMessage) {
        ProcessResponse resultMessage = new ProcessResponse();
        resultMessage.setProcessInstanceKey(processMessage.getProcessInstanceKey());

        try {
            client.newCancelInstanceCommand(processMessage.getProcessInstanceKey())
                    .send()
                    .join();

            resultMessage.setSuccess(true);
        } catch (Exception exception) {
            LOG.error("Cannot cancel process instance {}", processMessage.getProcessInstanceKey(), exception);
            resultMessage.setErrorMessage(exception.getMessage());
            resultMessage.setSuccess(false);
        }

        return resultMessage;
    }

    public MessageResponse publishMessage(MessageRequest message) {
        MessageResponse resultMessage = new MessageResponse();
        resultMessage.setCorrelationKey(message.getCorrelationKey());

        try {
            if (message.getCorrelationKey() == null) {
                LOG.error("Correlation Key is missing!");
                resultMessage.setSuccess(false);
                resultMessage.setErrorMessage("Correlation Key is missing!");
                return resultMessage;
            }

            var cmd = client.newPublishMessageCommand()
                    .messageName(message.getName())
                    .correlationKey(message.getCorrelationKey());

            if (message.getTimeToLive() >= 0) {
                cmd.timeToLive(Duration.ofMillis(message.getTimeToLive()));
            }
            if (message.getMessageId() != null) {
                cmd.messageId(message.getMessageId());
            }
            if (!message.getVariables().isEmpty()) {
                cmd.variables(message.getVariables());
            }

            PublishMessageResponse response = cmd.send().join();
            resultMessage.setMessageKey(response.getMessageKey());
            resultMessage.setSuccess(true);
        } catch (Exception exception) {
            LOG.error("Cannot publish message {}", message.getCorrelationKey(), exception);
            resultMessage.setErrorMessage(exception.getMessage());
            resultMessage.setSuccess(false);
        }

        return resultMessage;
    }

    public JobResponse completeJob(JobRequest message) {
        JobResponse resultMessage = new JobResponse();

        try {
            var cmd = client.newCompleteCommand(message.getJobKey());
            if (!message.getVariables().isEmpty()) {
                cmd.variables(message.getVariables());
            }
            cmd.send().join();

            resultMessage.setSuccess(true);
        } catch (Exception exception) {
            LOG.error("Cannot complete Job {}", message.getJobKey(), exception);
            resultMessage.setErrorMessage(exception.getMessage());
            resultMessage.setSuccess(false);
        }

        return resultMessage;
    }

    public JobResponse failJob(JobRequest message) {
        JobResponse resultMessage = new JobResponse();

        try {
            var cmd = client.newFailCommand(message.getJobKey())
                    .retries(message.getRetries());
            if (message.getFailMessage() != null) {
                cmd.errorMessage(message.getFailMessage());
            }
            cmd.send().join();

            resultMessage.setSuccess(true);
        } catch (Exception exception) {
            LOG.error("Cannot fail Job {}", message.getJobKey(), exception);
            resultMessage.setErrorMessage(exception.getMessage());
            resultMessage.setSuccess(false);
        }

        return resultMessage;
    }

    public JobResponse updateJobRetries(JobRequest message) {
        JobResponse resultMessage = new JobResponse();

        try {
            client.newUpdateRetriesCommand(message.getJobKey())
                    .retries(message.getRetries())
                    .send()
                    .join();

            resultMessage.setSuccess(true);
        } catch (Exception exception) {
            LOG.error("Cannot update retries for Job {}", message.getJobKey(), exception);
            resultMessage.setErrorMessage(exception.getMessage());
            resultMessage.setSuccess(false);
        }

        return resultMessage;
    }

    public JobResponse throwError(JobRequest message) {
        JobResponse resultMessage = new JobResponse();

        try {
            var cmd = client.newThrowErrorCommand(message.getJobKey())
                    .errorCode(message.getErrorCode());
            if (message.getErrorMessage() != null) {
                cmd.errorMessage(message.getErrorMessage());
            }
            cmd.send().join();

            resultMessage.setSuccess(true);
        } catch (Exception exception) {
            LOG.error("Cannot throw error for Job {}", message.getJobKey(), exception);
            resultMessage.setErrorMessage(exception.getMessage());
            resultMessage.setSuccess(false);
        }

        return resultMessage;
    }

    public DeploymentResponse deployResource(DeploymentRequest message) {
        DeploymentResponse resultMessage = new DeploymentResponse();

        try {
            DeploymentEvent event = client.newDeployResourceCommand()
                    .addResourceBytes(message.getFileContent(), message.getName())
                    .send()
                    .join();

            List<Process> processes = event.getProcesses();
            if (!processes.isEmpty()) {
                Process process = processes.get(0);
                ProcessDeploymentResponse processResult = new ProcessDeploymentResponse();
                processResult.setBpmnProcessId(process.getBpmnProcessId());
                processResult.setResourceName(process.getResourceName());
                processResult.setProcessDefinitionKey(process.getProcessDefinitionKey());
                processResult.setVersion(process.getVersion());
                processResult.setSuccess(true);
                return processResult;
            }

            resultMessage.setSuccess(true);
        } catch (Exception exception) {
            LOG.error("Cannot deploy resource {}", message.getName(), exception);
            resultMessage.setErrorMessage(exception.getMessage());
            resultMessage.setSuccess(false);
        }

        return resultMessage;
    }

    public JobWorker registerJobHandler(JobHandler handler, String jobType, int timeout) {
        return client.newWorker().jobType(jobType).handler(handler).timeout(Duration.ofSeconds(timeout)).open();
    }
}
