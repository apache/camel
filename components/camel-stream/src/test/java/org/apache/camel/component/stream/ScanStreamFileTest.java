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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for scan stream file
 */
public class ScanStreamFileTest extends CamelTestSupport {

    private File file;

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/stream");
        createDirectory("target/stream");

        file = new File("target/stream/scanstreamfile.txt");
        file.createNewFile();

        super.setUp();
    }

    @Test
    public void testScanFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);
        mock.message(0).header(StreamConstants.STREAM_INDEX).isEqualTo(0);
        mock.message(0).header(StreamConstants.STREAM_COMPLETE).isEqualTo(false);
        mock.message(1).header(StreamConstants.STREAM_INDEX).isEqualTo(1);
        // a scanStream=true is never finished
        mock.message(1).header(StreamConstants.STREAM_COMPLETE).isEqualTo(false);

        context.getRouteController().startAllRoutes();

        FileOutputStream fos = new FileOutputStream(file);
        try {
            fos.write("Hello\n".getBytes());
            Thread.sleep(150);
            fos.write("World\n".getBytes());
            // ensure it does not read the file again
            Thread.sleep(1000);
        } finally {
            fos.close();
        }
        
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testScanRefreshedFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(5);

        // write file during started route

        FileOutputStream fos = refreshFile(null);
        try {
            fos.write("Hello\nWorld\n".getBytes());
            Thread.sleep(150);

            context.getRouteController().startAllRoutes();

            // roll-over file
            Thread.sleep(1500);
            fos = refreshFile(fos);
            fos.write("Bye\nWorld\n".getBytes());
            fos.write("!\n".getBytes());
            // ensure it does not read the file again
            Thread.sleep(1500);
        } finally {
            fos.close();
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testScanFileAlreadyWritten() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(4);

        FileOutputStream fos = refreshFile(null);
        try {
            fos.write("Hello\nthere\nWorld\n!\n".getBytes());
            context.getRouteController().startAllRoutes();
            // ensure it does not read the file again
            Thread.sleep(1000);
        } finally {
            fos.close();
        }

        assertMockEndpointsSatisfied();
    }

    private FileOutputStream refreshFile(FileOutputStream fos) throws Exception {
        if (fos != null) {
            fos.close();
        }
        file.delete();
        file.createNewFile();
        return new FileOutputStream(file);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("stream:file?fileName=target/stream/scanstreamfile.txt&scanStream=true&scanStreamDelay=200&retry=true&fileWatcher=true")
                    .routeId("foo").noAutoStartup()
                    .to("log:line")
                    .to("mock:result");
            }
        };
    }

}
