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

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.xml.transform.Source;

import org.w3c.dom.Node;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * The GoogleCloudFunctions producer.
 */
public class GoogleCloudFunctionsProducer extends DefaultProducer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
            // no page size: iterateAll below follows the pages itself, and the service rejects an
            // oversized one
            ListFunctionsRequest request = ListFunctionsRequest
                    .newBuilder().setParent(LocationName
                            .of(getConfiguration().getProject(), getConfiguration().getLocation()).toString())
                    .build();
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

    private void callFunction(CloudFunctionsServiceClient client, Exchange exchange) throws CamelExchangeException {
        CallFunctionRequest request;
        if (getConfiguration().isPojoRequest()) {
            request = exchange.getIn().getMandatoryBody(CallFunctionRequest.class);
        } else {
            CloudFunctionName cfName = CloudFunctionName.of(getConfiguration().getProject(),
                    getConfiguration().getLocation(), getConfiguration().getFunctionName());
            request = CallFunctionRequest.newBuilder().setName(cfName.toString()).setData(callFunctionData(exchange))
                    .build();
        }
        CallFunctionResponse response = client.callFunction(request);
        Message message = getMessageForResponse(exchange);
        message.setHeader(GoogleCloudFunctionsConstants.RESPONSE_OBJECT, response);
        if (ObjectHelper.isNotEmpty(response.getError())) {
            // the function failed: its result is empty, so the exchange fails instead of going on as a success
            String execution = ObjectHelper.isNotEmpty(response.getExecutionId())
                    ? " in execution " + response.getExecutionId() : "";
            throw new CamelExchangeException(
                    "The function " + request.getName() + " failed" + execution + ": " + response.getError(), exchange);
        }
        message.setBody(response.getResult());
    }

    /**
     * The data to call the function with, which the API requires: text is sent as it is, and the bodies that Camel
     * converts to text (bytes, a stream, a file, XML, an enum, a character) are converted by the type converters, as
     * before. Another body, such as a Map, a List or a POJO, is marshalled to JSON instead of being sent as its
     * toString(). No body fails with an InvalidPayloadException instead of a NullPointerException of protobuf.
     */
    private static String callFunctionData(Exchange exchange) throws InvalidPayloadException {
        Message in = exchange.getIn();
        Object body = in.getBody();
        if (body == null || isText(body)) {
            return in.getMandatoryBody(String.class);
        }
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new InvalidPayloadException(exchange, String.class, in, e);
        }
    }

    /**
     * Whether the body is text, or a body that Camel converts to text: bytes, a stream, a file, XML (a DOM Node or a
     * Source), a ByteBuffer, an enum or a character. Jackson would give some of these another form, such as a quoted
     * JSON string for a DOM Node or Base64 for a ByteBuffer.
     */
    private static boolean isText(Object body) {
        return body instanceof CharSequence || body instanceof byte[] || body instanceof char[]
                || body instanceof InputStream || body instanceof Reader || body instanceof StreamCache
                || body instanceof File || body instanceof Path || body instanceof WrappedFile
                || body instanceof Node || body instanceof Source || body instanceof ByteBuffer
                || body instanceof Enum || body instanceof Character;
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
            final String entryPoint = mandatoryHeader(exchange, GoogleCloudFunctionsConstants.ENTRY_POINT);
            final String runtime = mandatoryHeader(exchange, GoogleCloudFunctionsConstants.RUNTIME);
            final String sourceArchiveUrl = mandatoryHeader(exchange, GoogleCloudFunctionsConstants.SOURCE_ARCHIVE_URL);
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

    /**
     * A header the request cannot be built without. The protobuf setters reject null, so without this the missing
     * header surfaces as a bare NullPointerException instead of naming what is missing.
     */
    private static String mandatoryHeader(Exchange exchange, String header) {
        String value = exchange.getIn().getHeader(header, String.class);
        if (ObjectHelper.isEmpty(value)) {
            throw new IllegalArgumentException("The " + header + " header must be set for this operation");
        }
        return value;
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
