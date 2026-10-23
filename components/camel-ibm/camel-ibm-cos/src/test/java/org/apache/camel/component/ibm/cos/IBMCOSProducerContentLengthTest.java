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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.model.PutObjectRequest;
import com.ibm.cloud.objectstorage.services.s3.model.PutObjectResult;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileBinding;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.converter.stream.ByteArrayInputStreamCache;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The content length of putObject is the length of the body when it is known, so a CamelIBMCOSContentLength header left
 * on the message by the consumer or getObject (the length of the object read) does not apply to a body the route
 * changed.
 */
class IBMCOSProducerContentLengthTest extends CamelTestSupport {

    // the length of the object the consumer or getObject read before the route changed the body
    private static final long STALE_LENGTH = 11L;
    private static final String NEW_BODY = "a body the route changed after reading the object";

    @BindToRegistry("cos")
    private final AmazonS3 cosClient = mock();

    @TempDir
    Path dir;

    @Override
    protected void doPreSetup() throws Exception {
        when(cosClient.doesBucketExistV2(anyString())).thenReturn(true);
        when(cosClient.putObject(any(PutObjectRequest.class))).thenReturn(new PutObjectResult());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // keep a stream body as it is sent, so the producer sees a stream of unknown length
        context.setStreamCaching(false);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:put").to("ibm-cos://bkt?cosClient=#cos&operation=putObject");
            }
        };
    }

    @Test
    void theLengthOfAStringBodyWinsOverTheHeader() {
        assertEquals(NEW_BODY.getBytes(StandardCharsets.UTF_8).length, put(NEW_BODY, STALE_LENGTH));
    }

    @Test
    void theLengthOfABytesBodyWinsOverTheHeader() {
        assertEquals(2L, put(new byte[] { 1, 2 }, STALE_LENGTH));
    }

    @Test
    void theLengthOfAStreamCacheWinsOverTheHeader() {
        byte[] data = NEW_BODY.getBytes(StandardCharsets.UTF_8);
        assertEquals(data.length, put(new ByteArrayInputStreamCache(new ByteArrayInputStream(data)), STALE_LENGTH));
    }

    @Test
    void theLengthOfAFileWinsOverTheHeader() throws Exception {
        File file = Files.writeString(dir.resolve("changed.txt"), NEW_BODY).toFile();
        assertEquals(file.length(), put(file, STALE_LENGTH));
    }

    @Test
    void theLengthOfTheFileOfTheFileConsumerWinsOverTheHeader() throws Exception {
        File file = Files.writeString(dir.resolve("changed.txt"), NEW_BODY).toFile();
        GenericFile<File> genericFile = new GenericFile<>();
        genericFile.setFile(file);
        genericFile.setFileName(file.getName());
        genericFile.setAbsoluteFilePath(file.getAbsolutePath());
        genericFile.setFileLength(file.length());
        genericFile.setBody(file);
        genericFile.setBinding(new FileBinding());

        assertEquals(file.length(), put(genericFile, STALE_LENGTH));
    }

    @Test
    void theHeaderIsUsedForAStreamOfUnknownLength() {
        byte[] data = NEW_BODY.getBytes(StandardCharsets.UTF_8);
        // a stream that does not tell its length: the header is the only way to know it without reading the stream
        InputStream stream = new BufferedInputStream(new ByteArrayInputStream(data));
        PutObjectRequest request = putRequest(stream, (long) data.length);
        assertEquals(data.length, request.getMetadata().getContentLength());
        assertSame(stream, request.getInputStream(), "the stream was copied to compute a length the header gives");
    }

    private long put(Object body, Long contentLengthHeader) {
        return putRequest(body, contentLengthHeader).getMetadata().getContentLength();
    }

    private PutObjectRequest putRequest(Object body, Long contentLengthHeader) {
        template.sendBodyAndHeaders("direct:put", body,
                Map.of(IBMCOSConstants.KEY, "copy.txt", IBMCOSConstants.CONTENT_LENGTH, contentLengthHeader));

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(cosClient).putObject(request.capture());
        return request.getValue();
    }
}
