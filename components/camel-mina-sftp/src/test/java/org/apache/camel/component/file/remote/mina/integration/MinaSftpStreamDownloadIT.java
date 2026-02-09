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
package org.apache.camel.component.file.remote.mina.integration;

import java.io.InputStream;
import java.nio.file.Files;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.remote.FtpConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for stream download support in MINA SFTP component.
 * <p>
 * These tests verify that:
 * <ul>
 * <li>When streamDownload=true, the GenericFile body is InputStream and header is set</li>
 * <li>When streamDownload=false (default), the GenericFile body is byte array</li>
 * <li>Behavior matches camel-ftp component for API compatibility</li>
 * </ul>
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class MinaSftpStreamDownloadIT extends MinaSftpServerTestSupport {

    private static final String TEST_CONTENT = "Hello Stream Download Test";

    @BeforeEach
    public void createTestFile() throws Exception {
        // Ensure the root directory exists and create the test file
        service.getFtpRootDir().toFile().mkdirs();
        Files.writeString(ftpFile("streamtest.txt"), TEST_CONTENT);
    }

    /**
     * Test that streamDownload=true allows body conversion to InputStream and sets the header. This implements T009
     * from the tasks.
     */
    @Test
    public void testStreamDownloadReturnsInputStream() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:streaming");
        mock.expectedMessageCount(1);

        context.getRouteController().startRoute("streamingRoute");

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = mock.getExchanges().get(0);

        // The body can be converted to InputStream when streamDownload=true
        InputStream is = exchange.getIn().getBody(InputStream.class);
        assertNotNull(is, "Body should be convertible to InputStream when streamDownload=true");

        // Header should be set to an InputStream
        Object header = exchange.getIn().getHeader(FtpConstants.REMOTE_FILE_INPUT_STREAM);
        assertNotNull(header, "REMOTE_FILE_INPUT_STREAM header should be set");
        assertInstanceOf(InputStream.class, header, "Header should be an InputStream");
    }

    /**
     * Test that streamDownload=false allows body conversion to byte array. This implements T013 from the tasks.
     */
    @Test
    public void testNonStreamDownloadReturnsByteArray() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:nonstreaming");
        mock.expectedMessageCount(1);

        context.getRouteController().startRoute("nonStreamingRoute");

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = mock.getExchanges().get(0);

        // The body can be converted to byte[] when streamDownload=false
        byte[] content = exchange.getIn().getBody(byte[].class);
        assertNotNull(content, "Body should be convertible to byte[] when streamDownload=false");
        assertTrue(content.length > 0, "Content should not be empty");
    }

    /**
     * Test that default configuration (no streamDownload param) returns byte array. This implements T014 from the
     * tasks.
     */
    @Test
    public void testDefaultDownloadReturnsByteArray() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:default");
        mock.expectedMessageCount(1);

        context.getRouteController().startRoute("defaultRoute");

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = mock.getExchanges().get(0);

        // Default behavior should allow byte[] conversion (same as streamDownload=false)
        byte[] content = exchange.getIn().getBody(byte[].class);
        assertNotNull(content, "Default body should be convertible to byte[]");
        assertTrue(content.length > 0, "Content should not be empty");
    }

    /**
     * Test that streaming mode sets header to an InputStream (camel-ftp compatibility). This implements T017 from the
     * tasks.
     */
    @Test
    public void testStreamDownloadHeaderIsSet() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:headercheck");
        mock.expectedMessageCount(1);

        context.getRouteController().startRoute("headerCheckRoute");

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = mock.getExchanges().get(0);
        Object header = exchange.getIn().getHeader(FtpConstants.REMOTE_FILE_INPUT_STREAM);

        // Header should be set and be an InputStream
        assertNotNull(header, "Header should be set in streaming mode");
        assertInstanceOf(InputStream.class, header, "Header should be an InputStream");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String baseUri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                                 + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false"
                                 + "&delay=10000&disconnect=true&noop=true&fileName=streamtest.txt";

                // Route for testing streamDownload=true
                from(baseUri + "&streamDownload=true&stepwise=false")
                        .routeId("streamingRoute").autoStartup(false)
                        .to("mock:streaming");

                // Route for testing streamDownload=false
                from(baseUri + "&streamDownload=false")
                        .routeId("nonStreamingRoute").autoStartup(false)
                        .to("mock:nonstreaming");

                // Route for testing default behavior (no streamDownload param)
                from(baseUri)
                        .routeId("defaultRoute").autoStartup(false)
                        .to("mock:default");

                // Route for testing header is set correctly in streaming mode
                from(baseUri + "&streamDownload=true&stepwise=false")
                        .routeId("headerCheckRoute").autoStartup(false)
                        .to("mock:headercheck");
            }
        };
    }
}
