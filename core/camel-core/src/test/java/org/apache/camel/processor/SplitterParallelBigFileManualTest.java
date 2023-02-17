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
package org.apache.camel.processor;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Manual test")
public class SplitterParallelBigFileManualTest extends ContextTestSupport {

    private int lines = 20000;

    @Test
    public void testSplitParallelBigFile() throws Exception {
        Path dir = testDirectory();
        Files.createDirectories(dir);
        try (OutputStream fos = Files.newOutputStream(testFile("bigfile.txt"))) {
            for (int i = 0; i < lines; i++) {
                String line = "line-" + i + LS;
                fos.write(line.getBytes());
            }
        }

        StopWatch watch = new StopWatch();

        NotifyBuilder builder = new NotifyBuilder(context).whenDone(lines + 1).create();
        boolean done = builder.matches(120, TimeUnit.SECONDS);

        log.info("Took {}", TimeUtils.printDuration(watch.taken(), true));

        if (!done) {
            throw new CamelException("Could not split file in 2 minutes");
        }

        // need a little sleep for capturing memory profiling
        Thread.sleep(60 * 1000);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // lower max pool to 10 for less number of concurrent threads
                // context.getExecutorServiceStrategy().getDefaultThreadPoolProfile().setMaxPoolSize(10);

                from(fileUri("?initialDelay=0&delay=10")).split(body().tokenize(LS)).streaming()
                        .parallelProcessing().to("log:split?groupSize=1000").end()
                        .log("Done splitting ${file:name}");
            }
        };
    }

}
