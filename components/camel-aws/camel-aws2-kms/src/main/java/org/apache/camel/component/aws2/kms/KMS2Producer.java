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
package org.apache.camel.component.aws2.kms;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyRequest;
import software.amazon.awssdk.services.kms.model.ListKeysRequest;
import software.amazon.awssdk.services.kms.model.ListKeysResponse;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionRequest;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionResponse;

/**
 * A Producer which sends messages to the Amazon KMS Service <a href="http://aws.amazon.com/kms/">AWS KMS</a>
 */
public class KMS2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KMS2Producer.class);
    public static final String MISSING_KEY_ID = "Key Id must be specified";

    private transient String kmsProducerToString;

    public KMS2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case listKeys:
                listKeys(getEndpoint().getKmsClient(), exchange);
                break;
            case createKey:
                createKey(getEndpoint().getKmsClient(), exchange);
                break;
            case disableKey:
                disableKey(getEndpoint().getKmsClient(), exchange);
                break;
            case enableKey:
                enableKey(getEndpoint().getKmsClient(), exchange);
                break;
            case scheduleKeyDeletion:
                scheduleKeyDeletion(getEndpoint().getKmsClient(), exchange);
                break;
            case describeKey:
                describeKey(getEndpoint().getKmsClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private KMS2Operations determineOperation(Exchange exchange) {
        KMS2Operations operation = exchange.getIn().getHeader(KMS2Constants.OPERATION, KMS2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }

        if (ObjectHelper.isEmpty(operation)) {
            throw new IllegalArgumentException("Operation must be specified");
        }
        return operation;
    }

    protected KMS2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (kmsProducerToString == null) {
            kmsProducerToString = "KMSProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return kmsProducerToString;
    }

    @Override
    public KMS2Endpoint getEndpoint() {
        return (KMS2Endpoint) super.getEndpoint();
    }

    private void listKeys(KmsClient kmsClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                ListKeysRequest.class,
                kmsClient::listKeys,
                () -> {
                    ListKeysRequest.Builder builder = ListKeysRequest.builder();
                    Integer limit = getOptionalHeader(exchange, KMS2Constants.LIMIT, Integer.class);
                    if (limit != null) {
                        builder.limit(limit);
                    }
                    String marker = getOptionalHeader(exchange, KMS2Constants.MARKER, String.class);
                    if (marker != null) {
                        builder.marker(marker);
                    }
                    return kmsClient.listKeys(builder.build());
                },
                "List Keys",
                (ListKeysResponse response, Message message) -> {
                    message.setHeader(KMS2Constants.MARKER, response.nextMarker());
                    message.setHeader(KMS2Constants.IS_TRUNCATED, response.truncated());
                });
    }

    private void createKey(KmsClient kmsClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                CreateKeyRequest.class,
                kmsClient::createKey,
                () -> {
                    CreateKeyRequest.Builder builder = CreateKeyRequest.builder();
                    String description = getOptionalHeader(exchange, KMS2Constants.DESCRIPTION, String.class);
                    if (description != null) {
                        builder.description(description);
                    }
                    return kmsClient.createKey(builder.build());
                },
                "Create Key",
                (CreateKeyResponse response, Message message) -> {
                    if (response.keyMetadata() != null) {
                        message.setHeader(KMS2Constants.KEY_ID, response.keyMetadata().keyId());
                        message.setHeader(KMS2Constants.KEY_ARN, response.keyMetadata().arn());
                        message.setHeader(KMS2Constants.KEY_STATE, response.keyMetadata().keyStateAsString());
                    }
                });
    }

    private void disableKey(KmsClient kmsClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DisableKeyRequest.class,
                kmsClient::disableKey,
                () -> {
                    String keyId = getRequiredHeader(exchange, KMS2Constants.KEY_ID, String.class, MISSING_KEY_ID);
                    return kmsClient.disableKey(DisableKeyRequest.builder().keyId(keyId).build());
                },
                "Disable Key");
    }

    private void scheduleKeyDeletion(KmsClient kmsClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                ScheduleKeyDeletionRequest.class,
                kmsClient::scheduleKeyDeletion,
                () -> {
                    ScheduleKeyDeletionRequest.Builder builder = ScheduleKeyDeletionRequest.builder();
                    String keyId = getRequiredHeader(exchange, KMS2Constants.KEY_ID, String.class, MISSING_KEY_ID);
                    builder.keyId(keyId);
                    Integer pendingWindows = getOptionalHeader(exchange, KMS2Constants.PENDING_WINDOW_IN_DAYS, Integer.class);
                    if (pendingWindows != null) {
                        builder.pendingWindowInDays(pendingWindows);
                    }
                    return kmsClient.scheduleKeyDeletion(builder.build());
                },
                "Schedule Key Deletion",
                (ScheduleKeyDeletionResponse response, Message message) -> {
                    message.setHeader(KMS2Constants.KEY_ID, response.keyId());
                    message.setHeader(KMS2Constants.DELETION_DATE, response.deletionDate());
                });
    }

    private void describeKey(KmsClient kmsClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DescribeKeyRequest.class,
                kmsClient::describeKey,
                () -> {
                    String keyId = getRequiredHeader(exchange, KMS2Constants.KEY_ID, String.class, MISSING_KEY_ID);
                    return kmsClient.describeKey(DescribeKeyRequest.builder().keyId(keyId).build());
                },
                "Describe Key",
                (DescribeKeyResponse response, Message message) -> {
                    if (response.keyMetadata() != null) {
                        message.setHeader(KMS2Constants.KEY_ID, response.keyMetadata().keyId());
                        message.setHeader(KMS2Constants.KEY_ARN, response.keyMetadata().arn());
                        message.setHeader(KMS2Constants.KEY_STATE, response.keyMetadata().keyStateAsString());
                    }
                });
    }

    private void enableKey(KmsClient kmsClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                EnableKeyRequest.class,
                kmsClient::enableKey,
                () -> {
                    String keyId = getRequiredHeader(exchange, KMS2Constants.KEY_ID, String.class, MISSING_KEY_ID);
                    return kmsClient.enableKey(EnableKeyRequest.builder().keyId(keyId).build());
                },
                "Enable Key");
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    /**
     * Executes a KMS operation with POJO request support.
     */
    private <REQ, RES> void executeOperation(
            Exchange exchange,
            Class<REQ> requestClass,
            Function<REQ, RES> pojoExecutor,
            Supplier<RES> headerExecutor,
            String operationName)
            throws InvalidPayloadException {
        executeOperation(exchange, requestClass, pojoExecutor, headerExecutor, operationName, null);
    }

    /**
     * Executes a KMS operation with POJO request support and optional response post-processing.
     */
    private <REQ, RES> void executeOperation(
            Exchange exchange,
            Class<REQ> requestClass,
            Function<REQ, RES> pojoExecutor,
            Supplier<RES> headerExecutor,
            String operationName,
            BiConsumer<RES, Message> responseProcessor)
            throws InvalidPayloadException {

        RES result;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (requestClass.isInstance(payload)) {
                try {
                    result = pojoExecutor.apply(requestClass.cast(payload));
                } catch (AwsServiceException ase) {
                    LOG.trace("{} command returned the error code {}", operationName, ase.awsErrorDetails().errorCode());
                    throw ase;
                }
            } else {
                throw new IllegalArgumentException(
                        String.format("Expected body of type %s but was %s",
                                requestClass.getName(),
                                payload != null ? payload.getClass().getName() : "null"));
            }
        } else {
            try {
                result = headerExecutor.get();
            } catch (AwsServiceException ase) {
                LOG.trace("{} command returned the error code {}", operationName, ase.awsErrorDetails().errorCode());
                throw ase;
            }
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
        if (responseProcessor != null) {
            responseProcessor.accept(result, message);
        }
    }

    /**
     * Gets a required header value or throws an IllegalArgumentException.
     */
    private <T> T getRequiredHeader(Exchange exchange, String headerName, Class<T> headerType, String errorMessage) {
        T value = exchange.getIn().getHeader(headerName, headerType);
        if (ObjectHelper.isEmpty(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value;
    }

    /**
     * Gets an optional header value.
     */
    private <T> T getOptionalHeader(Exchange exchange, String headerName, Class<T> headerType) {
        return exchange.getIn().getHeader(headerName, headerType);
    }
}
