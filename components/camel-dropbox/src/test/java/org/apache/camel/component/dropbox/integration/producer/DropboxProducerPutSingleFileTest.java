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
package org.apache.camel.component.dropbox.integration.producer;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.dropbox.integration.DropboxTestSupport;
import org.apache.camel.component.dropbox.util.DropboxConstants;
import org.apache.camel.component.dropbox.util.DropboxException;
import org.apache.camel.component.dropbox.util.DropboxResultHeader;
import org.apache.camel.component.dropbox.util.DropboxUploadMode;
import org.apache.camel.component.mock.MockEndpoint;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DropboxProducerPutSingleFileTest extends DropboxTestSupport {
    public static final String FILENAME = "newFile.txt";

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testCamelDropboxWithOptionInHeader() throws Exception {
        final Path file = Files.createTempFile("camel", ".txt");
        final Map<String, Object> headers = new HashMap<>();
        headers.put(DropboxConstants.HEADER_LOCAL_PATH, file.toAbsolutePath().toString());
        headers.put(DropboxConstants.HEADER_UPLOAD_MODE, DropboxUploadMode.add);
        template.sendBodyAndHeaders("direct:start", null, headers);

        assertFileUploaded();
    }

    @Test
    public void uploadBodyTest() throws Exception {
        template.sendBodyAndHeader("direct:start", "Helo Camels", DropboxConstants.HEADER_UPLOAD_MODE, DropboxUploadMode.add);

        assertFileUploaded();
    }

    @Test
    public void uploadIfExistsAddTest() throws Exception {
        thrown.expectCause(IsInstanceOf.instanceOf(DropboxException.class));
        createFile(FILENAME, "content");
        final Path file = Files.createTempFile("camel", ".txt");
        final Map<String, Object> headers = new HashMap<>();
        headers.put(DropboxConstants.HEADER_LOCAL_PATH, file.toAbsolutePath().toString());
        headers.put(DropboxConstants.HEADER_UPLOAD_MODE, DropboxUploadMode.add);
        template.sendBodyAndHeaders("direct:start", null, headers);
    }

    @Test
    public void uploadIfExistsForceTest() throws Exception {
        final String newContent = UUID.randomUUID().toString();
        createFile(FILENAME, "Hi camels");
        final Path file = Files.createTempFile("camel", ".txt");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file.toFile()))) {
            bw.write(newContent);
            bw.flush();
        }
        final Map<String, Object> headers = new HashMap<>();
        headers.put(DropboxConstants.HEADER_LOCAL_PATH, file.toAbsolutePath().toString());
        headers.put(DropboxConstants.HEADER_UPLOAD_MODE, DropboxUploadMode.force);
        template.sendBodyAndHeaders("direct:start", null, headers);

        assertFileUploaded();

        Assert.assertEquals(newContent, getFileContent(workdir + "/" + FILENAME));
    }

    private void assertFileUploaded() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(DropboxResultHeader.UPLOADED_FILE.name(), workdir + "/" + FILENAME);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("dropbox://put?accessToken={{accessToken}}&remotePath=" + workdir + "/" + FILENAME)
                    .to("mock:result");
            }
        };
    }
}
