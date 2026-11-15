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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * camel debug reads the output of the integration it spawned with {@link OutputTail}: all the time, so the integration
 * does not block on a full pipe, and only until the end of the stream (CAMEL-27004: it read the ended stream again in a
 * loop, at 100% CPU).
 */
class OutputTailTest {

    @Test
    void keepsTheLastLines() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 250; i++) {
            sb.append("line ").append(i).append('\n');
        }
        AtomicInteger read = new AtomicInteger();
        OutputTail tail = new OutputTail(100, read::incrementAndGet)
                .start(new ByteArrayInputStream(sb.toString().getBytes(Charset.defaultCharset())), "test");

        assertThat(tail.awaitEnd(10_000)).isTrue();
        assertThat(read).hasValue(250);
        assertThat(tail.lines()).hasSize(100).startsWith("line 151").endsWith("line 250");
    }

    @Test
    void endsAtTheEndOfTheStream() throws Exception {
        PipedOutputStream out = new PipedOutputStream();
        OutputTail tail = new OutputTail(10, null).start(new PipedInputStream(out), "test");

        out.write("started\n".getBytes(Charset.defaultCharset()));
        out.flush();
        // still open: the thread waits for more
        assertThat(tail.awaitEnd(200)).isFalse();

        out.close();
        // the end of the stream: the thread ends, instead of reading the stream again
        assertThat(tail.awaitEnd(10_000)).isTrue();
        assertThat(tail.lines()).containsExactly("started");
    }

    @Test
    void noStartedThread() throws Exception {
        OutputTail tail = new OutputTail(10, null);
        assertThat(tail.awaitEnd(10)).isTrue();
        assertThat(tail.lines()).isEmpty();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void processWritingMoreThanAPipeHoldsDoesNotBlock() throws IOException, InterruptedException {
        // about 1 MB on stderr and on stdout, many times what the pipe of the OS holds (64 KB)
        Process p = new ProcessBuilder(
                "sh", "-c",
                "i=0; while [ $i -lt 20000 ]; do echo \"error line $i of the integration\" >&2;"
                            + " echo \"log line $i of the integration\"; i=$((i+1)); done")
                .start();
        OutputTail log = new OutputTail(100, null).start(p.getInputStream(), "test-log");
        OutputTail error = new OutputTail(1000, null).start(p.getErrorStream(), "test-error");

        assertThat(p.waitFor(60, TimeUnit.SECONDS)).as("the process ends, as both of its pipes are read").isTrue();
        assertThat(log.awaitEnd(10_000)).isTrue();
        assertThat(error.awaitEnd(10_000)).isTrue();
        assertThat(log.lines()).hasSize(100).endsWith("log line 19999 of the integration");
        assertThat(error.lines()).hasSize(1000).endsWith("error line 19999 of the integration");
    }
}
