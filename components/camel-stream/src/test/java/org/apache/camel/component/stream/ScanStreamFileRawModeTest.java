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
package org.apache.camel.component.stream;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.createDirectory;
import static org.apache.camel.test.junit6.TestSupport.deleteDirectory;
import static org.awaitility.Awaitility.await;

/**
 * A stream consumer with scanStream=true and readLine=false must route the data it reads, both the data that is in the
 * file when it starts and the data appended later.
 */
public class ScanStreamFileRawModeTest extends CamelTestSupport {

    private File file;

    @Override
    public void doPreSetup() throws Exception {
        deleteDirectory("target/stream-raw");
        createDirectory("target/stream-raw");

        file = new File("target/stream-raw/scanstreamraw.txt");
        file.createNewFile();
    }

    @Test
    public void testScanFileRawMode() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello\nWorld\n", "Bye\n");
        mock.message(0).header(StreamConstants.STREAM_INDEX).isEqualTo(0);
        mock.message(1).header(StreamConstants.STREAM_INDEX).isEqualTo(1);
        // a scanStream=true is never finished
        mock.message(0).header(StreamConstants.STREAM_COMPLETE).isEqualTo(false);
        mock.message(1).header(StreamConstants.STREAM_COMPLETE).isEqualTo(false);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write("Hello\nWorld\n".getBytes(StandardCharsets.UTF_8));
            fos.flush();

            context.getRouteController().startAllRoutes();

            // the data in the file when the consumer starts
            await().atMost(10, TimeUnit.SECONDS).until(() -> mock.getReceivedCounter() >= 1);

            // data appended to the file is picked up by a later scan
            fos.write("Bye\n".getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("stream:file?fileName=target/stream-raw/scanstreamraw.txt&scanStream=true&scanStreamDelay=100&readLine=false")
                        .routeId("raw").autoStartup(false)
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }

}
