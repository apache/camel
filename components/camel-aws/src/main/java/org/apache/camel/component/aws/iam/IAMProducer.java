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
import com.amazonaws.services.identitymanagement.model.CreateAccessKeyRequest;
import com.amazonaws.services.identitymanagement.model.CreateAccessKeyResult;
import com.amazonaws.services.identitymanagement.model.CreateUserRequest;
import com.amazonaws.services.identitymanagement.model.CreateUserResult;
import com.amazonaws.services.identitymanagement.model.DeleteAccessKeyRequest;
import com.amazonaws.services.identitymanagement.model.DeleteAccessKeyResult;
import com.amazonaws.services.identitymanagement.model.DeleteUserRequest;
import com.amazonaws.services.identitymanagement.model.DeleteUserResult;
import com.amazonaws.services.identitymanagement.model.GetUserRequest;
import com.amazonaws.services.identitymanagement.model.GetUserResult;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysResult;
import com.amazonaws.services.identitymanagement.model.ListUsersResult;
import com.amazonaws.services.identitymanagement.model.StatusType;
import com.amazonaws.services.identitymanagement.model.UpdateAccessKeyRequest;
import com.amazonaws.services.identitymanagement.model.UpdateAccessKeyResult;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

import static org.apache.camel.component.aws.common.AwsExchangeUtil.getMessageForResponse;

/**
 * A Producer which sends messages to the Amazon IAM Service
 * <a href="http://aws.amazon.com/iam/">AWS IAM</a>
 */
public class IAMProducer extends DefaultProducer {

    private transient String iamProducerToString;

    public IAMProducer(Endpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
        case listAccessKeys:
            listAccessKeys(getEndpoint().getIamClient(), exchange);
            break;
        case createAccessKey:
            createAccessKey(getEndpoint().getIamClient(), exchange);
            break;
        case deleteAccessKey:
            deleteAccessKey(getEndpoint().getIamClient(), exchange);
            break;
        case updateAccessKey:
            updateAccessKey(getEndpoint().getIamClient(), exchange);
            break;
        case createUser:
            createUser(getEndpoint().getIamClient(), exchange);
            break;
        case deleteUser:
            deleteUser(getEndpoint().getIamClient(), exchange);
            break;
        case getUser:
            getUser(getEndpoint().getIamClient(), exchange);
            break;
        case listUsers:
            listUsers(getEndpoint().getIamClient(), exchange);
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
            log.trace("List Access Keys command returned the error code {}", ase.getErrorCode());
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
            log.trace("Create user command returned the error code {}", ase.getErrorCode());
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
            log.trace("Delete user command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }
    
    private void getUser(AmazonIdentityManagement iamClient, Exchange exchange) {
        GetUserRequest request = new GetUserRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.USERNAME))) {
            String userName = exchange.getIn().getHeader(IAMConstants.USERNAME, String.class);
            request.withUserName(userName);
        }
        GetUserResult result;
        try {
            result = iamClient.getUser(request);
        } catch (AmazonServiceException ase) {
            log.trace("get user command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listUsers(AmazonIdentityManagement iamClient, Exchange exchange) {
        ListUsersResult result;
        try {
            result = iamClient.listUsers();
        } catch (AmazonServiceException ase) {
            log.trace("List users command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }
    
    private void createAccessKey(AmazonIdentityManagement iamClient, Exchange exchange) {
        CreateAccessKeyRequest request = new CreateAccessKeyRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.USERNAME))) {
            String userName = exchange.getIn().getHeader(IAMConstants.USERNAME, String.class);
            request.withUserName(userName);
        }
        CreateAccessKeyResult result;
        try {
            result = iamClient.createAccessKey(request);
        } catch (AmazonServiceException ase) {
            log.trace("Create Access Key command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }
    
    private void deleteAccessKey(AmazonIdentityManagement iamClient, Exchange exchange) {
        DeleteAccessKeyRequest request = new DeleteAccessKeyRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.ACCESS_KEY_ID))) {
            String accessKeyId = exchange.getIn().getHeader(IAMConstants.ACCESS_KEY_ID, String.class);
            request.withAccessKeyId(accessKeyId);
        } else {
            throw new IllegalArgumentException("Key Id must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.USERNAME))) {
            String userName = exchange.getIn().getHeader(IAMConstants.USERNAME, String.class);
            request.withUserName(userName);
        }
        DeleteAccessKeyResult result;
        try {
            result = iamClient.deleteAccessKey(request);
        } catch (AmazonServiceException ase) {
            log.trace("Delete Access Key command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }
    
    private void updateAccessKey(AmazonIdentityManagement iamClient, Exchange exchange) {
        UpdateAccessKeyRequest request = new UpdateAccessKeyRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.ACCESS_KEY_ID))) {
            String accessKeyId = exchange.getIn().getHeader(IAMConstants.ACCESS_KEY_ID, String.class);
            request.withAccessKeyId(accessKeyId);
        } else {
            throw new IllegalArgumentException("Key Id must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.ACCESS_KEY_STATUS))) {
            String status = exchange.getIn().getHeader(IAMConstants.ACCESS_KEY_STATUS, String.class);
            request.withStatus(StatusType.fromValue(status));
        } else {
            throw new IllegalArgumentException("Access Key status must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.USERNAME))) {
            String userName = exchange.getIn().getHeader(IAMConstants.USERNAME, String.class);
            request.withUserName(userName);
        }
        UpdateAccessKeyResult result;
        try {
            result = iamClient.updateAccessKey(request);
        } catch (AmazonServiceException ase) {
            log.trace("Update Access Key command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }
}
