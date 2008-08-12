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
package org.apache.camel.component.file;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.IOConverter;

/**
 * Unit test to verify the append option
 */
public class FileProduceAppendTest extends ContextTestSupport {

    public void testAppendText() throws Exception {
        template.sendBody("direct:start", " World");

        // give time to write to file
        Thread.sleep(1000);

        String body = IOConverter.toString(new File("target/test-file-append/hello.txt").getAbsoluteFile());
        assertEquals("Hello World", body);
    }

    public void testAppendFile() throws Exception {
        // create a file with some content we want to append to the existing file
        File in = new File("target/test-file-append/world.txt").getAbsoluteFile();
        template.sendBody("direct:start", in);

        // give time to write to file
        Thread.sleep(1000);

        String body = IOConverter.toString(new File("target/test-file-append/hello.txt").getAbsoluteFile());
        assertEquals("Hello World", body);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/test-file-append");
        template.sendBodyAndHeader("file://target/test-file-append", "Hello", FileComponent.HEADER_FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://target/test-file-append", " World", FileComponent.HEADER_FILE_NAME, "world.txt");
        // give time to write files
        Thread.sleep(1000);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .setHeader(FileComponent.HEADER_FILE_NAME, constant("hello.txt"))
                    .to("file://target/test-file-append?append=true");
            }
        };
    }

}