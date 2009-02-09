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
package org.apache.camel.component.jetty;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.converter.IOConverter;

/**
 * Unit testing demonstrating how to store incomming requests as files and serving a reponse back.
 */
public class HttpToFileTest extends ContextTestSupport {

    public void testToJettyAndSaveToFile() throws Exception {
        Object out = template.sendBody("http://localhost:9080/myworld", "Hello World");

        String response = context.getTypeConverter().convertTo(String.class, out);
        assertEquals("Response from Jetty", "We got the file", response);

        // give file some time to save
        Thread.sleep(1000);

        File file = new File("./target/myworld/hello.txt");
        file = file.getAbsoluteFile();
        assertTrue("File should exists", file.exists());

        String content = IOConverter.toString(file);
        assertEquals("File content", "Hello World", content);
    }

    @Override
    protected void setUp() throws Exception {
        disableJMX();
        super.setUp();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // put the incoming data on the seda queue and return a fixed response that we got the file
                from("jetty:http://localhost:9080/myworld").convertBodyTo(String.class).to("seda:in").transform(constant("We got the file"));

                // store the content from the queue as a file
                from("seda:in")
                    .setHeader(FileComponent.HEADER_FILE_NAME, constant("hello.txt"))
                    .to("file://target/myworld?append=false");
            }
        };
    }
}
