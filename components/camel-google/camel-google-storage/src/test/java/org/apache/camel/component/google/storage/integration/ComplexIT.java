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
import java.net.URI;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.storage.GoogleCloudStorageConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.infra.google.storage.services.GoogleStorageLocalContainerInfraService;
import org.apache.camel.test.infra.google.storage.services.GoogleStorageService;
import org.apache.camel.test.infra.google.storage.services.GoogleStorageServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Runs against the Cloud Storage service of Floci GCP, or with {@code -Dgoogle.instance.type=remote} against the one of
 * the project of the application default credentials, such as the key of the {@code GOOGLE_APPLICATION_CREDENTIALS}
 * environment variable.
 */
class ComplexIT extends CamelTestSupport {

    @RegisterExtension
    static GoogleStorageService service = GoogleStorageServiceFactory.createService();

    @EndpointInject("mock:bucket1")
    private MockEndpoint mockBucket1;

    @EndpointInject("mock:bucket2")
    private MockEndpoint mockBucket2;

    @EndpointInject("mock:processed")
    private MockEndpoint mockProcessed;

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        if (service instanceof GoogleStorageLocalContainerInfraService) {
            // the endpoints take the client of the registry; against Google Cloud, they build their own
            registry.bind("storageClient", createLocalStorageClient());
        }
    }

    // Floci GCP gives an access token to a service account of any key, at the token address of the key: unlike no
    // credentials, the ones of a key made for the test can sign the download links
    private static Storage createLocalStorageClient() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        ServiceAccountCredentials credentials = ServiceAccountCredentials.newBuilder()
                .setClientEmail("camel@" + service.projectId() + ".iam.gserviceaccount.com")
                .setPrivateKeyId("camel")
                .setPrivateKey(generator.generateKeyPair().getPrivate())
                .setProjectId(service.projectId())
                .setTokenServerUri(URI.create(service.endpoint() + "/token"))
                .build();
        return StorageOptions.newBuilder()
                .setHost(service.endpoint())
                .setProjectId(service.projectId())
                .setCredentials(credentials)
                .build()
                .getService();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                final int numberOfObjects = 3;
                final String bucket1 = "camel_test_bucket1";
                final String bucket2 = "camel_test_bucket2";
                final String bucket3 = "camel_test_processed_bucket";
                final String bucket4 = "camel_test_bucket4";

                //upload 3 file into bucket1
                byte[] payload = "Camel rocks!".getBytes();
                from("timer:timer1?includeMetadata=true&repeatCount=" + numberOfObjects)
                        .process(exchange -> {
                            String filename = "file_" + exchange.getProperty(Exchange.TIMER_COUNTER) + ".txt";
                            exchange.getIn().setHeader(GoogleCloudStorageConstants.OBJECT_NAME, filename);
                            exchange.getIn().setBody(new ByteArrayInputStream(payload));
                        })
                        .to("google-storage://" + bucket1)
                        .log("upload file object:${header.CamelGoogleCloudStorageObjectName}, body:${body}")
                        .to("mock:bucket1");

                //poll from bucket1, moving processed into bucket_processed and deleting original
                from("google-storage://" + bucket1
                     + "?moveAfterRead=true"
                     + "&destinationBucket=" + bucket3
                     + "&autoCreateBucket=true"
                     + "&deleteAfterRead=true"
                     + "&includeBody=true")
                        .log("consuming: ${header.CamelGoogleCloudStorageBucketName}/${header.CamelGoogleCloudStorageObjectName}")
                        .to("direct:processed")
                        .to("mock:processed");

                //upload these files to bucket2
                from("direct:processed")
                        .to("google-storage://" + bucket2)
                        .log("uploaded file object:${header.CamelGoogleCloudStorageObjectName}, body:${body}")
                        .process(exchange -> {
                            exchange.getIn().setHeader(GoogleCloudStorageConstants.DOWNLOAD_LINK_EXPIRATION_TIME, 86400000L); //1 day
                        })
                        .to("google-storage://" + bucket2
                            + "?operation=createDownloadLink")
                        .log("URL for ${header.CamelGoogleCloudStorageBucketName}/${header.CamelGoogleCloudStorageObjectName} =${body}")
                        .to("mock:bucket2");

                //list all buckets
                from("timer:timer1?repeatCount=1&fixedRate=true&period=10000")
                        .to("google-storage://" + bucket2
                            + "?operation=listBuckets")
                        .log("list buckets:${body}");

                //list all object of the bucket2 and send result to direct:moreinfo and direct:copy
                from("timer:timer1?repeatCount=1&fixedRate=true&period=10000")
                        .to("google-storage://" + bucket2
                            + "?operation=listObjects")
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
                        .to("google-storage://" + bucket2
                            + "?operation=getObject")
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
                        .to("google-storage://" + bucket2
                            + "?operation=copyObject")
                        .log("${body}");

            }
        };
    }

    @Test
    void sendIn() throws Exception {
        mockBucket1.expectedMessageCount(3);
        mockBucket2.expectedMessageCount(3);
        mockProcessed.expectedMessageCount(3);

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

}
