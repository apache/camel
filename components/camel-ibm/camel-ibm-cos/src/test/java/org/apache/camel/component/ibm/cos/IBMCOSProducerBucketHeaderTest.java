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
package org.apache.camel.component.ibm.cos;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ibm.cloud.objectstorage.auth.AWSStaticCredentialsProvider;
import com.ibm.cloud.objectstorage.auth.BasicAWSCredentials;
import com.ibm.cloud.objectstorage.client.builder.AwsClientBuilder;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3ClientBuilder;
import com.ibm.cloud.objectstorage.services.s3.model.PutObjectRequest;
import com.ibm.cloud.objectstorage.services.s3.model.PutObjectResult;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The producer stores an object in the bucket of its endpoint, not in the bucket named by the header the consumer sets
 * on the objects it consumes, so an object routed from one bucket to another is not written back to (and with
 * deleteAfterRead then deleted from) the bucket it was consumed from. The override header still selects the bucket.
 */
class IBMCOSProducerBucketHeaderTest extends CamelTestSupport {

    private final List<String> puts = new CopyOnWriteArrayList<>();
    private FakeS3Server s3;
    private AmazonS3 sourceClient;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        s3 = new FakeS3Server("source");
        s3.start();
        sourceClient = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("xxx", "yyy")))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(s3.endpoint(), "us-south"))
                .withPathStyleAccessEnabled(true)
                .build();
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("sourceClient", sourceClient);
        context.getRegistry().bind("targetClient", recordingClient());
        return context;
    }

    // records the bucket and key of each object put, answers that every bucket exists
    private AmazonS3 recordingClient() {
        return (AmazonS3) Proxy.newProxyInstance(AmazonS3.class.getClassLoader(), new Class<?>[] { AmazonS3.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "putObject" -> {
                        PutObjectRequest request = (PutObjectRequest) args[0];
                        puts.add(request.getBucketName() + "/" + request.getKey());
                        yield new PutObjectResult();
                    }
                    case "doesBucketExistV2" -> true;
                    case "toString" -> "recordingClient";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @AfterEach
    void stopServer() {
        if (sourceClient != null) {
            sourceClient.shutdown();
        }
        if (s3 != null) {
            s3.stop();
        }
    }

    @Test
    void consumedObjectIsStoredInTheBucketOfTheProducerEndpoint() throws Exception {
        s3.put("a.txt", "hello");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hello");
        mock.expectedHeaderReceived(IBMCOSConstants.BUCKET_NAME, "source");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ibm-cos://source?cosClient=#sourceClient&delay=10")
                        .convertBodyTo(String.class)
                        .to("ibm-cos://target?cosClient=#targetClient")
                        .to("mock:result");
            }
        });

        mock.assertIsSatisfied();
        assertEquals(List.of("target/a.txt"), puts);
    }

    @Test
    void overrideBucketNameHeaderSelectsTheBucket() {
        template.sendBodyAndHeader("ibm-cos://target?cosClient=#targetClient", "hello",
                IBMCOSConstants.KEY, "b.txt");
        template.send("ibm-cos://target?cosClient=#targetClient", exchange -> {
            exchange.getIn().setBody("hello");
            exchange.getIn().setHeader(IBMCOSConstants.KEY, "c.txt");
            exchange.getIn().setHeader(IBMCOSConstants.OVERRIDE_BUCKET_NAME, "other");
        });

        assertEquals(List.of("target/b.txt", "other/c.txt"), puts);
    }
}
