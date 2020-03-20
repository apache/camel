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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
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
import software.amazon.awssdk.services.kms.model.DisableKeyResponse;
import software.amazon.awssdk.services.kms.model.EnableKeyRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyResponse;
import software.amazon.awssdk.services.kms.model.ListKeysRequest;
import software.amazon.awssdk.services.kms.model.ListKeysResponse;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionRequest;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionResponse;

/**
 * A Producer which sends messages to the Amazon KMS Service
 * <a href="http://aws.amazon.com/kms/">AWS KMS</a>
 */
public class KMS2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KMS2Producer.class);

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
        return (KMS2Endpoint)super.getEndpoint();
    }

    private void listKeys(KmsClient kmsClient, Exchange exchange) {
        ListKeysRequest.Builder builder = ListKeysRequest.builder();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KMS2Constants.LIMIT))) {
            int limit = exchange.getIn().getHeader(KMS2Constants.LIMIT, Integer.class);
            builder.limit(limit);
        }
        ListKeysResponse result;
        try {
            result = kmsClient.listKeys(builder.build());
        } catch (AwsServiceException ase) {
            LOG.trace("List Keys command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void createKey(KmsClient kmsClient, Exchange exchange) {
        CreateKeyRequest.Builder builder = CreateKeyRequest.builder();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KMS2Constants.DESCRIPTION))) {
            String description = exchange.getIn().getHeader(KMS2Constants.DESCRIPTION, String.class);
            builder.description(description);
        }
        CreateKeyResponse result;
        try {
            result = kmsClient.createKey(builder.build());
        } catch (AwsServiceException ase) {
            LOG.trace("Create Key command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void disableKey(KmsClient kmsClient, Exchange exchange) {
        DisableKeyRequest.Builder builder = DisableKeyRequest.builder();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KMS2Constants.KEY_ID))) {
            String keyId = exchange.getIn().getHeader(KMS2Constants.KEY_ID, String.class);
            builder.keyId(keyId);
        } else {
            throw new IllegalArgumentException("Key Id must be specified");
        }
        DisableKeyResponse result;
        try {
            result = kmsClient.disableKey(builder.build());
        } catch (AwsServiceException ase) {
            LOG.trace("Disable Key command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void scheduleKeyDeletion(KmsClient kmsClient, Exchange exchange) {
        ScheduleKeyDeletionRequest.Builder builder = ScheduleKeyDeletionRequest.builder();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KMS2Constants.KEY_ID))) {
            String keyId = exchange.getIn().getHeader(KMS2Constants.KEY_ID, String.class);
            builder.keyId(keyId);
        } else {
            throw new IllegalArgumentException("Key Id must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KMS2Constants.PENDING_WINDOW_IN_DAYS))) {
            int pendingWindows = exchange.getIn().getHeader(KMS2Constants.PENDING_WINDOW_IN_DAYS, Integer.class);
            builder.pendingWindowInDays(pendingWindows);
        }
        ScheduleKeyDeletionResponse result;
        try {
            result = kmsClient.scheduleKeyDeletion(builder.build());
        } catch (AwsServiceException ase) {
            LOG.trace("Schedule Key Deletion command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void describeKey(KmsClient kmsClient, Exchange exchange) {
        DescribeKeyRequest.Builder builder = DescribeKeyRequest.builder();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KMS2Constants.KEY_ID))) {
            String keyId = exchange.getIn().getHeader(KMS2Constants.KEY_ID, String.class);
            builder.keyId(keyId);
        } else {
            throw new IllegalArgumentException("Key Id must be specified");
        }
        DescribeKeyResponse result;
        try {
            result = kmsClient.describeKey(builder.build());
        } catch (AwsServiceException ase) {
            LOG.trace("Describe Key command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void enableKey(KmsClient kmsClient, Exchange exchange) {
        EnableKeyRequest.Builder builder = EnableKeyRequest.builder();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KMS2Constants.KEY_ID))) {
            String keyId = exchange.getIn().getHeader(KMS2Constants.KEY_ID, String.class);
            builder.keyId(keyId);
        } else {
            throw new IllegalArgumentException("Key Id must be specified");
        }
        EnableKeyResponse result;
        try {
            result = kmsClient.enableKey(builder.build());
        } catch (AwsServiceException ase) {
            LOG.trace("Enable Key command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
