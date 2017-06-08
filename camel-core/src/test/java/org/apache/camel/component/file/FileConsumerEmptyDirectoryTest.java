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
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class FileConsumerEmptyDirectoryTest extends ContextTestSupport {
    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/emptytest_out");
        super.setUp();
    }
    
    public void testEmptyDirectory() throws Exception {
        Files.createDirectories(Paths.get("src/test/resources/org/apache/camel/component/file/data/emptydir"));
        Files.createDirectories(Paths.get("src/test/resources/org/apache/camel/component/file/data/emptydir1"));
        Files.createDirectories(Paths.get("src/test/resources/org/apache/camel/component/file/data/emptydir1/emptydir2"));
        MockEndpoint mock = getMockEndpoint("mock:emptyDirectory");
        mock.expectedMessageCount(1);
        
        assertMockEndpointsSatisfied();
        
        Thread.sleep(500);
        
        assertTrue(Files.exists(Paths.get("target/emptytest_out/emptydir")));
        assertTrue(Files.exists(Paths.get("target/emptytest_out/emptydir1")));
        assertTrue(Files.exists(Paths.get("target/emptytest_out/emptydir1/emptydir2")));
        deleteDirectory(new File("target/emptytest_out"));
        deleteDirectory(new File("src/test/resources/org/apache/camel/component/file/data/emptydir"));
        deleteDirectory(new File("src/test/resources/org/apache/camel/component/file/data/emptydir1"));
        deleteDirectory(new File("src/test/resources/org/apache/camel/component/file/data/emptydir1/emptydir2"));
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file:src/test/resources/org/apache/camel/component/file/data?consumer.delay=5000&noop=true&recursive=true&allowEmptyDirectory=true")
                    .to("mock:emptyDirectory")
                    .to("file:target/emptytest_out?allowEmptyDirectory=true");
            }
        };
    }

}
