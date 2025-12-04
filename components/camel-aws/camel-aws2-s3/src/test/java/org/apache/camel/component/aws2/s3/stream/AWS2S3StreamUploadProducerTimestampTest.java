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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.component.aws2.s3.AWS2S3Configuration;
import org.apache.camel.component.aws2.s3.AWS2S3Endpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AWS2S3StreamUploadProducerTimestampTest {

    @Mock
    private AWS2S3Endpoint endpoint;

    @Mock
    private AWS2S3Configuration configuration;

    private AWS2S3StreamUploadProducer producer;
    private DefaultCamelContext camelContext;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        camelContext = new DefaultCamelContext();

        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getCamelContext()).thenReturn(camelContext);

        producer = new AWS2S3StreamUploadProducer(endpoint);
    }

    @Test
    public void testGetTimestampWindow() throws Exception {
        // Configure 5-minute windows (300000ms)
        when(configuration.getTimestampWindowSizeMillis()).thenReturn(300000L);

        Method method = AWS2S3StreamUploadProducer.class.getDeclaredMethod("getTimestampWindow", long.class);
        method.setAccessible(true);

        // Test timestamp: 2024-01-01 08:02:30 UTC (1704096150000)
        long timestamp = 1704096150000L;
        long expectedWindow = 1704096000000L; // Should round down to 08:00:00

        long actualWindow = (Long) method.invoke(producer, timestamp);
        assertEquals(expectedWindow, actualWindow, "Should round down to window start");

        // Test exact window boundary
        long boundaryTimestamp = 1704096000000L; // Exactly 08:00:00
        long boundaryWindow = (Long) method.invoke(producer, boundaryTimestamp);
        assertEquals(boundaryTimestamp, boundaryWindow, "Boundary timestamp should map to itself");

        // Test just before next window
        long beforeNextWindow = 1704096299999L; // 07:59:59.999
        long beforeNextWindowResult = (Long) method.invoke(producer, beforeNextWindow);
        assertEquals(1704096000000L, beforeNextWindowResult, "Should still be in same window");
    }

    @Test
    public void testGenerateTimestampBasedFileName() throws Exception {
        Method method = AWS2S3StreamUploadProducer.class.getDeclaredMethod(
                "generateTimestampBasedFileName", String.class, String.class, long.class);
        method.setAccessible(true);

        when(configuration.getTimestampWindowSizeMillis()).thenReturn(300000L); // 5 minutes

        // Test with extension
        long timestampWindow = 1704096000000L; // 2024-01-01 08:00:00 UTC
        String fileName = (String) method.invoke(producer, "testfile", ".txt", timestampWindow);
        assertEquals("testfile_20240101_0800_0800-0805.txt", fileName);

        // Test without extension
        fileName = (String) method.invoke(producer, "testfile", null, timestampWindow);
        assertEquals("testfile_20240101_0800_0800-0805", fileName);

        // Test with empty extension
        fileName = (String) method.invoke(producer, "testfile", "", timestampWindow);
        assertEquals("testfile_20240101_0800_0800-0805", fileName);

        // Test different time window
        long timestampWindow2 = 1704096300000L; // 2024-01-01 08:05:00 UTC
        fileName = (String) method.invoke(producer, "myfile", ".log", timestampWindow2);
        assertEquals("myfile_20240101_0805_0805-0810.log", fileName);
    }

    @Test
    public void testExtractTimestampFromExchangeWithLong() throws Exception {
        when(configuration.getTimestampHeaderName()).thenReturn(Exchange.MESSAGE_TIMESTAMP);

        Method method =
                AWS2S3StreamUploadProducer.class.getDeclaredMethod("extractTimestampFromExchange", Exchange.class);
        method.setAccessible(true);

        Exchange exchange = new DefaultExchange(camelContext);

        // Test with Long timestamp
        long expectedTimestamp = 1704096000000L;
        exchange.getIn().setHeader(Exchange.MESSAGE_TIMESTAMP, expectedTimestamp);

        Long result = (Long) method.invoke(producer, exchange);
        assertNotNull(result);
        assertEquals(expectedTimestamp, result.longValue());
    }

    @Test
    public void testExtractTimestampFromExchangeWithDate() throws Exception {
        when(configuration.getTimestampHeaderName()).thenReturn(Exchange.MESSAGE_TIMESTAMP);

        Method method =
                AWS2S3StreamUploadProducer.class.getDeclaredMethod("extractTimestampFromExchange", Exchange.class);
        method.setAccessible(true);

        Exchange exchange = new DefaultExchange(camelContext);

        // Test with Date timestamp
        long expectedTimestamp = 1704096000000L;
        Date date = new Date(expectedTimestamp);
        exchange.getIn().setHeader(Exchange.MESSAGE_TIMESTAMP, date);

        Long result = (Long) method.invoke(producer, exchange);
        assertNotNull(result);
        assertEquals(expectedTimestamp, result.longValue());
    }

    @Test
    public void testExtractTimestampFromExchangeWithString() throws Exception {
        when(configuration.getTimestampHeaderName()).thenReturn(Exchange.MESSAGE_TIMESTAMP);

        Method method =
                AWS2S3StreamUploadProducer.class.getDeclaredMethod("extractTimestampFromExchange", Exchange.class);
        method.setAccessible(true);

        Exchange exchange = new DefaultExchange(camelContext);

        // Test with String timestamp
        long expectedTimestamp = 1704096000000L;
        exchange.getIn().setHeader("CamelMessageTimestamp", String.valueOf(expectedTimestamp));

        Long result = (Long) method.invoke(producer, exchange);
        assertNotNull(result);
        assertEquals(expectedTimestamp, result.longValue());
    }

    @Test
    public void testExtractTimestampFromExchangeWithInvalidString() throws Exception {
        when(configuration.getTimestampHeaderName()).thenReturn(Exchange.MESSAGE_TIMESTAMP);

        Method method =
                AWS2S3StreamUploadProducer.class.getDeclaredMethod("extractTimestampFromExchange", Exchange.class);
        method.setAccessible(true);

        Exchange exchange = new DefaultExchange(camelContext);

        // Test with invalid string timestamp
        exchange.getIn().setHeader("CamelMessageTimestamp", "not-a-timestamp");

        Long result = (Long) method.invoke(producer, exchange);
        assertNull(result, "Should return null for invalid timestamp string");
    }

    @Test
    public void testExtractTimestampFromExchangeWithMissingHeader() throws Exception {
        when(configuration.getTimestampHeaderName()).thenReturn(Exchange.MESSAGE_TIMESTAMP);

        Method method =
                AWS2S3StreamUploadProducer.class.getDeclaredMethod("extractTimestampFromExchange", Exchange.class);
        method.setAccessible(true);

        Exchange exchange = new DefaultExchange(camelContext);
        // No timestamp header set

        Long result = (Long) method.invoke(producer, exchange);
        assertNull(result, "Should return null when timestamp header is missing");
    }

    @Test
    public void testExtractTimestampFromExchangeWithNullHeader() throws Exception {
        when(configuration.getTimestampHeaderName()).thenReturn(Exchange.MESSAGE_TIMESTAMP);

        Method method =
                AWS2S3StreamUploadProducer.class.getDeclaredMethod("extractTimestampFromExchange", Exchange.class);
        method.setAccessible(true);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader("CamelMessageTimestamp", null);

        Long result = (Long) method.invoke(producer, exchange);
        assertNull(result, "Should return null when timestamp header is null");
    }

    @Test
    public void testExtractTimestampFromExchangeWithCustomHeaderName() throws Exception {
        when(configuration.getTimestampHeaderName()).thenReturn("MyCustomTimestamp");

        Method method =
                AWS2S3StreamUploadProducer.class.getDeclaredMethod("extractTimestampFromExchange", Exchange.class);
        method.setAccessible(true);

        Exchange exchange = new DefaultExchange(camelContext);

        // Test with custom header name
        long expectedTimestamp = 1704096000000L;
        exchange.getIn().setHeader("MyCustomTimestamp", expectedTimestamp);

        Long result = (Long) method.invoke(producer, exchange);
        assertNotNull(result);
        assertEquals(expectedTimestamp, result.longValue());
    }

    @Test
    public void testTimestampWindowCalculationWithDifferentWindowSizes() throws Exception {
        Method method = AWS2S3StreamUploadProducer.class.getDeclaredMethod("getTimestampWindow", long.class);
        method.setAccessible(true);

        long baseTimestamp = 1704096150000L; // 2024-01-01 08:02:30 UTC

        // Test 1-minute windows
        when(configuration.getTimestampWindowSizeMillis()).thenReturn(60000L);
        long window1min = (Long) method.invoke(producer, baseTimestamp);
        assertEquals(1704096120000L, window1min); // Should round to 08:02:00

        // Test 1-hour windows
        when(configuration.getTimestampWindowSizeMillis()).thenReturn(3600000L);
        long window1hour = (Long) method.invoke(producer, baseTimestamp);
        assertEquals(1704096000000L, window1hour); // Should round to 08:00:00

        // Test 1-second windows
        when(configuration.getTimestampWindowSizeMillis()).thenReturn(1000L);
        long window1sec = (Long) method.invoke(producer, baseTimestamp);
        assertEquals(1704096150000L, window1sec); // Should round to 08:02:30
    }

    @Test
    public void testGenerateTimestampBasedFileNameWithDifferentWindowSizes() throws Exception {
        Method method = AWS2S3StreamUploadProducer.class.getDeclaredMethod(
                "generateTimestampBasedFileName", String.class, String.class, long.class);
        method.setAccessible(true);

        long timestampWindow = 1704096000000L; // 2024-01-01 08:00:00 UTC

        // Test 1-minute window
        when(configuration.getTimestampWindowSizeMillis()).thenReturn(60000L);
        String fileName1min = (String) method.invoke(producer, "test", ".txt", timestampWindow);
        assertEquals("test_20240101_0800_0800-0801.txt", fileName1min);

        // Test 1-hour window
        when(configuration.getTimestampWindowSizeMillis()).thenReturn(3600000L);
        String fileName1hour = (String) method.invoke(producer, "test", ".txt", timestampWindow);
        assertEquals("test_20240101_0800_0800-0900.txt", fileName1hour);

        // Test 30-minute window
        when(configuration.getTimestampWindowSizeMillis()).thenReturn(1800000L);
        String fileName30min = (String) method.invoke(producer, "test", ".txt", timestampWindow);
        assertEquals("test_20240101_0800_0800-0830.txt", fileName30min);
    }
}
