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
package org.apache.camel.support;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.converter.stream.FileInputStreamCache;
import org.apache.camel.impl.engine.DefaultUnitOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class PayloadHelperTest extends ContextTestSupport {

    private static final int PAYLOAD_SIZE = 256 * 1024;

    private byte[] payload;
    private Path file;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setStreamCaching(true);
        context.getStreamCachingStrategy().setSpoolDirectory(testDirectory("spool").toFile());
        context.getStreamCachingStrategy().setSpoolEnabled(true);
        return context;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        payload = new byte[PAYLOAD_SIZE];
        new Random(42).nextBytes(payload);
        file = Files.write(testFile("payload.bin"), payload);
    }

    @Test
    public void testLengthIsDeterminedWithoutReading() throws Exception {
        assertEquals(PAYLOAD_SIZE, PayloadHelper.getLength(file));
        assertEquals(PAYLOAD_SIZE, PayloadHelper.getLength(file.toFile()));
        assertEquals(PAYLOAD_SIZE, PayloadHelper.getLength(payload));
        assertEquals(PAYLOAD_SIZE, PayloadHelper.getLength(new ByteArrayInputStream(payload)));
        try (FileInputStream fis = new FileInputStream(file.toFile())) {
            // only the remaining bytes are counted
            fis.skip(1024);
            assertEquals(PAYLOAD_SIZE - 1024, PayloadHelper.getLength(fis));
        }

        // the length of a buffered stream is unknown, and determining it must not read the stream
        try (InputStream is = new BufferedInputStream(Files.newInputStream(file))) {
            assertEquals(-1, PayloadHelper.getLength(is));
            assertArrayEquals(payload, is.readAllBytes());
        }
    }

    @Test
    public void testBodyLengthOfWrappedFile() {
        Exchange exchange = context.getEndpoint("mock:result").createExchange();

        // a remote file whose content is not stored in a local file, such as from SFTP
        GenericFile<Object> remote = new GenericFile<>();
        remote.setFile(new Object());
        remote.setFileLength(PAYLOAD_SIZE);
        exchange.getIn().setBody(remote);
        assertEquals(PAYLOAD_SIZE, PayloadHelper.getBodyLength(exchange.getIn()));

        // a local file without a known file length
        GenericFile<Object> local = new GenericFile<>();
        local.setFile(file.toFile());
        exchange.getIn().setBody(local);
        assertEquals(PAYLOAD_SIZE, PayloadHelper.getBodyLength(exchange.getIn()));
    }

    @Test
    public void testCacheStreamSpoolsBigPayloadToDisk() throws Exception {
        context.start();
        Exchange exchange = context.getEndpoint("mock:result").createExchange();
        exchange.getExchangeExtension().setUnitOfWork(new DefaultUnitOfWork(exchange));

        // a stream of unknown length
        InputStream stream = new BufferedInputStream(new ByteArrayInputStream(payload));
        InputStream cached = PayloadHelper.cacheStream(exchange, stream);

        // the payload is above the spool threshold, so it is kept on disk and not in memory
        assertInstanceOf(FileInputStreamCache.class, cached);
        assertEquals(PAYLOAD_SIZE, ((StreamCache) cached).length());
        assertEquals(PAYLOAD_SIZE, PayloadHelper.getLength(cached));
        assertArrayEquals(payload, cached.readAllBytes());
    }
}
