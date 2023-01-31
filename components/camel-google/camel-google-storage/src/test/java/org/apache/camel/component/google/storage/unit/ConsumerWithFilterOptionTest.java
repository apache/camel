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

import java.nio.file.Path;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.storage.GoogleCloudStorageConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ConsumerWithFilterOptionTest extends GoogleCloudStorageBaseTest {

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
                     + "&includeBody=true"
                     + "&filter=.*.csv")
                        .startupOrder(2)
                        .to("mock:consumedObjects");

            }
        };
    }

    @Test
    public void onlyCsvFilesShouldBeFiltered(@TempDir Path tempDir) throws Exception {

        final int totalNumberOfFiles = 5;
        final int numberOfFilteredFiles = 2;

        Path path = tempDir.resolve("file");

        uploadFiles(path.toString(), "csv", 2);
        uploadFiles(path.toString(), "txt", 3);

        result.expectedMessageCount(totalNumberOfFiles);
        consumedObjects.expectedMessageCount(numberOfFilteredFiles);

        MockEndpoint.assertIsSatisfied(context);

        context.stop();

    }

    @Test
    public void noFilesShouldBeFitered(@TempDir Path tempDir) throws Exception {

        final int totalNumberOfFiles = 5;
        final int numberOfFilteredFiles = 0;

        Path path = tempDir.resolve("file");

        uploadFiles(path.toString(), "json", 2);
        uploadFiles(path.toString(), "txt", 3);

        result.expectedMessageCount(totalNumberOfFiles);
        consumedObjects.expectedMessageCount(numberOfFilteredFiles);

        MockEndpoint.assertIsSatisfied(context);

        context.stop();

    }

    private void uploadFiles(final String fileNamePrefix, final String extension, final int numberOfFiles) {

        for (int i = 0; i < numberOfFiles; i++) {

            final String filename = String.format("%s_%s.%s", fileNamePrefix, i, extension);
            final String body = String.format("body_%s", i);
            //upload a file

            template.send("direct:putObject", exchange -> {
                exchange.getIn().setHeader(GoogleCloudStorageConstants.OBJECT_NAME, filename);
                exchange.getIn().setBody(body);
            });
        }
    }

}
