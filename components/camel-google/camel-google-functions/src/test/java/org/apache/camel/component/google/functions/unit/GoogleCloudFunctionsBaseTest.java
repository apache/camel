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
import org.apache.camel.CamelContext;
import org.apache.camel.component.google.functions.GoogleCloudFunctionsComponent;
import org.apache.camel.component.google.functions.mock.MockCloudFunctionsService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;

public abstract class GoogleCloudFunctionsBaseTest extends CamelTestSupport {

    protected static MockServiceHelper mockServiceHelper;
    protected static MockCloudFunctionsService mockCloudFunctionsService;
    protected LocalChannelProvider channelProvider;
    protected CloudFunctionsServiceClient clientMock;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        GoogleCloudFunctionsComponent component = context.getComponent("google-functions", GoogleCloudFunctionsComponent.class);

        //init mock
        mockCloudFunctionsService = new MockCloudFunctionsService();
        mockServiceHelper = new MockServiceHelper(
                UUID.randomUUID().toString(),
                Arrays.<MockGrpcService> asList(mockCloudFunctionsService));
        mockServiceHelper.start();
        channelProvider = mockServiceHelper.createChannelProvider();
        CloudFunctionsServiceSettings settings = CloudFunctionsServiceSettings.newBuilder()
                .setTransportChannelProvider(channelProvider).setCredentialsProvider(NoCredentialsProvider.create())
                .build();
        clientMock = CloudFunctionsServiceClient.create(settings);

        component.getConfiguration().setClient(clientMock);
        return context;
    }

    @BeforeEach
    public void restMock() {
        mockServiceHelper.reset();
    }

    @AfterAll
    public static void releaseResources() {
        mockServiceHelper.stop();
    }
}
