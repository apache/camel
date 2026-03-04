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
package org.apache.camel.component.aws2.s3.stream;

import java.lang.reflect.Method;
import java.util.UUID;

import org.apache.camel.component.aws2.s3.AWS2S3Configuration;
import org.apache.camel.component.aws2.s3.AWS2S3Endpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class AWSS3NamingStrategyTest extends CamelTestSupport {

    @Test
    public void testProgressiveNamingStrategy() throws Exception {
        AWS2S3StreamUploadProducer producer = createProducer();

        Method method = AWS2S3StreamUploadProducer.class.getDeclaredMethod(
                "fileNameToUpload", String.class, AWSS3NamingStrategyEnum.class, String.class, int.class, UUID.class,
                long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(producer, "testFile", AWSS3NamingStrategyEnum.progressive, ".txt", 0, null, 0L);
        assertEquals("testFile.txt", result);

        result = (String) method.invoke(producer, "testFile", AWSS3NamingStrategyEnum.progressive, ".txt", 1, null, 0L);
        assertEquals("testFile-1.txt", result);

        result = (String) method.invoke(producer, "testFile", AWSS3NamingStrategyEnum.progressive, null, 2, null, 0L);
        assertEquals("testFile-2", result);
    }

    @Test
    public void testRandomNamingStrategy() throws Exception {
        AWS2S3StreamUploadProducer producer = createProducer();

        Method method = AWS2S3StreamUploadProducer.class.getDeclaredMethod(
                "fileNameToUpload", String.class, AWSS3NamingStrategyEnum.class, String.class, int.class, UUID.class,
                long.class);
        method.setAccessible(true);

        UUID testUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        String result = (String) method.invoke(producer, "testFile", AWSS3NamingStrategyEnum.random, ".txt", 0, testUuid, 0L);
        assertEquals("testFile.txt", result);

        result = (String) method.invoke(producer, "testFile", AWSS3NamingStrategyEnum.random, ".txt", 1, testUuid, 0L);
        assertEquals("testFile-123e4567-e89b-12d3-a456-426614174000.txt", result);

        result = (String) method.invoke(producer, "testFile", AWSS3NamingStrategyEnum.random, null, 1, testUuid, 0L);
        assertEquals("testFile-123e4567-e89b-12d3-a456-426614174000", result);
    }

    @Test
    public void testTimestampNamingStrategy() throws Exception {
        AWS2S3StreamUploadProducer producer = createProducer();

        Method method = AWS2S3StreamUploadProducer.class.getDeclaredMethod(
                "fileNameToUpload", String.class, AWSS3NamingStrategyEnum.class, String.class, int.class, UUID.class,
                long.class);
        method.setAccessible(true);

        long testTimestamp = 1632468273000L; // September 24, 2021 1:04:33 PM GMT

        String result = (String) method.invoke(producer, "testFile", AWSS3NamingStrategyEnum.timestamp, ".txt", 0, null,
                testTimestamp);
        assertEquals("testFile.txt", result);

        result = (String) method.invoke(producer, "testFile", AWSS3NamingStrategyEnum.timestamp, ".txt", 1, null,
                testTimestamp);
        assertEquals("testFile-1632468273000.txt", result);

        result = (String) method.invoke(producer, "testFile", AWSS3NamingStrategyEnum.timestamp, null, 1, null, testTimestamp);
        assertEquals("testFile-1632468273000", result);
    }

    @Test
    public void testTimestampNamingStrategyWithCurrentTime() throws Exception {
        AWS2S3StreamUploadProducer producer = createProducer();

        Method method = AWS2S3StreamUploadProducer.class.getDeclaredMethod(
                "fileNameToUpload", String.class, AWSS3NamingStrategyEnum.class, String.class, int.class, UUID.class,
                long.class);
        method.setAccessible(true);

        long currentTime = System.currentTimeMillis();

        String result
                = (String) method.invoke(producer, "testFile", AWSS3NamingStrategyEnum.timestamp, ".txt", 1, null, currentTime);

        assertTrue(result.startsWith("testFile-"));
        assertTrue(result.endsWith(".txt"));

        String timestampPart = result.substring("testFile-".length(), result.length() - ".txt".length());
        long parsedTimestamp = Long.parseLong(timestampPart);

        // Allow for small time difference during test execution
        assertTrue(Math.abs(parsedTimestamp - currentTime) < 1000,
                "Timestamp should be within 1 second of current time");
    }

    private AWS2S3StreamUploadProducer createProducer() {
        AWS2S3Endpoint endpoint = Mockito.mock(AWS2S3Endpoint.class);
        AWS2S3Configuration configuration = new AWS2S3Configuration();
        when(endpoint.getConfiguration()).thenReturn(configuration);

        return new AWS2S3StreamUploadProducer(endpoint);
    }
}
