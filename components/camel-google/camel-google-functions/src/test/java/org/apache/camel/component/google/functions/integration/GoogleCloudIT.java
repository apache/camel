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
package org.apache.camel.component.google.functions.integration;

import java.util.List;

import com.google.cloud.functions.v1.CloudFunction;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.functions.GoogleCloudFunctionsConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_APPLICATION_CREDENTIALS", matches = ".*",
                              disabledReason = "Application credentials were not provided")
public class GoogleCloudIT extends CamelTestSupport {

    final String functionNameReverseString = "function-reverse-string";
    final String serviceAccountKeyFile = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
    final String project = "myProject";
    final String location = "us-central1";
    final String functionCreationEntryPoint = "com.example.Example";
    final String functionCreationSourceCode = "gs://myCamelBucket/function-source.zip";

    @EndpointInject("mock:functionsList1")
    private MockEndpoint mockFunctionList1;
    @EndpointInject("mock:getFunction")
    private MockEndpoint mockGetFunction;
    @EndpointInject("mock:generateDownloadUrl")
    private MockEndpoint mockGenerateDownloadUrl;
    @EndpointInject("mock:callFunction")
    private MockEndpoint mockCallFunction;
    @EndpointInject("mock:createFunction")
    private MockEndpoint mockCreateFunction;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                // list functions
                from("timer:timer1?repeatCount=1")
                        .to("google-functions://" + functionNameReverseString + "?serviceAccountKey="
                            + serviceAccountKeyFile + "&project=" + project + "&location=" + location
                            + "&operation=listFunctions")
                        .log("body:${body}").split(bodyAs(List.class)).process(exchange -> {
                            CloudFunction cf = exchange.getIn().getBody(CloudFunction.class);
                            exchange.getIn().setBody(cf.getName());
                        }).to("direct:directFunctionList1").to("mock:functionsList1");

                // get function
                from("direct:directFunctionList1").to("google-functions://" + functionNameReverseString
                                                      + "?serviceAccountKey=" + serviceAccountKeyFile + "&project=" + project
                                                      + "&location="
                                                      + location + "&operation=getFunction")
                        .log("body:${body}").to("direct:getFunction").to("mock:getFunction");

                // generate download url
                from("timer:timer1?repeatCount=1").to("google-functions://" + functionNameReverseString
                                                      + "?serviceAccountKey=" + serviceAccountKeyFile + "&project=" + project
                                                      + "&location="
                                                      + location + "&operation=generateDownloadUrl")
                        .log("body:${body}").to("mock:generateDownloadUrl");

                // call function
                from("timer:timer1?repeatCount=1").process(exchange -> {
                    exchange.getIn().setBody("just a message");
                }).to("google-functions://" + functionNameReverseString + "?serviceAccountKey=" + serviceAccountKeyFile
                      + "&project=" + project + "&location=" + location + "&operation=callFunction")
                        .log("body:${body}").to("mock:callFunction");

                // create function
                final String randomFunctionName = createRandomFunctionName();
                from("timer:timer1?repeatCount=1").process(exchange -> {
                    exchange.getIn().setHeader(GoogleCloudFunctionsConstants.ENTRY_POINT, functionCreationEntryPoint);
                    exchange.getIn().setHeader(GoogleCloudFunctionsConstants.RUNTIME, "java11");
                    exchange.getIn().setHeader(GoogleCloudFunctionsConstants.SOURCE_ARCHIVE_URL,
                            functionCreationSourceCode);
                }).to("google-functions://" + randomFunctionName + "?serviceAccountKey=" + serviceAccountKeyFile
                      + "&project=" + project + "&location=" + location + "&operation=createFunction")
                        .log("body:${body}").to("mock:createFunction");

                // delete function
                from("timer:timer1?repeatCount=1")
                        .to("google-functions://" + "randomFunction_0" + "?serviceAccountKey=" + serviceAccountKeyFile
                            + "&project=" + project + "&location=" + location + "&operation=deleteFunction")
                        .log("body:${body}");

            }
        };
    }

    @Test
    public void sendIn() throws Exception {
        mockFunctionList1.expectedMinimumMessageCount(1);
        mockGenerateDownloadUrl.expectedMessageCount(1);
        mockCallFunction.expectedMessageCount(1);
        mockCreateFunction.expectedMessageCount(1);

        Thread.sleep(10000);
        int functionListCounter = mockFunctionList1.getReceivedCounter();
        mockGetFunction.expectedMessageCount(functionListCounter);

        MockEndpoint.assertIsSatisfied(context);
    }

    private String createRandomFunctionName() {
        int r = (int) (Math.random() * 10000);
        return "randomFunction_" + r;
    }

}
