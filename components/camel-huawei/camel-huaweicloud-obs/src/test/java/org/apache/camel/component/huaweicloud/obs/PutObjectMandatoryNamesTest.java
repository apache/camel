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
package org.apache.camel.component.huaweicloud.obs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.obs.services.ObsClient;
import com.obs.services.model.PutObjectResult;
import com.obs.services.model.StorageClassEnum;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.component.huaweicloud.obs.constants.OBSProperties;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bucket name is mandatory for putObject with every body. The object name is mandatory unless the body is a file,
 * whose name is the default.
 */
class PutObjectMandatoryNamesTest extends CamelTestSupport {

    TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("obsClient")
    ObsClient mockClient = Mockito.mock(ObsClient.class);

    @BindToRegistry("serviceKeys")
    ServiceKeys serviceKeys = new ServiceKeys(
            testConfiguration.getProperty("accessKey"),
            testConfiguration.getProperty("secretKey"));

    @TempDir
    Path tempDir;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:put_object")
                        .to("hwcloud-obs:putObject?" +
                            "serviceKeys=#serviceKeys" +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&ignoreSslVerification=true" +
                            "&obsClient=#obsClient");
            }
        };
    }

    @Test
    void fileWithoutBucketNameFails() throws Exception {
        Exchange exchange = putObject(file(), null, null);

        assertBucketNameMandatory(exchange);
    }

    @Test
    void fileOfFileConsumerWithoutBucketNameFails() throws Exception {
        File file = file();
        GenericFile<File> genericFile = new GenericFile<>();
        genericFile.setFile(file);
        genericFile.setFileName(file.getName());
        genericFile.setAbsoluteFilePath(file.getAbsolutePath());

        Exchange exchange = putObject(genericFile, null, null);

        assertBucketNameMandatory(exchange);
    }

    @Test
    void textWithoutBucketNameFails() {
        Exchange exchange = putObject("content", null, "object.txt");

        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        Mockito.verifyNoInteractions(mockClient);
    }

    @Test
    void textWithoutObjectNameFails() {
        Exchange exchange = putObject("content", "reji-abc", null);

        Exception failure = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(failure.getMessage().toLowerCase().contains("object name"), failure.getMessage());
        Mockito.verifyNoInteractions(mockClient);
    }

    @Test
    void fileWithoutObjectNameUsesTheFileName() throws Exception {
        File file = file();
        Mockito.when(mockClient.headBucket("reji-abc")).thenReturn(true);
        Mockito.when(mockClient.putObject("reji-abc", "test_file.txt", file))
                .thenReturn(new PutObjectResult(
                        "reji-abc", "test_file.txt", "etag", null, StorageClassEnum.STANDARD, null));

        Exchange exchange = putObject(file, "reji-abc", null);

        assertNull(exchange.getException(), () -> "putObject failed: " + exchange.getException());
        Mockito.verify(mockClient).putObject("reji-abc", "test_file.txt", file);
        assertTrue(exchange.getMessage().getBody(String.class).contains("\"objectKey\":\"test_file.txt\""));
    }

    private File file() throws Exception {
        return Files.writeString(tempDir.resolve("test_file.txt"), "sample file content", StandardCharsets.UTF_8).toFile();
    }

    private Exchange putObject(Object body, String bucketName, String objectName) {
        return template.send("direct:put_object", e -> {
            e.getMessage().setBody(body);
            if (bucketName != null) {
                e.setProperty(OBSProperties.BUCKET_NAME, bucketName);
            }
            if (objectName != null) {
                e.setProperty(OBSProperties.OBJECT_NAME, objectName);
            }
        });
    }

    private void assertBucketNameMandatory(Exchange exchange) {
        assertNotNull(exchange.getException(), "putObject should fail without a bucket name");
        Exception failure = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertEquals("Bucket name is mandatory to put objects into bucket", failure.getMessage());
        // the bucket was not checked, created or written with a null name
        Mockito.verifyNoInteractions(mockClient);
    }
}
