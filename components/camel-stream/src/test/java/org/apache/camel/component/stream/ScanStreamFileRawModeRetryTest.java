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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stream consumer with scanStream=true, retry=true and readLine=false on a file that does not exist when it starts
 * must keep scanning, and route the file once it exists.
 */
public class ScanStreamFileRawModeRetryTest extends CamelTestSupport {

    private File file;

    @Override
    public void doPreSetup() throws Exception {
        deleteDirectory("target/stream-raw-retry");
        createDirectory("target/stream-raw-retry");

        file = new File("target/stream-raw-retry/scanstreamraw.txt");
    }

    @Test
    public void testScanMissingFileRawMode() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // retry without fileWatcher reads the file again from the start at the end of the stream
        mock.expectedMinimumMessageCount(1);
        mock.message(0).body().isEqualTo("Hello\nWorld\n");

        context.getRouteController().startAllRoutes();

        // the file is created after the consumer started
        File tmp = new File("target/stream-raw-retry/scanstreamraw.tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write("Hello\nWorld\n".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(tmp.renameTo(file), "rename of " + tmp + " to " + file);

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("stream:file?fileName=target/stream-raw-retry/scanstreamraw.txt&scanStream=true&scanStreamDelay=100&retry=true&readLine=false")
                        .routeId("raw").autoStartup(false)
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }

}
