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
package org.apache.camel.component.hdfs2;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class FromFileToHdfsTest extends HdfsTestSupport {

    private static final Path TEMP_DIR = new Path(new File("target/outbox/").getAbsolutePath());

    @Before
    public void setUp() throws Exception {
        if (!canTest()) {
            return;
        }
        deleteDirectory("target/inbox");
        deleteDirectory("target/outbox");
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        if (!canTest()) {
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
        if (!canTest()) {
            return;
        }

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        template.sendBodyAndHeader("file:target/inbox", "Hello World", Exchange.FILE_NAME, "hello.txt");

        notify.matchesMockWaitTime();

        File delete = new File("target/inbox/hello.txt");
        assertTrue("File should be deleted " + delete, !delete.exists());

        File create = new File(TEMP_DIR + "/output.txt");
        assertTrue("File should be created " + create, create.exists());
    }

    @Test
    public void testTwoFilesToHdfs() throws Exception {
        if (!canTest()) {
            return;
        }

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(2).create();

        template.sendBodyAndHeader("file:target/inbox", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file:target/inbox", "Bye World", Exchange.FILE_NAME, "bye.txt");

        notify.matchesMockWaitTime();

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
                    .to("hdfs2:localhost/" + TEMP_DIR.toUri() + "/output.txt?fileSystemType=LOCAL");
            }
        };
    }
}
