/**
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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for stream file
 */
public class StreamFileTest extends ContextTestSupport {

    private File file;
    private FileOutputStream fos;

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("./target/stream");
        createDirectory("./target/stream");

        file = new File("./target/stream/streamfile.txt");
        file = file.getAbsoluteFile();
        file.createNewFile();

        fos = new FileOutputStream(file);
        fos.write("Hello\n".getBytes());

        super.setUp();
    }

    public void testFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello");

        assertMockEndpointsSatisfied();

        fos.close();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("stream:file?fileName=./target/stream/streamfile.txt").to("mock:result");
            }
        };
    }

}