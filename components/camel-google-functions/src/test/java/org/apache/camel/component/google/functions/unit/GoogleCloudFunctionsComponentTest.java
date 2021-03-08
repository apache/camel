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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.google.api.gax.grpc.GaxGrpcProperties;
import com.google.api.gax.rpc.ApiClientHeaderProvider;
import com.google.cloud.functions.v1.CallFunctionRequest;
import com.google.cloud.functions.v1.CallFunctionResponse;
import com.google.cloud.functions.v1.CloudFunction;
import com.google.cloud.functions.v1.CloudFunctionName;
import com.google.cloud.functions.v1.CloudFunctionStatus;
import com.google.cloud.functions.v1.GetFunctionRequest;
import com.google.cloud.functions.v1.ListFunctionsRequest;
import com.google.cloud.functions.v1.ListFunctionsResponse;
import com.google.cloud.functions.v1.LocationName;
import com.google.protobuf.AbstractMessage;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleCloudFunctionsComponentTest extends GoogleCloudFunctionsBaseTest {

    private static final Logger log = LoggerFactory.getLogger(GoogleCloudFunctionsComponentTest.class);

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    private String project = "project123";
    private String location = "location123";
    private String functionName = "myCalmelFunction";

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                //simple routes
                from("direct:listFunctions").to("google-functions://" + functionName + "?project=" + project
                        + "&location=" + location + "&operation=listFunctions").to("mock:result");
                from("direct:getFunction").to("google-functions://" + functionName + "?project=" + project
                        + "&location=" + location + "&operation=getFunction").to("mock:result");
                from("direct:callFunction").to("google-functions://" + functionName + "?project=" + project
                        + "&location=" + location + "&operation=callFunction").to("mock:result");
                //pojo routes
                from("direct:listFunctionsPojo").to("google-functions://" + functionName + "?project=" + project
                        + "&location=" + location + "&operation=listFunctions&pojoRequest=true").to("mock:result");
                from("direct:getFunctionPojo").to("google-functions://" + functionName + "?project=" + project
                        + "&location=" + location + "&operation=getFunction&pojoRequest=true").to("mock:result");
                from("direct:callFunctionPojo").to("google-functions://" + functionName + "?project=" + project
                        + "&location=" + location + "&operation=callFunction&pojoRequest=true").to("mock:result");
            }
        };
    }

    @Test
    public void listFunctionsTest() throws Exception {
        CloudFunction cf1 = CloudFunction.newBuilder().build();
        CloudFunction cf2 = CloudFunction.newBuilder().build();
        List<CloudFunction> cfList = Arrays.asList(cf1, cf2);
        ListFunctionsResponse expectedResponse = ListFunctionsResponse.newBuilder().setNextPageToken("")
                .addAllFunctions(cfList).build();
        mockCloudFunctionsService.addResponse(expectedResponse);

        Exchange exchange = template.send("direct:listFunctions", ExchangePattern.InOut, exc -> {
        });
        List<CloudFunction> result = exchange.getMessage().getBody(List.class);
        assertNotNull(result);
        assertEquals(cfList.size(), result.size());

        for (int i = 0; i < result.size(); i++) {
            assertEquals(expectedResponse.getFunctionsList().get(i), result.get(i));
        }

    }

    @Test
    public void listFunctionsPojoTest() throws Exception {
        CloudFunction cf1 = CloudFunction.newBuilder().build();
        CloudFunction cf2 = CloudFunction.newBuilder().build();
        List<CloudFunction> cfList = Arrays.asList(cf1, cf2);
        ListFunctionsResponse expectedResponse = ListFunctionsResponse.newBuilder().setNextPageToken("")
                .addAllFunctions(cfList).build();
        mockCloudFunctionsService.addResponse(expectedResponse);

        ListFunctionsRequest pojoRequest = ListFunctionsRequest.newBuilder()
                .setParent(LocationName.of(project, location).toString()).setPageSize(883849137)
                .setPageToken("pageToken873572522").build();

        Exchange exchange = template.send("direct:listFunctionsPojo", ExchangePattern.InOut, exc -> {
            exc.getIn().setBody(pojoRequest);
        });
        List<CloudFunction> result = exchange.getMessage().getBody(List.class);
        assertNotNull(result);
        assertEquals(cfList.size(), result.size());

        for (int i = 0; i < result.size(); i++) {
            assertEquals(expectedResponse.getFunctionsList().get(i), result.get(i));
        }

    }

    @Test
    public void getFunctionTest() throws Exception {
        CloudFunction expectedResponse = CloudFunction.newBuilder()
                .setName(CloudFunctionName.of(project, location, functionName).toString())
                .setDescription("description-1724546052").setStatus(CloudFunctionStatus.forNumber(0))
                .setEntryPoint("entryPoint-1979329474").setRuntime("runtime1550962648")
                .setTimeout(Duration.newBuilder().build()).setAvailableMemoryMb(1964533661)
                .setServiceAccountEmail("serviceAccountEmail1825953988").setUpdateTime(Timestamp.newBuilder().build())
                .setVersionId(-670497310).putAllLabels(new HashMap<String, String>())
                .putAllEnvironmentVariables(new HashMap<String, String>()).setNetwork("network1843485230")
                .setMaxInstances(-330682013).setVpcConnector("vpcConnector2101559652").setBuildId("buildId230943785")
                .build();
        mockCloudFunctionsService.addResponse(expectedResponse);
        CloudFunctionName cfName = CloudFunctionName.of(project, location, functionName);

        Exchange exchange = template.send("direct:getFunction", ExchangePattern.InOut, exc -> {
        });
        CloudFunction actualResponse = exchange.getMessage().getBody(CloudFunction.class);
        assertEquals(expectedResponse, actualResponse);

        List<AbstractMessage> actualRequests = mockCloudFunctionsService.getRequests();
        assertEquals(1, actualRequests.size());
        GetFunctionRequest actualRequest = ((GetFunctionRequest) actualRequests.get(0));

        assertEquals(cfName.toString(), actualRequest.getName());
        assertTrue(channelProvider.isHeaderSent(ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
                GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
    }

    @Test
    public void getFunctionPojoTest() throws Exception {
        CloudFunctionName cfName = CloudFunctionName.of(project, location, functionName);
        CloudFunction expectedResponse = CloudFunction.newBuilder()
                .setName( cfName.toString())
                .setDescription("description-1724546052").setStatus(CloudFunctionStatus.forNumber(0))
                .setEntryPoint("entryPoint-1979329474").setRuntime("runtime1550962648")
                .setTimeout(Duration.newBuilder().build()).setAvailableMemoryMb(1964533661)
                .setServiceAccountEmail("serviceAccountEmail1825953988").setUpdateTime(Timestamp.newBuilder().build())
                .setVersionId(-670497310).putAllLabels(new HashMap<String, String>())
                .putAllEnvironmentVariables(new HashMap<String, String>()).setNetwork("network1843485230")
                .setMaxInstances(-330682013).setVpcConnector("vpcConnector2101559652").setBuildId("buildId230943785")
                .build();
        mockCloudFunctionsService.addResponse(expectedResponse);
        
        Exchange exchange = template.send("direct:getFunctionPojo", ExchangePattern.InOut, exc -> {
            exc.getIn().setBody(cfName);
        });
        CloudFunction actualResponse = exchange.getMessage().getBody(CloudFunction.class);
        assertEquals(expectedResponse, actualResponse);

        List<AbstractMessage> actualRequests = mockCloudFunctionsService.getRequests();
        assertEquals(1, actualRequests.size());
        GetFunctionRequest actualRequest = ((GetFunctionRequest) actualRequests.get(0));

        assertEquals(cfName.toString(), actualRequest.getName());
        assertTrue(channelProvider.isHeaderSent(ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
                GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
    }

    @Test
    public void callFunctionTest() throws Exception {
        CallFunctionResponse expectedResponse = CallFunctionResponse.newBuilder()
                .setExecutionId("executionId-454906285").setResult("result-934426595").setError("error96784904")
                .build();
        mockCloudFunctionsService.addResponse(expectedResponse);

        CloudFunctionName name = CloudFunctionName.of(project, location, functionName);
        String data = "data3076010";

        Exchange exchange = template.send("direct:callFunction", ExchangePattern.InOut, exc -> {
            exc.getIn().setBody(data);
        });
        CallFunctionResponse actualResponse = exchange.getMessage().getBody(CallFunctionResponse.class);
        assertEquals(expectedResponse, actualResponse);

        List<AbstractMessage> actualRequests = mockCloudFunctionsService.getRequests();
        assertEquals(1, actualRequests.size());
        CallFunctionRequest actualRequest = ((CallFunctionRequest) actualRequests.get(0));

        assertEquals(name.toString(), actualRequest.getName());
        assertEquals(data, actualRequest.getData());
        assertTrue(channelProvider.isHeaderSent(ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
                GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
    }

    @Test
    public void callFunctionPojoTest() throws Exception {
        CallFunctionResponse expectedResponse = CallFunctionResponse.newBuilder()
                .setExecutionId("executionId-454906285").setResult("result-934426595").setError("error96784904")
                .build();
        mockCloudFunctionsService.addResponse(expectedResponse);

        CloudFunctionName cfName = CloudFunctionName.of(project, location, functionName);
        String data = "data3076010";
        CallFunctionRequest request = CallFunctionRequest.newBuilder().setName(cfName.toString()).setData(data).build();

        Exchange exchange = template.send("direct:callFunctionPojo", ExchangePattern.InOut, exc -> {
            exc.getIn().setBody(request);
        });
        CallFunctionResponse actualResponse = exchange.getMessage().getBody(CallFunctionResponse.class);
        assertEquals(expectedResponse, actualResponse);

        List<AbstractMessage> actualRequests = mockCloudFunctionsService.getRequests();
        assertEquals(1, actualRequests.size());
        CallFunctionRequest actualRequest = ((CallFunctionRequest) actualRequests.get(0));

        assertEquals(cfName.toString(), actualRequest.getName());
        assertEquals(data, actualRequest.getData());
        assertTrue(channelProvider.isHeaderSent(ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
                GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
    }

}
