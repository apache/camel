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
package org.apache.camel.component.aws2.sts;

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
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest.Builder;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.GetFederationTokenRequest;
import software.amazon.awssdk.services.sts.model.GetFederationTokenResponse;
import software.amazon.awssdk.services.sts.model.GetSessionTokenRequest;
import software.amazon.awssdk.services.sts.model.GetSessionTokenResponse;

/**
 * A Producer which sends messages to the Amazon STS Service SDK v2 <a href="http://aws.amazon.com/sts/">AWS STS</a>
 */
public class STS2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(STS2Producer.class);

    private transient String stsProducerToString;

    public STS2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case assumeRole:
                assumeRole(getEndpoint().getStsClient(), exchange);
                break;
            case getSessionToken:
                getSessionToken(getEndpoint().getStsClient(), exchange);
                break;
            case getFederationToken:
                getFederationToken(getEndpoint().getStsClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private STS2Operations determineOperation(Exchange exchange) {
        STS2Operations operation = exchange.getIn().getHeader(STS2Constants.OPERATION, STS2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected STS2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (stsProducerToString == null) {
            stsProducerToString = "STSProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return stsProducerToString;
    }

    @Override
    public STS2Endpoint getEndpoint() {
        return (STS2Endpoint) super.getEndpoint();
    }

    private void assumeRole(StsClient stsClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof AssumeRoleRequest) {
                AssumeRoleResponse result;
                try {
                    AssumeRoleRequest request = (AssumeRoleRequest) payload;
                    result = stsClient.assumeRole(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Assume Role command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            Builder builder = AssumeRoleRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(STS2Constants.ROLE_ARN))) {
                String roleArn = exchange.getIn().getHeader(STS2Constants.ROLE_ARN, String.class);
                builder.roleArn(roleArn);
            } else {
                throw new IllegalArgumentException("Role ARN needs to be specified for assumeRole operation");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(STS2Constants.ROLE_SESSION_NAME))) {
                String roleSessionName = exchange.getIn().getHeader(STS2Constants.ROLE_SESSION_NAME, String.class);
                builder.roleSessionName(roleSessionName);
            } else {
                throw new IllegalArgumentException("Role Session Name needs to be specified for assumeRole operation");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(STS2Constants.ASSUME_ROLE_DURATION_SECONDS))) {
                Integer durationInSeconds
                        = exchange.getIn().getHeader(STS2Constants.ASSUME_ROLE_DURATION_SECONDS, Integer.class);
                builder.durationSeconds(durationInSeconds);
            }
            AssumeRoleResponse result;
            try {
                result = stsClient.assumeRole(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Assume Role command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void getSessionToken(StsClient stsClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetSessionTokenRequest) {
                GetSessionTokenResponse result;
                try {
                    GetSessionTokenRequest request = (GetSessionTokenRequest) payload;
                    result = stsClient.getSessionToken(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Get Session Token command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            GetSessionTokenRequest.Builder builder = GetSessionTokenRequest.builder();
            GetSessionTokenResponse result;
            try {
                result = stsClient.getSessionToken(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Get Session Token command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void getFederationToken(StsClient stsClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetFederationTokenRequest) {
                GetFederationTokenResponse result;
                try {
                    GetFederationTokenRequest request = (GetFederationTokenRequest) payload;
                    result = stsClient.getFederationToken(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Get Federation Token command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            GetFederationTokenRequest.Builder builder = GetFederationTokenRequest.builder();
            GetFederationTokenResponse result;
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(STS2Constants.FEDERATED_NAME))) {
                String federatedName = exchange.getIn().getHeader(STS2Constants.FEDERATED_NAME, String.class);
                builder.name(federatedName);
            } else {
                throw new IllegalArgumentException("Federated name needs to be specified for assumeRole operation");
            }
            try {
                result = stsClient.getFederationToken(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Get Federation Token command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
