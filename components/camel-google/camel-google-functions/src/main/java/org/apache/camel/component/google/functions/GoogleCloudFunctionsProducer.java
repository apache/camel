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
package org.apache.camel.component.google.functions;

import java.util.List;
import java.util.concurrent.ExecutionException;

import com.google.api.client.util.Lists;
import com.google.cloud.functions.v1.CallFunctionRequest;
import com.google.cloud.functions.v1.CallFunctionResponse;
import com.google.cloud.functions.v1.CloudFunction;
import com.google.cloud.functions.v1.CloudFunctionName;
import com.google.cloud.functions.v1.CloudFunctionsServiceClient;
import com.google.cloud.functions.v1.CloudFunctionsServiceClient.ListFunctionsPagedResponse;
import com.google.cloud.functions.v1.CreateFunctionRequest;
import com.google.cloud.functions.v1.DeleteFunctionRequest;
import com.google.cloud.functions.v1.GenerateDownloadUrlRequest;
import com.google.cloud.functions.v1.GenerateDownloadUrlResponse;
import com.google.cloud.functions.v1.GenerateUploadUrlRequest;
import com.google.cloud.functions.v1.GenerateUploadUrlResponse;
import com.google.cloud.functions.v1.HttpsTrigger;
import com.google.cloud.functions.v1.ListFunctionsRequest;
import com.google.cloud.functions.v1.LocationName;
import com.google.cloud.functions.v1.UpdateFunctionRequest;
import com.google.protobuf.Empty;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;

/**
 * The GoogleCloudFunctions producer.
 */
public class GoogleCloudFunctionsProducer extends DefaultProducer {
    private GoogleCloudFunctionsEndpoint endpoint;

