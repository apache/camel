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

import com.google.api.client.util.Lists;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.functions.v1.CallFunctionResponse;
import com.google.cloud.functions.v1.CloudFunction;
import com.google.cloud.functions.v1.CloudFunctionName;
import com.google.cloud.functions.v1.CloudFunctionsServiceClient;
import com.google.cloud.functions.v1.CloudFunctionsServiceClient.ListFunctionsPagedResponse;
import com.google.cloud.functions.v1.ListFunctionsRequest;
import com.google.cloud.functions.v1.LocationName;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GoogleCloudFunctions producer.
 */
public class GoogleCloudFunctionsProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(GoogleCloudFunctionsProducer.class);

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

            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private void listFunctions(CloudFunctionsServiceClient client, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListFunctionsRequest) {
                ListFunctionsPagedResponse result;
                try {
                    result = client.listFunctions((ListFunctionsRequest) payload);
                } catch (ApiException ae) {
                    LOG.trace("listFunctions command returned the error code {}", ae.getStatusCode());
                    throw ae;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListFunctionsRequest request = ListFunctionsRequest.newBuilder()
                    .setParent(LocationName.of(getConfiguration().getProject(), getConfiguration().getLocation()).toString())
                    .setPageSize(883849137) //TODO check it
                    .setPageToken("pageToken873572522").build();
            ListFunctionsPagedResponse pagedListResponse = client.listFunctions(request);
            List<CloudFunction> result = Lists.newArrayList(pagedListResponse.iterateAll());
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void getFunction(CloudFunctionsServiceClient client, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CloudFunctionName) {
                CloudFunction result;
                try {
                    result = client.getFunction((CloudFunctionName) payload);
                } catch (ApiException ae) {
                    LOG.trace("getFunction command returned the error code {}", ae.getStatusCode());
                    throw ae;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CloudFunctionName cfName = CloudFunctionName.of(getConfiguration().getProject(), getConfiguration().getLocation(),
                    getConfiguration().getFunctionName());
            CloudFunction result = client.getFunction(cfName);
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void callFunction(CloudFunctionsServiceClient client, Exchange exchange) throws InvalidPayloadException {
        String data = exchange.getIn().getBody(String.class);
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CloudFunctionName) {
                CallFunctionResponse result;
                try {
                    result = client.callFunction((CloudFunctionName) payload, data);
                } catch (ApiException ae) {
                    LOG.trace("callFunction command returned the error code {}", ae.getStatusCode());
                    throw ae;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CloudFunctionName cfName = CloudFunctionName.of(getConfiguration().getProject(), getConfiguration().getLocation(),
                    getConfiguration().getFunctionName());
            CallFunctionResponse result = client.callFunction(cfName, data);
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
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
