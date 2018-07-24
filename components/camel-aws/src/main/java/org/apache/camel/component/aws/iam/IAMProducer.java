/**
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
package org.apache.camel.component.aws.iam;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.CreateUserRequest;
import com.amazonaws.services.identitymanagement.model.CreateUserResult;
import com.amazonaws.services.identitymanagement.model.DeleteUserRequest;
import com.amazonaws.services.identitymanagement.model.DeleteUserResult;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysResult;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.aws.common.AwsExchangeUtil.getMessageForResponse;

/**
 * A Producer which sends messages to the Amazon IAM Service
 * <a href="http://aws.amazon.com/iam/">AWS IAM</a>
 */
public class IAMProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(IAMProducer.class);

    private transient String iamProducerToString;

    public IAMProducer(Endpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
        case listAccessKeys:
            listAccessKeys(getEndpoint().getIamClient(), exchange);
            break;
        case createUser:
            createUser(getEndpoint().getIamClient(), exchange);
            break;
        case deleteUser:
            deleteUser(getEndpoint().getIamClient(), exchange);
            break;
        default:
            throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private IAMOperations determineOperation(Exchange exchange) {
        IAMOperations operation = exchange.getIn().getHeader(IAMConstants.OPERATION, IAMOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected IAMConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (iamProducerToString == null) {
        	iamProducerToString = "IAMProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return iamProducerToString;
    }

    @Override
    public IAMEndpoint getEndpoint() {
        return (IAMEndpoint)super.getEndpoint();
    }

    private void listAccessKeys(AmazonIdentityManagement iamClient, Exchange exchange) {
        ListAccessKeysResult result;
        try {
            result = iamClient.listAccessKeys();
        } catch (AmazonServiceException ase) {
            LOG.trace("List Access Keys command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }
    
    private void createUser(AmazonIdentityManagement iamClient, Exchange exchange) {
        CreateUserRequest request = new CreateUserRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.USERNAME))) {
            String userName = exchange.getIn().getHeader(IAMConstants.USERNAME, String.class);
            request.withUserName(userName);
        }
        CreateUserResult result;
        try {
            result = iamClient.createUser(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Create user command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }
    
    private void deleteUser(AmazonIdentityManagement iamClient, Exchange exchange) {
        DeleteUserRequest request = new DeleteUserRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.USERNAME))) {
            String userName = exchange.getIn().getHeader(IAMConstants.USERNAME, String.class);
            request.withUserName(userName);
        }
        DeleteUserResult result;
        try {
            result = iamClient.deleteUser(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Delete user command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }
}