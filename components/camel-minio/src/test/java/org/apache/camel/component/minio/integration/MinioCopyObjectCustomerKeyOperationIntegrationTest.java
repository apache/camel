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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Properties;
import java.util.UUID;

import javax.crypto.KeyGenerator;

import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
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

    final Properties properties = MinioTestUtils.loadMinioPropertiesFile();
    final ServerSideEncryptionCustomerKey secretKey = generateSecretKey();
    String key = UUID.randomUUID().toString();

    @BindToRegistry("minioClient")
    MinioClient minioClient =
            MinioClient.builder()
                    .endpoint(properties.getProperty("endpoint"))
                    .credentials(properties.getProperty("accessKey"), properties.getProperty("secretKey"))
                    .build();
    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    public MinioCopyObjectCustomerKeyOperationIntegrationTest() throws IOException {
    }

    @Test
    public void sendIn() throws Exception {

        result.expectedMessageCount(1);

        template.send("direct:putObject", exchange -> {
            String string = "Test";

            //use ByteArrayInputStream to get the bytes of the String and convert them to InputStream.
            InputStream inputStream = new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));

            PutObjectArgs.Builder putObjectRequest = PutObjectArgs.builder()
                    .stream(inputStream, inputStream.available(), -1)
                    .bucket("mycamel")
                    .object("test.txt")
                    .sse(secretKey);

            exchange.getIn().setBody(putObjectRequest);
        });

        template.send("direct:copyObject", exchange -> {

            CopySource.Builder copySourceBuilder = CopySource.builder()
                    .bucket("mycamel")
                    .object("test.txt")
                    .ssec(secretKey);

            CopyObjectArgs.Builder copyObjectRequest = CopyObjectArgs.builder()
                    .bucket("mycamel1")
                    .object("test1.txt")
                    .source(copySourceBuilder.build())
                    .sse(secretKey);

            exchange.getIn().setHeader(MinioConstants.MINIO_OPERATION, MinioOperations.copyObject);
            exchange.getIn().setBody(copyObjectRequest);
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
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String minioEndpoint = "minio://mycamel?autoCreateBucket=false&pojoRequest=true";
                String minioEndpoint1 = "minio://mycamel1?autoCreateBucket=false&pojoRequest=true";

                from("direct:putObject").to(minioEndpoint);

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
                textBuilder.append((char) c);
            }
        }
        return textBuilder.toString();
    }
}
