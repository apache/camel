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
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test for file producer option tempPrefix
 */
public class FileProduceTempPrefixTest extends ContextTestSupport {

    private String fileUrl = "file://target/tempandrename/?tempPrefix=inprogress.";

    public void testCreateTempFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUrl);
        FileProducer producer = (FileProducer) endpoint.createProducer();

        File fileName = new File("target/tempandrename/claus.txt");
        File tempFileName = producer.createTempFileName(fileName);
        assertEquals("target" + File.separatorChar + "tempandrename" + File.separatorChar + "inprogress.claus.txt", tempFileName.getPath());
    }

    public void testNoPathCreateTempFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUrl);
        FileProducer producer = (FileProducer) endpoint.createProducer();

        File fileName = new File("claus.txt");
        File tempFileName = producer.createTempFileName(fileName);
        assertEquals("inprogress.claus.txt", tempFileName.getPath());
    }

    public void testTempPrefix() throws Exception {
        deleteDirectory("target/tempandrename");

        template.sendBodyAndHeader("direct:a", "Hello World", FileComponent.HEADER_FILE_NAME, "hello.txt");

        Thread.sleep(250);

        File file = new File("target/tempandrename/hello.txt");
        // use absolute file to let unittest pass on all platforms
        file = file.getAbsoluteFile();
        assertEquals("The generated file should exists: " + file, true, file.exists());
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:a").to(fileUrl);
            }
        };
    }
}