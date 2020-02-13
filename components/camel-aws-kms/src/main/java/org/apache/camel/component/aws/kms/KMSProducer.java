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
package org.apache.camel.component.aws.kms;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.model.CreateKeyRequest;
import com.amazonaws.services.kms.model.CreateKeyResult;
import com.amazonaws.services.kms.model.DescribeKeyRequest;
import com.amazonaws.services.kms.model.DescribeKeyResult;
import com.amazonaws.services.kms.model.DisableKeyRequest;
import com.amazonaws.services.kms.model.DisableKeyResult;
import com.amazonaws.services.kms.model.EnableKeyRequest;
import com.amazonaws.services.kms.model.EnableKeyResult;
import com.amazonaws.services.kms.model.ListKeysRequest;
import com.amazonaws.services.kms.model.ListKeysResult;
import com.amazonaws.services.kms.model.ScheduleKeyDeletionRequest;
import com.amazonaws.services.kms.model.ScheduleKeyDeletionResult;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sends messages to the Amazon KMS Service
 * <a href="http://aws.amazon.com/kms/">AWS KMS</a>
 */
public class KMSProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KMSProducer.class);

    private transient String kmsProducerToString;

    public KMSProducer(Endpoint endpoint) {
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

    private KMSOperations determineOperation(Exchange exchange) {
        KMSOperations operation = exchange.getIn().getHeader(KMSConstants.OPERATION, KMSOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }

        if (ObjectHelper.isEmpty(operation)) {
            throw new IllegalArgumentException("Operation must be specified");
        }
        return operation;
    }

    protected KMSConfiguration getConfiguration() {
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
    public KMSEndpoint getEndpoint() {
        return (KMSEndpoint)super.getEndpoint();
    }

    private void listKeys(AWSKMS kmsClient, Exchange exchange) {
        ListKeysRequest request = new ListKeysRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KMSConstants.LIMIT))) {
            int limit = exchange.getIn().getHeader(KMSConstants.LIMIT, Integer.class);
            request.withLimit(limit);
        }
        ListKeysResult result;
        try {
            result = kmsClient.listKeys(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("List Keys command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void createKey(AWSKMS kmsClient, Exchange exchange) {
        CreateKeyRequest request = new CreateKeyRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KMSConstants.DESCRIPTION))) {
            String description = exchange.getIn().getHeader(KMSConstants.DESCRIPTION, String.class);
            request.withDescription(description);
        }
        CreateKeyResult result;
        try {
            result = kmsClient.createKey(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Create Key command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void disableKey(AWSKMS kmsClient, Exchange exchange) {
        DisableKeyRequest request = new DisableKeyRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KMSConstants.KEY_ID))) {
            String keyId = exchange.getIn().getHeader(KMSConstants.KEY_ID, String.class);
            request.withKeyId(keyId);
        } else {
            throw new IllegalArgumentException("Key Id must be specified");
        }
        DisableKeyResult result;
        try {
            result = kmsClient.disableKey(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Disable Key command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void scheduleKeyDeletion(AWSKMS kmsClient, Exchange exchange) {
        ScheduleKeyDeletionRequest request = new ScheduleKeyDeletionRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KMSConstants.KEY_ID))) {
            String keyId = exchange.getIn().getHeader(KMSConstants.KEY_ID, String.class);
            request.withKeyId(keyId);
        } else {
            throw new IllegalArgumentException("Key Id must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KMSConstants.PENDING_WINDOW_IN_DAYS))) {
            int pendingWindows = exchange.getIn().getHeader(KMSConstants.PENDING_WINDOW_IN_DAYS, Integer.class);
            request.withPendingWindowInDays(pendingWindows);
        }
        ScheduleKeyDeletionResult result;
        try {
            result = kmsClient.scheduleKeyDeletion(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Schedule Key Deletion command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void describeKey(AWSKMS kmsClient, Exchange exchange) {
        DescribeKeyRequest request = new DescribeKeyRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KMSConstants.KEY_ID))) {
            String keyId = exchange.getIn().getHeader(KMSConstants.KEY_ID, String.class);
            request.withKeyId(keyId);
        } else {
            throw new IllegalArgumentException("Key Id must be specified");
        }
        DescribeKeyResult result;
        try {
            result = kmsClient.describeKey(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Describe Key command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void enableKey(AWSKMS kmsClient, Exchange exchange) {
        EnableKeyRequest request = new EnableKeyRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KMSConstants.KEY_ID))) {
            String keyId = exchange.getIn().getHeader(KMSConstants.KEY_ID, String.class);
            request.withKeyId(keyId);
        } else {
            throw new IllegalArgumentException("Key Id must be specified");
        }
        EnableKeyResult result;
        try {
            result = kmsClient.enableKey(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Enable Key command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        if (exchange.getPattern().isOutCapable()) {
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());
            return out;
        }
        return exchange.getIn();
    }
}
