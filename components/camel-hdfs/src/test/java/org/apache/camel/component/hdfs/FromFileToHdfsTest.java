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
package org.apache.camel.component.hdfs;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
public class FromFileToHdfsTest extends HdfsTestSupport {

    private static final Path TEMP_DIR = new Path(new File("target/outbox/").getAbsolutePath());

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        checkTest();
        deleteDirectory("target/inbox");
        deleteDirectory("target/outbox");
        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        Configuration conf = new Configuration();
        Path dir = new Path("target/outbox");
        FileSystem fs = FileSystem.get(dir.toUri(), conf);
        fs.delete(dir, true);
    }

    @Test
    public void testFileToHdfs() {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        template.sendBodyAndHeader("file:target/inbox", "Hello World", Exchange.FILE_NAME, "hello.txt");

        notify.matchesWaitTime();

        File delete = new File("target/inbox/hello.txt");
        assertFalse(delete.exists(), "File should be deleted " + delete);

        File create = new File(TEMP_DIR + "/output.txt");
        assertTrue(create.exists(), "File should be created " + create);
    }

    @Test
    public void testTwoFilesToHdfs() {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(2).create();

        template.sendBodyAndHeader("file:target/inbox", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file:target/inbox", "Bye World", Exchange.FILE_NAME, "bye.txt");

        notify.matchesWaitTime();

        File delete = new File("target/inbox/hello.txt");
        assertFalse(delete.exists(), "File should be deleted " + delete);
        delete = new File("target/inbox/bye.txt");
        assertFalse(delete.exists(), "File should be deleted " + delete);

        File create = new File(TEMP_DIR + "/output.txt");
        assertTrue(create.exists(), "File should be created " + create);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("file:target/inbox?delete=true")
                        .to("hdfs:localhost/" + TEMP_DIR.toUri() + "/output.txt?fileSystemType=LOCAL");
            }
        };
    }
}
