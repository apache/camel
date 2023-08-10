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
package org.apache.camel.component.undertow;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xnio.XnioIoThread;
import org.xnio.channels.EmptyStreamSourceChannel;
import org.xnio.channels.StreamSourceChannel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class DefaultUndertowHttpBindingTest {

    @Timeout(10)
    @Test
    public void readEntireDelayedPayload() throws Exception {
        String[] delayedPayloads = new String[] {
                "",
                "chunk",
        };

        StreamSourceChannel source = source(delayedPayloads);

        DefaultUndertowHttpBinding binding = new DefaultUndertowHttpBinding();
        String result = new String(binding.readFromChannel(source));

        checkResult(result, delayedPayloads);
    }

    @Timeout(10)
    @Test
    public void readEntireMultiDelayedPayload() throws Exception {
        String[] delayedPayloads = new String[] {
                "",
                "first ",
                "second",
        };

        StreamSourceChannel source = source(delayedPayloads);

        DefaultUndertowHttpBinding binding = new DefaultUndertowHttpBinding();
        String result = new String(binding.readFromChannel(source));

        checkResult(result, delayedPayloads);
    }

    private void checkResult(String result, String[] delayedPayloads) {
        assertEquals(Stream.of(delayedPayloads).collect(Collectors.joining()), result);
    }

    @Timeout(10)
    @Test
    public void readEntireMultiDelayedWithPausePayload() throws Exception {
        String[] delayedPayloads = new String[] {
                "",
                "first ",
                "",
                "second",
        };

        StreamSourceChannel source = source(delayedPayloads);

        DefaultUndertowHttpBinding binding = new DefaultUndertowHttpBinding();
        String result = new String(binding.readFromChannel(source));

        checkResult(result, delayedPayloads);
    }

    private StreamSourceChannel source(final String[] delayedPayloads) {
        Thread sourceThread = Thread.currentThread();

        return new EmptyStreamSourceChannel(thread()) {
            int chunk;
            boolean mustWait;  // make sure that the caller is not spinning on read==0

            @Override
            public int read(ByteBuffer dst) {
                if (mustWait) {
                    fail("must wait before reading");
                }
                if (chunk < delayedPayloads.length) {
                    byte[] delayedPayload = delayedPayloads[chunk].getBytes();
                    dst.put(delayedPayload);
                    chunk++;
                    if (delayedPayload.length == 0) {
                        mustWait = true;
                    }
                    return delayedPayload.length;
                }
                return -1;
            }

            @Override
            public void resumeReads() {
                /**
                 * {@link io.undertow.server.HttpServerExchange.ReadDispatchChannel} delays resumes in the main thread
                 */
                if (sourceThread != Thread.currentThread()) {
                    super.resumeReads();
                }
            }

            @Override
            public void awaitReadable() throws IOException {
                mustWait = false;
                super.awaitReadable();
            }
        };
    }

    private XnioIoThread thread() {
        ExecutorService executor = Executors.newFixedThreadPool(1);
        return new XnioIoThread(null, 0) {
            @Override
            public void execute(Runnable runnable) {
                executor.execute(runnable);
            }

            @Override
            public Key executeAfter(Runnable runnable, long l, TimeUnit timeUnit) {
                execute(runnable);
                return null;
            }

            @Override
            public Key executeAtInterval(Runnable runnable, long l, TimeUnit timeUnit) {
                execute(runnable);
                return null;
            }
        };
    }
}
