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
package org.apache.camel.component.file.remote.integration;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ThreadPoolProfile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Tag("not-parallel")
class FromFileToFtpSplitParallelIT extends FtpServerTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(FromFileToFtpSplitParallelIT.class);

    private static final int SIZE = 5_000;
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();

    @TempDir
    Path testDirectory;

    protected String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/tmp2/big?password=admin";
    }

    @Test
    void testSplit() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.setResultWaitTime(TimeUnit.MINUTES.toMillis(5));

        assertDoesNotThrow(() -> context.getRouteController().startAllRoutes(),
                "The split parallel route should start without exceptions");

        mock.assertIsSatisfied();
    }

    @BeforeEach
    public void createBigFile() throws FileNotFoundException {
        Assumptions.assumeTrue(AVAILABLE_PROCESSORS > 1,
                "Skipping test because this system may not have enough resources to run it");

        // create big file
        try (PrintWriter writer = new PrintWriter(
                new FileOutputStream(testDirectory.toString() + "/bigdata.txt"), true, StandardCharsets.UTF_8)) {
            for (int i = 0; i < SIZE; i++) {
                writer.printf("ABCDEFGHIJKLMNOPQRSTUVWXYZ%d%n", i);
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                final int poolSize = AVAILABLE_PROCESSORS / 2;

                LOG.info(
                        "Setting up the max pool size to the number of available processors: {}. Pool size will be set to half of that: {}",
                        AVAILABLE_PROCESSORS, poolSize);

                ThreadPoolProfile tpp
                        = new ThreadPoolProfileBuilder("ftp-pool")
                                .poolSize(poolSize)
                                .maxPoolSize(AVAILABLE_PROCESSORS)
                                .maxQueueSize(1_000)
                                .build();
                context.getExecutorServiceManager().registerThreadPoolProfile(tpp);

                onException().maximumRedeliveries(5).redeliveryDelay(1_000);

                fromF("file:%s", testDirectory).noAutoStartup().routeId("foo")
                    .split(body().tokenize("\n")).executorService("ftp-pool")
                        .to(getFtpUrl())
                        .to("log:line?groupSize=100")
                    .end()
                    .log("End of splitting")
                    .to("mock:result");
            }
        };
    }
}
