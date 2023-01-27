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
package org.apache.camel.component.google.storage.unit;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.storage.GoogleCloudStorageConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class ConsumerLocalTest extends GoogleCloudStorageBaseTest {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @EndpointInject("mock:consumedObjects")
    private MockEndpoint consumedObjects;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                String endpoint = "google-storage://myCamelBucket?autoCreateBucket=true";

                from("direct:putObject")
                        .startupOrder(1)
                        .to(endpoint)
                        .to("mock:result");

                from("google-storage://myCamelBucket?"
                     + "moveAfterRead=true"
                     + "&destinationBucket=camelDestinationBucket"
                     + "&autoCreateBucket=true"
                     + "&deleteAfterRead=true"
                     + "&includeBody=true")
                        .startupOrder(2)
                        //.log("consuming: ${header.CamelGoogleCloudStorageBucketName}/${header.CamelGoogleCloudStorageObjectName}, body=${body}")
                        .to("mock:consumedObjects");

            }
        };
    }

    @Test
    public void sendIn() throws Exception {

        final int numberOfFiles = 3;

        result.expectedMessageCount(numberOfFiles);
        consumedObjects.expectedMessageCount(numberOfFiles);

        for (int i = 0; i < numberOfFiles; i++) {
            final String filename = String.format("file_%s.txt", i);
            final String body = String.format("body_%s", i);
            //upload a file
            template.send("direct:putObject", exchange -> {
                exchange.getIn().setHeader(GoogleCloudStorageConstants.OBJECT_NAME, filename);
                exchange.getIn().setBody(body);
            });
        }

        MockEndpoint.assertIsSatisfied(context);

    }

}
