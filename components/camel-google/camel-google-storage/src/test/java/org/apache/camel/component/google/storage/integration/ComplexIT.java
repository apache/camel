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
package org.apache.camel.component.google.storage.integration;

import java.io.ByteArrayInputStream;
import java.util.List;

import com.google.cloud.storage.Blob;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.storage.GoogleCloudStorageConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_APPLICATION_CREDENTIALS", matches = ".*",
                              disabledReason = "Application credentials were not provided")
public class ComplexIT extends CamelTestSupport {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:bucket1")
    private MockEndpoint mockBucket1;

    @EndpointInject("mock:bucket2")
    private MockEndpoint mockBucket2;

    @EndpointInject("mock:processed")
    private MockEndpoint mockProcessed;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                final int numberOfObjects = 3;
                //final String serviceAccountKeyFile = "somefile.json";
                final String serviceAccountKeyFile = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
                final String bucket1 = "camel_test_bucket1";
                final String bucket2 = "camel_test_bucket2";
                final String bucket3 = "camel_test_processed_bucket";
                final String bucket4 = "camel_test_bucket4";

                //upload 3 file into bucket1
                byte[] payload = "Camel rocks!".getBytes();
                ByteArrayInputStream bais = new ByteArrayInputStream(payload);
                from("timer:timer1?repeatCount=" + numberOfObjects)
                        .process(exchange -> {
                            String filename = "file_" + ((int) (Math.random() * 10000)) + ".txt";
                            exchange.getIn().setHeader(GoogleCloudStorageConstants.OBJECT_NAME, filename);
                            exchange.getIn().setBody(bais);
                        })
                        .to("google-storage://" + bucket1 + "?serviceAccountKey=file:" + serviceAccountKeyFile)
                        .log("upload file object:${header.CamelGoogleCloudStorageObjectName}, body:${body}")
                        .to("mock:bucket1");

                //poll from bucket1, moving processed into bucket_processed and deleting original
                from("google-storage://" + bucket1 + "?serviceAccountKey=file:" + serviceAccountKeyFile
                     + "&moveAfterRead=true"
                     + "&destinationBucket=" + bucket3
                     + "&autoCreateBucket=true"
                     + "&deleteAfterRead=true"
                     + "&includeBody=true")
                        .log("consuming: ${header.CamelGoogleCloudStorageBucketName}/${header.CamelGoogleCloudStorageObjectName}")
                        .to("direct:processed")
                        .to("mock:processed");

                //upload these files to bucket2
                from("direct:processed")
                        .to("google-storage://" + bucket2 + "?serviceAccountKey=file:" + serviceAccountKeyFile)
                        .log("uploaded file object:${header.CamelGoogleCloudStorageObjectName}, body:${body}")
                        .process(exchange -> {
                            exchange.getIn().setHeader(GoogleCloudStorageConstants.DOWNLOAD_LINK_EXPIRATION_TIME, 86400000L); //1 day
                        })
                        .to("google-storage://" + bucket2 + "?serviceAccountKey=file:" + serviceAccountKeyFile
                            + "&operation=createDownloadLink")
                        .log("URL for ${header.CamelGoogleCloudStorageBucketName}/${header.CamelGoogleCloudStorageObjectName} =${body}")
                        .to("mock:bucket2");

                //list all buckets
                from("timer:timer1?repeatCount=1&fixedRate=true&period=10000")
                        .to("google-storage://" + bucket2 + "?serviceAccountKey=file:" + serviceAccountKeyFile
                            + "&operation=listBuckets")
                        .log("list buckets:${body}");

                //list all object of the bucket2 and send result to direct:moreinfo and direct:copy
                from("timer:timer1?repeatCount=1&fixedRate=true&period=10000")
                        .to("google-storage://" + bucket2 + "?serviceAccountKey=file:" + serviceAccountKeyFile
                            + "&operation=listObjects")
                        .log("list " + bucket2 + " objects body:${body}")
                        .split(bodyAs(List.class))
                        .log("splitted: ${body}")
                        .multicast().to("direct:moreinfo", "direct:copy");

                from("direct:moreinfo")
                        .process(exchange -> {
                            Blob blob = exchange.getIn().getBody(Blob.class);
                            String fileName = blob.getName();
                            exchange.getIn().setHeader(GoogleCloudStorageConstants.OBJECT_NAME, fileName);
                        })
                        .to("google-storage://" + bucket2 + "?serviceAccountKey=file:" + serviceAccountKeyFile
                            + "&operation=getObject")
                        .log("get object bucket:${header.CamelGoogleCloudStorageBucketName} object:${header.CamelGoogleCloudStorageObjectName}, body:${body}");

                //copy object
                from("direct:copy")
                        .process(exchange -> {
                            Blob blob = exchange.getIn().getBody(Blob.class);
                            String fileName = blob.getName();
                            String copyFileName = "copy_" + fileName;
                            exchange.getIn().setHeader(GoogleCloudStorageConstants.OBJECT_NAME, fileName);
                            exchange.getIn().setHeader(GoogleCloudStorageConstants.DESTINATION_BUCKET_NAME, bucket4);
                            exchange.getIn().setHeader(GoogleCloudStorageConstants.DESTINATION_OBJECT_NAME, copyFileName);
                        })
                        .to("google-storage://" + bucket2 + "?serviceAccountKey=file:" + serviceAccountKeyFile
                            + "&operation=copyObject")
                        .log("${body}");

            }
        };
    }

    @Test
    public void sendIn() throws Exception {
        mockBucket1.expectedMessageCount(3);
        mockBucket2.expectedMessageCount(3);
        mockProcessed.expectedMessageCount(3);

        Thread.sleep(10000);
        MockEndpoint.assertIsSatisfied(context);
    }

}
