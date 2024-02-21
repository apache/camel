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
package org.apache.camel.component.google.functions.unit;

import java.util.Arrays;
import java.util.UUID;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.testing.LocalChannelProvider;
import com.google.api.gax.grpc.testing.MockGrpcService;
import com.google.api.gax.grpc.testing.MockServiceHelper;
import com.google.cloud.functions.v1.CloudFunctionsServiceClient;
import com.google.cloud.functions.v1.CloudFunctionsServiceSettings;
import org.apache.camel.component.google.functions.GoogleCloudFunctionsComponent;
import org.apache.camel.component.google.functions.GoogleCloudFunctionsEndpoint;
import org.apache.camel.component.google.functions.GoogleCloudFunctionsOperations;
import org.apache.camel.component.google.functions.mock.MockCloudFunctionsService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class GoogleCloudFunctionsConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointConfiguration() throws Exception {
        final String functionName = "function1";
        final String serviceAccountKeyFile = "somefile.json";
        final String project = "project123";
        final String location = "location123";
        final GoogleCloudFunctionsOperations operation = GoogleCloudFunctionsOperations.callFunction;
        final boolean pojoRequest = false;

        GoogleCloudFunctionsComponent component = context.getComponent("google-functions",
                GoogleCloudFunctionsComponent.class);
        GoogleCloudFunctionsEndpoint endpoint = (GoogleCloudFunctionsEndpoint) component.createEndpoint(String.format(
                "google-functions://%s?serviceAccountKey=%s&project=%s&location=%s&operation=%s&pojoRequest=%s",
                functionName, serviceAccountKeyFile, project, location, operation.name(), pojoRequest));

        assertEquals(endpoint.getConfiguration().getFunctionName(), functionName);
        assertEquals(endpoint.getConfiguration().getServiceAccountKey(), serviceAccountKeyFile);
        assertEquals(endpoint.getConfiguration().getProject(), project);
        assertEquals(endpoint.getConfiguration().getLocation(), location);
        assertEquals(endpoint.getConfiguration().getOperation(), operation);
        assertEquals(endpoint.getConfiguration().isPojoRequest(), pojoRequest);
    }

    @Test
    public void createEndpointWithAutowireClient() throws Exception {
        final String functionName = "function1";

        // init mock
        MockCloudFunctionsService mockCloudFunctionsService = new MockCloudFunctionsService();
        MockServiceHelper mockServiceHelper = new MockServiceHelper(
                UUID.randomUUID().toString(),
                Arrays.<MockGrpcService> asList(mockCloudFunctionsService));
        mockServiceHelper.start();
        LocalChannelProvider channelProvider = mockServiceHelper.createChannelProvider();
        CloudFunctionsServiceSettings settings = CloudFunctionsServiceSettings.newBuilder()
                .setTransportChannelProvider(channelProvider).setCredentialsProvider(NoCredentialsProvider.create())
                .build();
        CloudFunctionsServiceClient clientMock = CloudFunctionsServiceClient.create(settings);

        context.getRegistry().bind("myClient", clientMock);

        GoogleCloudFunctionsComponent component = context.getComponent("google-functions",
                GoogleCloudFunctionsComponent.class);
        GoogleCloudFunctionsEndpoint endpoint = (GoogleCloudFunctionsEndpoint) component
                .createEndpoint(String.format("google-functions://%s?client=#myClient", functionName));

        assertEquals(endpoint.getConfiguration().getFunctionName(), functionName);
        assertSame(clientMock, endpoint.getConfiguration().getClient());

        mockServiceHelper.stop();
    }

}
