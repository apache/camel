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
package org.apache.camel.component.minio;

import java.util.Properties;

import io.minio.MinioClient;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MinioComponentConfigurationTest extends CamelTestSupport {

    @Test
    void createEndpointWithMinimalConfiguration() throws Exception {
        MinioComponent component = context.getComponent("minio", MinioComponent.class);
        MinioEndpoint endpoint = (MinioEndpoint) component
                .createEndpoint(
                        "minio://TestDomain?accessKey=xxx&secretKey=yyy&region=us-west-1&endpoint=http://localhost:4572");
        assertEquals("TestDomain", endpoint.getConfiguration().getBucketName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("us-west-1", endpoint.getConfiguration().getRegion());
        assertEquals("http://localhost:4572", endpoint.getConfiguration().getEndpoint());
    }

    @Test
    void createEndpointWithCredentialsAndClientExistInRegistry() throws Exception {
        final Properties properties = MinioTestUtils.loadMinioPropertiesFile();

        MinioClient client = MinioClient.builder()
                .endpoint(properties.getProperty("endpoint"))
                .build();
        context.getRegistry().bind("minioClient", client);
        MinioComponent component = context.getComponent("minio", MinioComponent.class);
        MinioEndpoint endpoint = (MinioEndpoint) component
                .createEndpoint("minio://MyBucket?accessKey=RAW(XXX)&secretKey=RAW(XXX)&region=eu-west-1");

        assertEquals("MyBucket", endpoint.getConfiguration().getBucketName());
        assertSame(client, endpoint.getConfiguration().getMinioClient());
    }

    @Test
    void createEndpoinWthAutowiredDisabledAndClientExistInRegistry() throws Exception {
        final Properties properties = MinioTestUtils.loadMinioPropertiesFile();

        context.setAutowiredEnabled(false);
        MinioClient client = MinioClient.builder()
                .endpoint(properties.getProperty("endpoint"))
                .build();
        context.getRegistry().bind("minioClient", client);
        MinioComponent component = context.getComponent("minio", MinioComponent.class);
        MinioEndpoint endpoint = (MinioEndpoint) component
                .createEndpoint(
                        "minio://MyBucket?accessKey=RAW(XXX)&secretKey=RAW(XXX)&region=eu-west-1&endpoint=https://play.min.io");
        context.setAutowiredEnabled(true);
        assertEquals("MyBucket", endpoint.getConfiguration().getBucketName());
        assertNull(endpoint.getConfiguration().getMinioClient());
    }
}
