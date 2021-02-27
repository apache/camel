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

import com.google.cloud.functions.v1.CloudFunction;
import com.google.cloud.functions.v1.CloudFunctionsServiceClient.ListFunctionsPagedResponse;
import com.google.cloud.functions.v1.ListFunctionsResponse;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.google.functions.unit.GoogleCloudFunctionsBaseTest.mockCloudFunctionsService;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GoogleCloudFunctionsComponentTest extends GoogleCloudFunctionsBaseTest {

    private static final Logger log = LoggerFactory.getLogger(GoogleCloudFunctionsComponentTest.class);

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void listFunctionsTest() throws Exception {

        CloudFunction responsesElement = CloudFunction.newBuilder().build();
        ListFunctionsResponse expectedResponse = ListFunctionsResponse.newBuilder().setNextPageToken("")
                .addAllFunctions(Arrays.asList(responsesElement)).build();
        mockCloudFunctionsService.addResponse(expectedResponse);

        Exchange exchange = template.send("direct:listFunctions", ExchangePattern.InOut, exc -> {
        });
        log.info("body: " + exchange.getMessage().getBody());
        ListFunctionsPagedResponse result = exchange.getMessage().getBody(ListFunctionsPagedResponse.class);
        assertNotNull(result);
        // assertEquals(1, result.getPage().);
        // assertEquals("GetHelloWithName", result.functions().get(0).functionName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        log.info("createRouteBuilder");
        return new RouteBuilder() {
            public void configure() {

                from("direct:listFunctions")
                        // .to("google-functions://myCamelFunction?client=#client&operation=listFunctions")
                        .to("google-functions://myCamelFunction?project=proj1&location=loc1&operation=listFunctions")
                        .to("mock:result");
            }
        };
    }
}