    public GoogleCloudFunctionsProducer(GoogleCloudFunctionsEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case listFunctions:
                listFunctions(endpoint.getClient(), exchange);
                break;
            case getFunction:
                getFunction(endpoint.getClient(), exchange);
                break;
            case callFunction:
                callFunction(endpoint.getClient(), exchange);
                break;
            case generateDownloadUrl:
                generateDownloadUrl(endpoint.getClient(), exchange);
                break;
            case generateUploadUrl:
                generateUploadUrl(endpoint.getClient(), exchange);
                break;
            case createFunction:
                createFunction(endpoint.getClient(), exchange);
                break;
            case updateFunction:
                updateFunction(endpoint.getClient(), exchange);
                break;
            case deleteFunction:
                deleteFunction(endpoint.getClient(), exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private void listFunctions(CloudFunctionsServiceClient client, Exchange exchange) throws InvalidPayloadException {
        List<CloudFunction> response = null;
        if (getConfiguration().isPojoRequest()) {
            ListFunctionsRequest request = exchange.getIn().getMandatoryBody(ListFunctionsRequest.class);
            ListFunctionsPagedResponse pagedListResponse = client.listFunctions(request);
            response = Lists.newArrayList(pagedListResponse.iterateAll());
        } else {
            ListFunctionsRequest request = ListFunctionsRequest
                    .newBuilder().setParent(LocationName
                            .of(getConfiguration().getProject(), getConfiguration().getLocation()).toString())
                    .setPageSize(Integer.MAX_VALUE).build();
            ListFunctionsPagedResponse pagedListResponse = client.listFunctions(request);
            response = Lists.newArrayList(pagedListResponse.iterateAll());
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(response);
    }

    private void getFunction(CloudFunctionsServiceClient client, Exchange exchange) throws InvalidPayloadException {
        CloudFunction response = null;
        if (getConfiguration().isPojoRequest()) {
            CloudFunctionName request = exchange.getIn().getMandatoryBody(CloudFunctionName.class);
            response = client.getFunction(request);
        } else {
            CloudFunctionName cfName = CloudFunctionName.of(getConfiguration().getProject(),
                    getConfiguration().getLocation(), getConfiguration().getFunctionName());
            response = client.getFunction(cfName);
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(response);
    }

    private void callFunction(CloudFunctionsServiceClient client, Exchange exchange) throws InvalidPayloadException {
        CallFunctionResponse response = null;
        if (getConfiguration().isPojoRequest()) {
            CallFunctionRequest request = exchange.getIn().getMandatoryBody(CallFunctionRequest.class);
            response = client.callFunction(request);
        } else {
            String data = exchange.getIn().getBody(String.class);
            CloudFunctionName cfName = CloudFunctionName.of(getConfiguration().getProject(),
                    getConfiguration().getLocation(), getConfiguration().getFunctionName());
            CallFunctionRequest request = CallFunctionRequest.newBuilder().setName(cfName.toString()).setData(data)
                    .build();
            response = client.callFunction(request);
        }
        Message message = getMessageForResponse(exchange);
        message.setHeader(GoogleCloudFunctionsConstants.RESPONSE_OBJECT, response);
        message.setBody(response.getResult());
    }

    private void generateDownloadUrl(CloudFunctionsServiceClient client, Exchange exchange)
            throws InvalidPayloadException {
        GenerateDownloadUrlResponse response = null;
        if (getConfiguration().isPojoRequest()) {
            GenerateDownloadUrlRequest request = exchange.getIn().getMandatoryBody(GenerateDownloadUrlRequest.class);
            response = client.generateDownloadUrl(request);
        } else {
            CloudFunctionName cfName = CloudFunctionName.of(getConfiguration().getProject(),
                    getConfiguration().getLocation(), getConfiguration().getFunctionName());
            GenerateDownloadUrlRequest request = GenerateDownloadUrlRequest.newBuilder().setName(cfName.toString())
                    .build();
            response = client.generateDownloadUrl(request);
        }
        Message message = getMessageForResponse(exchange);
        message.setHeader(GoogleCloudFunctionsConstants.RESPONSE_OBJECT, response);
        message.setBody(response.getDownloadUrl());
    }

    private void generateUploadUrl(CloudFunctionsServiceClient client, Exchange exchange)
            throws InvalidPayloadException {
        GenerateUploadUrlResponse response = null;
        if (getConfiguration().isPojoRequest()) {
            GenerateUploadUrlRequest request = exchange.getIn().getMandatoryBody(GenerateUploadUrlRequest.class);
            response = client.generateUploadUrl(request);
        } else {
            LocationName locationName = LocationName.of(getConfiguration().getProject(),
                    getConfiguration().getLocation());
            GenerateUploadUrlRequest request = GenerateUploadUrlRequest.newBuilder().setParent(locationName.toString())
                    .build();
            response = client.generateUploadUrl(request);
        }
        Message message = getMessageForResponse(exchange);
        message.setHeader(GoogleCloudFunctionsConstants.RESPONSE_OBJECT, response);
        message.setBody(response.getUploadUrl());
    }

    private void createFunction(CloudFunctionsServiceClient client, Exchange exchange)
            throws InvalidPayloadException, InterruptedException, ExecutionException {
        CloudFunction response = null;
        if (getConfiguration().isPojoRequest()) {
            CreateFunctionRequest request = exchange.getIn().getMandatoryBody(CreateFunctionRequest.class);
            response = client.createFunctionAsync(request).get();
        } else {
            final String project = getConfiguration().getProject();
            final String location = getConfiguration().getLocation();
            final String functionName = getConfiguration().getFunctionName();
            final String entryPoint = exchange.getIn().getHeader(GoogleCloudFunctionsConstants.ENTRY_POINT,
                    String.class);
            final String runtime = exchange.getIn().getHeader(GoogleCloudFunctionsConstants.RUNTIME, String.class);
            final String sourceArchiveUrl = exchange.getIn().getHeader(GoogleCloudFunctionsConstants.SOURCE_ARCHIVE_URL,
                    String.class);
            CloudFunction function = CloudFunction.newBuilder()
                    .setName(CloudFunctionName.of(project, location, functionName).toString()).setEntryPoint(entryPoint)
                    .setRuntime(runtime).setHttpsTrigger(HttpsTrigger.getDefaultInstance())
                    .setSourceArchiveUrl(sourceArchiveUrl).build();
            CreateFunctionRequest request = CreateFunctionRequest.newBuilder()
                    .setLocation(LocationName.of(project, location).toString()).setFunction(function).build();
            response = client.createFunctionAsync(request).get();
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(response);
    }

    private void updateFunction(CloudFunctionsServiceClient client, Exchange exchange)
            throws InvalidPayloadException, InterruptedException, ExecutionException {
        CloudFunction response = null;
        if (getConfiguration().isPojoRequest()) {
            UpdateFunctionRequest request = exchange.getIn().getMandatoryBody(UpdateFunctionRequest.class);
            response = client.updateFunctionAsync(request).get();
        } else {
            throw new IllegalArgumentException("updateFunction is supported only in pojo mode");
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(response);
    }

    private void deleteFunction(CloudFunctionsServiceClient client, Exchange exchange)
            throws InvalidPayloadException, InterruptedException, ExecutionException {
        Empty response = null;
        if (getConfiguration().isPojoRequest()) {
            DeleteFunctionRequest request = exchange.getIn().getMandatoryBody(DeleteFunctionRequest.class);
            response = client.deleteFunctionAsync(request).get();
        } else {
            CloudFunctionName cfName = CloudFunctionName.of(getConfiguration().getProject(),
                    getConfiguration().getLocation(), getConfiguration().getFunctionName());
            DeleteFunctionRequest request = DeleteFunctionRequest.newBuilder().setName(cfName.toString()).build();
            response = client.deleteFunctionAsync(request).get();
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(response);
    }

    private GoogleCloudFunctionsOperations determineOperation(Exchange exchange) {
        GoogleCloudFunctionsOperations operation = exchange.getIn().getHeader(GoogleCloudFunctionsConstants.OPERATION,
                GoogleCloudFunctionsOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation() == null
                    ? GoogleCloudFunctionsOperations.callFunction
                    : getConfiguration().getOperation();
        }
        return operation;
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    private GoogleCloudFunctionsConfiguration getConfiguration() {
        return this.endpoint.getConfiguration();
    }

}
