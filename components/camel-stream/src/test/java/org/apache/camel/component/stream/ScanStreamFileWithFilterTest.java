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
public class ScanStreamFileWithFilterTest extends CamelTestSupport {

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
        mock.expectedBodiesReceived("Hello Boy");

        FileOutputStream fos = new FileOutputStream(file);
        fos.write("Hello\n".getBytes());
        Thread.sleep(150);
        fos.write("World\n".getBytes());
        Thread.sleep(150);
        fos.write("Hello\n".getBytes());
        Thread.sleep(150);
        fos.write("World\n".getBytes());
        Thread.sleep(150);
        fos.write("Hello\n".getBytes());
        Thread.sleep(150);
        fos.write("World\n".getBytes());
        Thread.sleep(150);
        fos.write("Hello Boy\n".getBytes());
        Thread.sleep(150);
        fos.write("World\n".getBytes());

        assertMockEndpointsSatisfied();

        fos.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("stream:file?fileName=target/stream/scanstreamfile.txt&scanStream=true&scanStreamDelay=100")
                    .filter(body().contains("Hello Boy"))
                    .to("mock:result");
            }
        };
    }

}
