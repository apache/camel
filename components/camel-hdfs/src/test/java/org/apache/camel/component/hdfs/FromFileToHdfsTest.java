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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class FromFileToHdfsTest extends HdfsTestSupport {

    private static final Path TEMP_DIR = new Path(new File("target/outbox/").getAbsolutePath());

    @Override
    @Before
    public void setUp() throws Exception {
        if (skipTest()) {
            return;
        }
        deleteDirectory("target/inbox");
        deleteDirectory("target/outbox");
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        if (skipTest()) {
            return;
        }

        super.tearDown();
        Configuration conf = new Configuration();
        Path dir = new Path("target/outbox");
        FileSystem fs = FileSystem.get(dir.toUri(), conf);
        fs.delete(dir, true);
    }

    @Test
    public void testFileToHdfs() throws Exception {
        if (skipTest()) {
            return;
        }

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        template.sendBodyAndHeader("file:target/inbox", "Hello World", Exchange.FILE_NAME, "hello.txt");

        notify.matchesWaitTime();

        File delete = new File("target/inbox/hello.txt");
        assertTrue("File should be deleted " + delete, !delete.exists());

        File create = new File(TEMP_DIR + "/output.txt");
        assertTrue("File should be created " + create, create.exists());
    }

    @Test
    public void testTwoFilesToHdfs() throws Exception {
        if (skipTest()) {
            return;
        }

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(2).create();

        template.sendBodyAndHeader("file:target/inbox", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file:target/inbox", "Bye World", Exchange.FILE_NAME, "bye.txt");

        notify.matchesWaitTime();

        File delete = new File("target/inbox/hello.txt");
        assertTrue("File should be deleted " + delete, !delete.exists());
        delete = new File("target/inbox/bye.txt");
        assertTrue("File should be deleted " + delete, !delete.exists());

        File create = new File(TEMP_DIR + "/output.txt");
        assertTrue("File should be created " + create, create.exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/inbox?delete=true")
                    .to("hdfs:localhost/" + TEMP_DIR.toUri() + "/output.txt?fileSystemType=LOCAL");
            }
        };
    }
}
