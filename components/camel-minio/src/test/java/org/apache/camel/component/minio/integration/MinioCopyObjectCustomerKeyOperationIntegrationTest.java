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
package org.apache.camel.component.minio.integration;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;

import javax.crypto.KeyGenerator;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.ServerSideEncryption;
import io.minio.ServerSideEncryptionCustomerKey;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.minio.MinioConstants;
import org.apache.camel.component.minio.MinioOperations;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Disabled("Must be manually tested. Provide your own accessKey and secretKey!")
public class MinioCopyObjectCustomerKeyOperationIntegrationTest extends CamelTestSupport {
    
    String key = UUID.randomUUID().toString();
    ServerSideEncryptionCustomerKey secretKey = generateSecretKey();

    @BindToRegistry("minioClient")
    MinioClient minioClient =
            MinioClient.builder()
                    .endpoint("https://play.min.io")
                    .credentials("Q3AM3UQ867SPQQA43P2F", "zuf+tfteSlswRu7BJ86wekitnifILbZam1KYY3TG")
                    .build();
    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendIn() throws Exception {

        result.expectedMessageCount(1);

        template.send("direct:putObject", exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "test.txt");
            exchange.getIn().setBody("Test");
        });

        template.send("direct:copyObject", exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "test.txt");
            exchange.getIn().setHeader(MinioConstants.DESTINATION_OBJECT_NAME, "test1.txt");
            exchange.getIn().setHeader(MinioConstants.BUCKET_DESTINATION_NAME, "mycamel1");
            exchange.getIn().setHeader(MinioConstants.MINIO_OPERATION, MinioOperations.copyObject);
        });
        
        Exchange respond = template.request("direct:getObject", exchange -> {
            GetObjectArgs.Builder getObjectRequest = GetObjectArgs.builder()
                    .object("test1.txt")
                    .bucket("mycamel1")
                    .ssec(secretKey);
            exchange.getIn().setHeader(MinioConstants.MINIO_OPERATION, MinioOperations.getObject);
            exchange.getIn().setBody(getObjectRequest);
        });
        
        InputStream minio = respond.getIn().getBody(InputStream.class);
        

        assertEquals("Test", readInputStream(minio));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String minioEndpoint = "minio://mycamel?autoCreateBucket=false";
                String minioEndpoint1 = "minio://mycamel1?autoCreateBucket=false&pojoRequest=true";
                from("direct:putObject").setHeader(MinioConstants.OBJECT_NAME, constant("test.txt")).setBody(constant("Test")).to(minioEndpoint);

                from("direct:copyObject").to(minioEndpoint);
                
                from("direct:getObject").to(minioEndpoint1).to("mock:result");

            }
        };
    }
    
    protected static ServerSideEncryptionCustomerKey generateSecretKey() {
        KeyGenerator generator;
        try {
            generator = KeyGenerator.getInstance("AES");
            generator.init(256, new SecureRandom());
            return ServerSideEncryption.withCustomerKey(generator.generateKey());
        } catch (Exception e) {
            fail("Unable to generate symmetric key: " + e.getMessage());
            return null;
        }
    }
    
    private String readInputStream(InputStream minioObject) throws IOException {
        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(minioObject, Charset.forName(StandardCharsets.UTF_8.name())))) {
            int c;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char)c);
            }
        }
        return textBuilder.toString();
    }
}
