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
package org.apache.camel.component.aws.parameterstore;

import java.util.Arrays;
import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.DeleteParameterRequest;
import software.amazon.awssdk.services.ssm.model.DeleteParameterResponse;
import software.amazon.awssdk.services.ssm.model.DeleteParametersRequest;
import software.amazon.awssdk.services.ssm.model.DeleteParametersResponse;
import software.amazon.awssdk.services.ssm.model.DescribeParametersRequest;
import software.amazon.awssdk.services.ssm.model.DescribeParametersResponse;
import software.amazon.awssdk.services.ssm.model.GetParameterHistoryRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterHistoryResponse;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.GetParametersRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersResponse;
import software.amazon.awssdk.services.ssm.model.ParameterTier;
import software.amazon.awssdk.services.ssm.model.ParameterType;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;
import software.amazon.awssdk.services.ssm.model.PutParameterResponse;

/**
 * A Producer which sends messages to the Amazon SSM Parameter Store SDK v2
 * <a href="https://aws.amazon.com/systems-manager/features/#Parameter_Store">AWS Parameter Store</a>
 */
public class ParameterStoreProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(ParameterStoreProducer.class);

    private transient String parameterStoreProducerToString;
    private WritableHealthCheckRepository healthCheckRepository;
    private HealthCheck producerHealthCheck;

    public ParameterStoreProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case getParameter:
                getParameter(getEndpoint().getSsmClient(), exchange);
                break;
            case getParameters:
                getParameters(getEndpoint().getSsmClient(), exchange);
                break;
            case getParametersByPath:
                getParametersByPath(getEndpoint().getSsmClient(), exchange);
                break;
            case putParameter:
                putParameter(getEndpoint().getSsmClient(), exchange);
                break;
            case deleteParameter:
                deleteParameter(getEndpoint().getSsmClient(), exchange);
                break;
            case deleteParameters:
                deleteParameters(getEndpoint().getSsmClient(), exchange);
                break;
            case describeParameters:
                describeParameters(getEndpoint().getSsmClient(), exchange);
                break;
            case getParameterHistory:
                getParameterHistory(getEndpoint().getSsmClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private ParameterStoreOperations determineOperation(Exchange exchange) {
        ParameterStoreOperations operation = exchange.getIn().getHeader(ParameterStoreConstants.OPERATION,
                ParameterStoreOperations.class);
        if (ObjectHelper.isEmpty(operation)) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected ParameterStoreConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (ObjectHelper.isEmpty(parameterStoreProducerToString)) {
            parameterStoreProducerToString = "ParameterStoreProducer["
                                             + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return parameterStoreProducerToString;
    }

    @Override
    public ParameterStoreEndpoint getEndpoint() {
        return (ParameterStoreEndpoint) super.getEndpoint();
    }

    private void getParameter(SsmClient ssmClient, Exchange exchange)
            throws InvalidPayloadException {
        GetParameterRequest request;
        GetParameterResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(GetParameterRequest.class);
        } else {
            GetParameterRequest.Builder builder = GetParameterRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_NAME))) {
                String parameterName = exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_NAME, String.class);
                builder.name(parameterName);
            } else {
                throw new IllegalArgumentException("Parameter Name must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.WITH_DECRYPTION))) {
                Boolean withDecryption = exchange.getIn().getHeader(ParameterStoreConstants.WITH_DECRYPTION, Boolean.class);
                builder.withDecryption(withDecryption);
            }
            request = builder.build();
        }
        try {
            result = ssmClient.getParameter(request);
        } catch (AwsServiceException ase) {
            LOG.trace("Get Parameter command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result.parameter().value());
        if (ObjectHelper.isNotEmpty(result.parameter().version())) {
            exchange.getMessage().setHeader(ParameterStoreConstants.PARAMETER_VERSION, result.parameter().version());
        }
    }

    private void getParameters(SsmClient ssmClient, Exchange exchange)
            throws InvalidPayloadException {
        GetParametersRequest request;
        GetParametersResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(GetParametersRequest.class);
        } else {
            GetParametersRequest.Builder builder = GetParametersRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_NAMES))) {
                List<String> parameterNames = Arrays.asList(
                        exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_NAMES, String.class).split(","));
                builder.names(parameterNames);
            } else {
                throw new IllegalArgumentException("Parameter Names must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.WITH_DECRYPTION))) {
                Boolean withDecryption = exchange.getIn().getHeader(ParameterStoreConstants.WITH_DECRYPTION, Boolean.class);
                builder.withDecryption(withDecryption);
            }
            request = builder.build();
        }
        try {
            result = ssmClient.getParameters(request);
        } catch (AwsServiceException ase) {
            LOG.trace("Get Parameters command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void getParametersByPath(SsmClient ssmClient, Exchange exchange)
            throws InvalidPayloadException {
        GetParametersByPathRequest request;
        GetParametersByPathResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(GetParametersByPathRequest.class);
        } else {
            GetParametersByPathRequest.Builder builder = GetParametersByPathRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_PATH))) {
                String path = exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_PATH, String.class);
                builder.path(path);
            } else {
                throw new IllegalArgumentException("Parameter Path must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.WITH_DECRYPTION))) {
                Boolean withDecryption = exchange.getIn().getHeader(ParameterStoreConstants.WITH_DECRYPTION, Boolean.class);
                builder.withDecryption(withDecryption);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.RECURSIVE))) {
                Boolean recursive = exchange.getIn().getHeader(ParameterStoreConstants.RECURSIVE, Boolean.class);
                builder.recursive(recursive);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.MAX_RESULTS))) {
                Integer maxResults = exchange.getIn().getHeader(ParameterStoreConstants.MAX_RESULTS, Integer.class);
                builder.maxResults(maxResults);
            }
            request = builder.build();
        }
        try {
            result = ssmClient.getParametersByPath(request);
        } catch (AwsServiceException ase) {
            LOG.trace("Get Parameters By Path command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void putParameter(SsmClient ssmClient, Exchange exchange)
            throws InvalidPayloadException {
        PutParameterRequest request;
        PutParameterResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(PutParameterRequest.class);
        } else {
            PutParameterRequest.Builder builder = PutParameterRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_NAME))) {
                String parameterName = exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_NAME, String.class);
                builder.name(parameterName);
            } else {
                throw new IllegalArgumentException("Parameter Name must be specified");
            }
            String payload = exchange.getIn().getMandatoryBody(String.class);
            builder.value(payload);
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_TYPE))) {
                String type = exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_TYPE, String.class);
                builder.type(ParameterType.fromValue(type));
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_DESCRIPTION))) {
                String description = exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_DESCRIPTION, String.class);
                builder.description(description);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.OVERWRITE))) {
                Boolean overwrite = exchange.getIn().getHeader(ParameterStoreConstants.OVERWRITE, Boolean.class);
                builder.overwrite(overwrite);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.KMS_KEY_ID))) {
                String kmsKeyId = exchange.getIn().getHeader(ParameterStoreConstants.KMS_KEY_ID, String.class);
                builder.keyId(kmsKeyId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_TIER))) {
                String tier = exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_TIER, String.class);
                builder.tier(ParameterTier.fromValue(tier));
            }
            request = builder.build();
        }
        try {
            result = ssmClient.putParameter(request);
        } catch (AwsServiceException ase) {
            LOG.trace("Put Parameter command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
        if (ObjectHelper.isNotEmpty(result.version())) {
            exchange.getMessage().setHeader(ParameterStoreConstants.PARAMETER_VERSION, result.version());
        }
    }

    private void deleteParameter(SsmClient ssmClient, Exchange exchange)
            throws InvalidPayloadException {
        DeleteParameterRequest request;
        DeleteParameterResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(DeleteParameterRequest.class);
        } else {
            DeleteParameterRequest.Builder builder = DeleteParameterRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_NAME))) {
                String parameterName = exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_NAME, String.class);
                builder.name(parameterName);
            } else {
                throw new IllegalArgumentException("Parameter Name must be specified");
            }
            request = builder.build();
        }
        try {
            result = ssmClient.deleteParameter(request);
        } catch (AwsServiceException ase) {
            LOG.trace("Delete Parameter command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteParameters(SsmClient ssmClient, Exchange exchange)
            throws InvalidPayloadException {
        DeleteParametersRequest request;
        DeleteParametersResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(DeleteParametersRequest.class);
        } else {
            DeleteParametersRequest.Builder builder = DeleteParametersRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_NAMES))) {
                List<String> parameterNames = Arrays.asList(
                        exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_NAMES, String.class).split(","));
                builder.names(parameterNames);
            } else {
                throw new IllegalArgumentException("Parameter Names must be specified");
            }
            request = builder.build();
        }
        try {
            result = ssmClient.deleteParameters(request);
        } catch (AwsServiceException ase) {
            LOG.trace("Delete Parameters command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void describeParameters(SsmClient ssmClient, Exchange exchange)
            throws InvalidPayloadException {
        DescribeParametersRequest request;
        DescribeParametersResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(DescribeParametersRequest.class);
        } else {
            DescribeParametersRequest.Builder builder = DescribeParametersRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.MAX_RESULTS))) {
                Integer maxResults = exchange.getIn().getHeader(ParameterStoreConstants.MAX_RESULTS, Integer.class);
                builder.maxResults(maxResults);
            }
            request = builder.build();
        }
        try {
            result = ssmClient.describeParameters(request);
        } catch (AwsServiceException ase) {
            LOG.trace("Describe Parameters command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void getParameterHistory(SsmClient ssmClient, Exchange exchange)
            throws InvalidPayloadException {
        GetParameterHistoryRequest request;
        GetParameterHistoryResponse result;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(GetParameterHistoryRequest.class);
        } else {
            GetParameterHistoryRequest.Builder builder = GetParameterHistoryRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_NAME))) {
                String parameterName = exchange.getIn().getHeader(ParameterStoreConstants.PARAMETER_NAME, String.class);
                builder.name(parameterName);
            } else {
                throw new IllegalArgumentException("Parameter Name must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.WITH_DECRYPTION))) {
                Boolean withDecryption = exchange.getIn().getHeader(ParameterStoreConstants.WITH_DECRYPTION, Boolean.class);
                builder.withDecryption(withDecryption);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ParameterStoreConstants.MAX_RESULTS))) {
                Integer maxResults = exchange.getIn().getHeader(ParameterStoreConstants.MAX_RESULTS, Integer.class);
                builder.maxResults(maxResults);
            }
            request = builder.build();
        }
        try {
            result = ssmClient.getParameterHistory(request);
        } catch (AwsServiceException ase) {
            LOG.trace("Get Parameter History command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    @Override
    protected void doStart() throws Exception {
        // health-check is optional so discover and resolve
        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                getEndpoint().getCamelContext(),
                "producers",
                WritableHealthCheckRepository.class);

        if (ObjectHelper.isNotEmpty(healthCheckRepository)) {
            String id = getEndpoint().getId();
            producerHealthCheck = new ParameterStoreProducerHealthCheck(getEndpoint(), id);
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (ObjectHelper.isNotEmpty(healthCheckRepository) && ObjectHelper.isNotEmpty(producerHealthCheck)) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }
    }

}
