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
package org.apache.camel.component.file.remote;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class FromEmptyDirectoryToFtpTest extends FtpServerTestSupport {

    protected String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/tmp3/camel?password=admin&consumer.initialDelay=3000&consumer.delay=3000&recursive=true&allowEmptyDirectory=true";
    }

    @Test
    public void testEmptyDirectoryToFtp() throws Exception {
        Files.createDirectories(Paths.get("src/main/data/emptydir"));
        Files.createDirectories(Paths.get("src/main/data/emptydir1"));
        Files.createDirectories(Paths.get("src/main/data/emptydir1/emptydir2"));
        Thread.sleep(6000);
        assertTrue(Files.exists(Paths.get(FTP_ROOT_DIR + "/tmp3/camel/emptydir")));
        assertTrue(Files.exists(Paths.get(FTP_ROOT_DIR + "/tmplocal/camel/emptydir")));
        assertTrue(Files.exists(Paths.get(FTP_ROOT_DIR + "/tmplocal/camel/emptydir1")));
        assertTrue(Files.exists(Paths.get(FTP_ROOT_DIR + "/tmplocal/camel/emptydir1/emptydir2")));
        deleteDirectory(new File(FTP_ROOT_DIR + "/tmp3/camel/emptydir"));
        deleteDirectory(new File(FTP_ROOT_DIR + "/tmplocal/camel/emptydir"));
        deleteDirectory(new File(FTP_ROOT_DIR + "/tmplocal/camel/emptydir1"));
        deleteDirectory(new File(FTP_ROOT_DIR + "/tmplocal/camel/emptydir1/emptydir2"));
        deleteDirectory(new File(FTP_ROOT_DIR + "/tmp3"));
        deleteDirectory(new File(FTP_ROOT_DIR + "/tmplocal"));
        deleteDirectory(new File("src/main/data/emptydir"));
        deleteDirectory(new File("src/main/data/emptydir1"));
        deleteDirectory(new File("src/main/data/emptydir1/emptydir2"));
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(getFtpUrl()).to("file:" + FTP_ROOT_DIR + "/tmplocal/camel?allowEmptyDirectory=true");
                from("file:src/main/data?noop=true&consumer.delay=6000&recursive=true&allowEmptyDirectory=true").to(getFtpUrl());
            }
        };
    }
}