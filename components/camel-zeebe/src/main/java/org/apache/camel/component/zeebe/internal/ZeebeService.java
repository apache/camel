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

package org.apache.camel.component.zeebe.internal;

import java.time.Duration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.apache.camel.component.zeebe.model.DeploymentRequest;
import org.apache.camel.component.zeebe.model.DeploymentResponse;
import org.apache.camel.component.zeebe.model.JobRequest;
import org.apache.camel.component.zeebe.model.JobResponse;
import org.apache.camel.component.zeebe.model.MessageRequest;
import org.apache.camel.component.zeebe.model.MessageResponse;
import org.apache.camel.component.zeebe.model.ProcessDeploymentResponse;
import org.apache.camel.component.zeebe.model.ProcessRequest;
import org.apache.camel.component.zeebe.model.ProcessResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZeebeService {
    private static final Logger LOG = LoggerFactory.getLogger(ZeebeService.class);

    private ZeebeClient zeebeClient;
    private ManagedChannel managedChannel;

    private ObjectMapper objectMapper;

    private String gatewayHost;
    private int gatewayPort;
    private String clientId;
    private String clientSecret;
    private String oAuthAPI;

    public ZeebeService(String gatewayHost, int gatewayPort) {
        this.gatewayHost = gatewayHost;
        this.gatewayPort = gatewayPort;

        objectMapper = new ObjectMapper();
    }

    public void doStart() {
        String gatewayAddress = String.format("%s:%d", gatewayHost, gatewayPort);

        if (zeebeClient == null) {
            if (clientId != null) {
                OAuthCredentialsProvider credentialsProvider = new OAuthCredentialsProviderBuilder()
                        .authorizationServerUrl(oAuthAPI)
                        .audience(gatewayAddress)
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .build();

                zeebeClient = ZeebeClient.newClientBuilder()
                        .gatewayAddress(gatewayAddress)
                        .credentialsProvider(credentialsProvider)
                        .build();
            } else {
                zeebeClient = ZeebeClient.newClientBuilder()
                        .gatewayAddress(gatewayAddress)
                        .usePlaintext()
                        .build();
            }
        }
        if (managedChannel == null) {
            managedChannel = ManagedChannelBuilder.forAddress(gatewayHost, gatewayPort)
                    .usePlaintext()
                    .build();
        }
    }

    public void doStop() {
        if (zeebeClient != null) {
            zeebeClient.close();
            zeebeClient = null;
        }
        if (managedChannel != null) {
            managedChannel.shutdown();
        }
    }

    public ProcessResponse startProcess(ProcessRequest processMessage) {
        ProcessResponse resultMessage = new ProcessResponse();
        resultMessage.setProcessId(processMessage.getProcessId());

        try {
            ProcessInstanceEvent processInstanceEvent = zeebeClient
                    .newCreateInstanceCommand()
                    .bpmnProcessId(processMessage.getProcessId())
                    .version(processMessage.getProcessVersion())
                    .variables(processMessage.getVariables())
                    .send()
                    .join();

            resultMessage.setProcessId(processInstanceEvent.getBpmnProcessId());
            resultMessage.setProcessKey(processInstanceEvent.getProcessDefinitionKey());
            resultMessage.setProcessVersion(processInstanceEvent.getVersion());
            resultMessage.setProcessInstanceKey(processInstanceEvent.getProcessInstanceKey());
            resultMessage.setSuccess(true);
        } catch (ClientStatusException exception) {
            LOG.error("Zeebe Error", exception);
            resultMessage.setProcessVersion(processMessage.getProcessVersion());
            resultMessage.setErrorMessage(exception.getMessage());
            resultMessage.setErrorCode(exception.getStatusCode().toString());
            resultMessage.setSuccess(false);
        }

        return resultMessage;
    }

    public ProcessResponse cancelProcessInstance(ProcessRequest processMessage) {
        ProcessResponse resultMessage = new ProcessResponse();
        resultMessage.setProcessInstanceKey(processMessage.getProcessInstanceKey());

        try {
            GatewayGrpc.GatewayBlockingStub stub = GatewayGrpc.newBlockingStub(managedChannel);
            GatewayOuterClass.CancelProcessInstanceResponse cancelProcessInstanceResponse
                    = stub.cancelProcessInstance(GatewayOuterClass.CancelProcessInstanceRequest.newBuilder()
                            .setProcessInstanceKey(processMessage.getProcessInstanceKey())
                            .build());

            resultMessage.setSuccess(true);
        } catch (StatusRuntimeException exception) {
            LOG.error(String.format("Cannot cancel process instance %s", processMessage.getProcessId()), exception);
            resultMessage.setErrorMessage(exception.getMessage());
            resultMessage.setErrorCode(exception.getStatus().toString());
            resultMessage.setSuccess(false);
        }

        return resultMessage;
    }

    public MessageResponse publishMessage(MessageRequest message) {
        MessageResponse resultMessage = new MessageResponse();
        resultMessage.setCorrelationKey(message.getCorrelationKey());

        try {
            GatewayGrpc.GatewayBlockingStub stub = GatewayGrpc.newBlockingStub(managedChannel);
            if (message.getCorrelationKey() == null) {
                LOG.error("Correlation Key is missing!");
                resultMessage.setSuccess(false);
                resultMessage.setErrorMessage("Correlation Key is missing!");
                return resultMessage;
            }
            GatewayOuterClass.PublishMessageRequest.Builder builder = GatewayOuterClass.PublishMessageRequest.newBuilder()
                    .setCorrelationKey(message.getCorrelationKey());
            if (message.getTimeToLive() >= 0) {
                builder = builder.setTimeToLive(message.getTimeToLive());
            }
            if (message.getName() != null) {
                builder = builder.setName(message.getName());
            }
            if (!message.getVariables().isEmpty()) {
                builder = builder.setVariables(objectMapper.writeValueAsString(message.getVariables()));
            }
            GatewayOuterClass.PublishMessageRequest request = builder.build();
            GatewayOuterClass.PublishMessageResponse publishMessageResponse = stub.publishMessage(request);

            resultMessage.setMessageKey(publishMessageResponse.getKey());
            resultMessage.setSuccess(true);
        } catch (StatusRuntimeException exception) {
            LOG.error(String.format("Cannot publish message %s", message.getCorrelationKey()), exception);
            resultMessage.setErrorMessage(exception.getMessage());
            resultMessage.setErrorCode(exception.getStatus().toString());
            resultMessage.setSuccess(false);
        } catch (JsonProcessingException exception) {
            LOG.error("Could not convert variables to JSON", exception);
            resultMessage.setErrorMessage(exception.getMessage());
            resultMessage.setSuccess(false);
        }

        return resultMessage;
    }

    public JobResponse completeJob(JobRequest message) {
        JobResponse resultMessage = new JobResponse();

        try {
            GatewayGrpc.GatewayBlockingStub stub = GatewayGrpc.newBlockingStub(managedChannel);
            GatewayOuterClass.CompleteJobRequest.Builder builder = GatewayOuterClass.CompleteJobRequest.newBuilder()
                    .setJobKey(message.getJobKey());
            if (!message.getVariables().isEmpty()) {
                builder = builder.setVariables(objectMapper.writeValueAsString(message.getVariables()));
            }
            GatewayOuterClass.CompleteJobRequest request = builder.build();
            GatewayOuterClass.CompleteJobResponse completeJobResponse = stub.completeJob(request);

            resultMessage.setSuccess(true);
        } catch (StatusRuntimeException exception) {
            LOG.error(String.format("Cannot complete Job %s", message.getJobKey()), exception);
            resultMessage.setErrorMessage(exception.getMessage());
            resultMessage.setErrorCode(exception.getStatus().toString());
            resultMessage.setSuccess(false);
        } catch (JsonProcessingException exception) {
            LOG.error("Could not convert variables to JSON", exception);
            resultMessage.setErrorMessage(exception.getMessage());
            resultMessage.setSuccess(false);
        }

        return resultMessage;
    }

    public JobResponse failJob(JobRequest message) {
        JobResponse resultMessage = new JobResponse();

        try {
            GatewayGrpc.GatewayBlockingStub stub = GatewayGrpc.newBlockingStub(managedChannel);
            GatewayOuterClass.FailJobRequest.Builder builder = GatewayOuterClass.FailJobRequest.newBuilder()
                    .setJobKey(message.getJobKey());
            builder = builder.setRetries(message.getRetries());
            builder = builder.setErrorMessage(message.getFailMessage());
            GatewayOuterClass.FailJobRequest request = builder.build();
            GatewayOuterClass.FailJobResponse failJobResponse = stub.failJob(request);

            resultMessage.setSuccess(true);
        } catch (StatusRuntimeException exception) {
            LOG.error(String.format("Cannot fail Job %s", message.getJobKey()), exception);
            resultMessage.setErrorMessage(exception.getMessage());
            resultMessage.setErrorCode(exception.getStatus().toString());
            resultMessage.setSuccess(false);
        }

        return resultMessage;
    }

    public JobResponse updateJobRetries(JobRequest message) {
        JobResponse resultMessage = new JobResponse();

        try {
            GatewayGrpc.GatewayBlockingStub stub = GatewayGrpc.newBlockingStub(managedChannel);
            GatewayOuterClass.UpdateJobRetriesRequest.Builder builder = GatewayOuterClass.UpdateJobRetriesRequest.newBuilder()
                    .setJobKey(message.getJobKey());
            builder = builder.setRetries(message.getRetries());
            GatewayOuterClass.UpdateJobRetriesRequest request = builder.build();
            GatewayOuterClass.UpdateJobRetriesResponse updateJobRetriesResponse = stub.updateJobRetries(request);

            resultMessage.setSuccess(true);
        } catch (StatusRuntimeException exception) {
            LOG.error(String.format("Cannot update retries for Job %s", message.getJobKey()), exception);
            resultMessage.setErrorMessage(exception.getMessage());
            resultMessage.setErrorCode(exception.getStatus().toString());
            resultMessage.setSuccess(false);
        }

        return resultMessage;
    }

    public JobResponse throwError(JobRequest message) {
        JobResponse resultMessage = new JobResponse();

        try {
            GatewayGrpc.GatewayBlockingStub stub = GatewayGrpc.newBlockingStub(managedChannel);
            GatewayOuterClass.ThrowErrorRequest.Builder builder = GatewayOuterClass.ThrowErrorRequest.newBuilder()
                    .setJobKey(message.getJobKey());
            builder = builder.setErrorMessage(message.getErrorMessage());
            builder = builder.setErrorCode(message.getErrorCode());
            GatewayOuterClass.ThrowErrorRequest request = builder.build();
            GatewayOuterClass.ThrowErrorResponse failJobResponse = stub.throwError(request);

            resultMessage.setSuccess(true);
        } catch (StatusRuntimeException exception) {
            LOG.error(String.format("Cannot fail Job %s", message.getJobKey()), exception);
            resultMessage.setErrorMessage(exception.getMessage());
            resultMessage.setErrorCode(exception.getStatus().toString());
            resultMessage.setSuccess(false);
        }

        return resultMessage;
    }

    public DeploymentResponse deployResource(DeploymentRequest message) {
        DeploymentResponse resultMessage = new DeploymentResponse();

        try {
            GatewayGrpc.GatewayBlockingStub stub = GatewayGrpc.newBlockingStub(managedChannel);
            GatewayOuterClass.Resource resource = GatewayOuterClass.Resource.newBuilder()
                    .setName(message.getName())
                    .setContent(ByteString.copyFrom(message.getContent()))
                    .build();
            GatewayOuterClass.DeployResourceRequest.Builder builder = GatewayOuterClass.DeployResourceRequest.newBuilder()
                    .addResources(resource);
            GatewayOuterClass.DeployResourceRequest request = builder.build();
            GatewayOuterClass.DeployResourceResponse deploymentResourceResponse = stub.deployResource(request);

            int deploymentsCount = deploymentResourceResponse.getDeploymentsCount();
            if (deploymentsCount != 1) {
                LOG.error(String.format("Cannot deploy resource %s. Incorrect number of deployments returned.",
                        message.getName()));
                resultMessage.setErrorMessage(String
                        .format("Cannot deploy resource %s. Incorrect number of deployments returned.", message.getName()));
                resultMessage.setErrorCode("UNKNOWN ERROR");
                resultMessage.setSuccess(false);
                return resultMessage;
            }
            GatewayOuterClass.Deployment deployment = deploymentResourceResponse.getDeployments(0);
            switch (deployment.getMetadataCase()) {
                case PROCESS:
                    resultMessage = new ProcessDeploymentResponse();
                    ((ProcessDeploymentResponse) resultMessage).setBpmnProcessId(deployment.getProcess().getBpmnProcessId());
                    ((ProcessDeploymentResponse) resultMessage).setResourceName(deployment.getProcess().getResourceName());
                    ((ProcessDeploymentResponse) resultMessage)
                            .setProcessDefinitionKey(deployment.getProcess().getProcessDefinitionKey());
                    ((ProcessDeploymentResponse) resultMessage).setVersion(deployment.getProcess().getVersion());
                    break;
                default:
                    LOG.error(String.format("Unknown Metadata Case %s.", message.getName()));
                    resultMessage.setErrorMessage(String
                            .format("Cannot deploy resource %s. Incorrect number of deployments returned.", message.getName()));
                    resultMessage.setErrorCode("UNKNOWN ERROR");
                    resultMessage.setSuccess(false);
                    return resultMessage;
            }

            resultMessage.setSuccess(true);
        } catch (StatusRuntimeException exception) {
            LOG.error(String.format("Cannot deploy resource %s", message.getName()), exception);
            resultMessage.setErrorMessage(exception.getMessage());
            resultMessage.setErrorCode(exception.getStatus().toString());
            resultMessage.setSuccess(false);
        }

        return resultMessage;
    }

    public JobWorker registerJobHandler(JobHandler handler, String jobType, int timeout) {
        return zeebeClient.newWorker().jobType(jobType).handler(handler).timeout(Duration.ofSeconds(timeout)).open();
    }
}
