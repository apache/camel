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
package org.apache.camel.component.google.secret.manager;

import com.google.cloud.secretmanager.v1.AccessSecretVersionRequest;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.AddSecretVersionRequest;
import com.google.cloud.secretmanager.v1.DeleteSecretRequest;
import com.google.cloud.secretmanager.v1.ListSecretsRequest;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.Replication;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretName;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.protobuf.ByteString;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;

/**
 * The Google Secret Manager producer.
 */
public class GoogleSecretManagerProducer extends DefaultProducer {

    private GoogleSecretManagerEndpoint endpoint;

    public GoogleSecretManagerProducer(GoogleSecretManagerEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case createSecret:
                createSecret(endpoint.getClient(), exchange);
                break;
            case getSecretVersion:
                getSecretVersion(endpoint.getClient(), exchange);
                break;
            case deleteSecret:
                deleteSecret(endpoint.getClient(), exchange);
                break;
            case listSecrets:
                listSecrets(endpoint.getClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private void createSecret(SecretManagerServiceClient client, Exchange exchange) throws InvalidPayloadException {
        SecretVersion response = null;
        if (getConfiguration().isPojoRequest()) {
            AddSecretVersionRequest request = exchange.getIn().getMandatoryBody(AddSecretVersionRequest.class);
            response = client.addSecretVersion(request);
        } else {
            String secretId = exchange.getMessage().getHeader(GoogleSecretManagerConstants.SECRET_ID, String.class);
            Secret secret = Secret.newBuilder()
                    .setReplication(
                            Replication.newBuilder()
                                    .setAutomatic(Replication.Automatic.newBuilder().build())
                                    .build())
                    .build();

            Secret createdSecret = client.createSecret(ProjectName.of(getConfiguration().getProject()), secretId, secret);

            SecretPayload payload = SecretPayload.newBuilder()
                    .setData(ByteString.copyFromUtf8(exchange.getMessage().getBody(String.class))).build();
            response = client.addSecretVersion(createdSecret.getName(), payload);
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(response);
    }

    private void getSecretVersion(SecretManagerServiceClient client, Exchange exchange) throws InvalidPayloadException {
        final String defaultVersion = "latest";
        AccessSecretVersionResponse response;
        if (getConfiguration().isPojoRequest()) {
            AccessSecretVersionRequest request = exchange.getIn().getMandatoryBody(AccessSecretVersionRequest.class);
            response = client.accessSecretVersion(request);
        } else {
            String secretId = exchange.getMessage().getHeader(GoogleSecretManagerConstants.SECRET_ID, String.class);
            String versionId
                    = exchange.getMessage().getHeader(GoogleSecretManagerConstants.VERSION_ID, defaultVersion, String.class);
            String projectId = getConfiguration().getProject();
            SecretVersionName secretVersionName = SecretVersionName.of(projectId, secretId, versionId);
            response = client.accessSecretVersion(secretVersionName);
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(response.getPayload().getData().toStringUtf8());
    }

    private void deleteSecret(SecretManagerServiceClient client, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            DeleteSecretRequest request = exchange.getIn().getMandatoryBody(DeleteSecretRequest.class);
            client.deleteSecret(request);
        } else {
            String secretId = exchange.getMessage().getHeader(GoogleSecretManagerConstants.SECRET_ID, String.class);
            client.deleteSecret(SecretName.of(getConfiguration().getProject(), secretId));
        }
    }

    private void listSecrets(SecretManagerServiceClient client, Exchange exchange) throws InvalidPayloadException {
        SecretManagerServiceClient.ListSecretsPagedResponse response;
        if (getConfiguration().isPojoRequest()) {
            ListSecretsRequest request = exchange.getIn().getMandatoryBody(ListSecretsRequest.class);
            response = client.listSecrets(request);
        } else {
            String projectId = getConfiguration().getProject();
            response = client.listSecrets(ProjectName.of(projectId));
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(response);
    }

    private GoogleSecretManagerOperations determineOperation(Exchange exchange) {
        GoogleSecretManagerOperations operation = exchange.getIn().getHeader(GoogleSecretManagerConstants.OPERATION,
                GoogleSecretManagerOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation() == null
                    ? GoogleSecretManagerOperations.createSecret
                    : getConfiguration().getOperation();
        }
        return operation;
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    private GoogleSecretManagerConfiguration getConfiguration() {
        return this.endpoint.getConfiguration();
    }

}
