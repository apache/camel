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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import org.apache.camel.builder.RouteBuilder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.Before;
import org.junit.Test;

public class HdfsProducerSplitTest extends HdfsTestSupport {

    private static final Path BASE_FILE = new Path(new File("target/test/test-camel-simple-write-BASE_FILE").getAbsolutePath());

    @Before
    public void setUp() throws Exception {
        if (!canTest()) {
            return;
        }
        super.setUp();
    }
    
    @Test
    public void testSimpleWriteFileWithMessageSplit() throws Exception {
        doTest(1);
    }

    @Test
    public void testSimpleWriteFileWithBytesSplit() throws Exception {
        doTest(2);
    }

    @Test
    public void testSimpleWriteFileWithIdleSplit() throws Exception {
        if (!canTest()) {
            return;
        }

        for (int i = 0; i < 3; ++i) {
            template.sendBody("direct:start3", "CIAO" + i);
            Thread.sleep(2000);
        }

        // stop Camel to flush and close file stream
        stopCamelContext();

        FileSystem fs = FileSystem.get(new Configuration());
        FileStatus[] status = fs.listStatus(new Path("file:///" + BASE_FILE.toUri() + "3"));
        assertEquals(3, status.length);
        for (int i = 0; i < 3; i++) {
            BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(status[i].getPath())));
            assertTrue(br.readLine().startsWith("CIAO"));
            assertNull(br.readLine());
        }
    }

    @Test
    public void testSimpleWriteFileWithMessageIdleSplit() throws Exception {
        doTest(4);
    }

    @Test
    public void testSimpleWriteFileWithBytesIdleSplit() throws Exception {
        doTest(5);
    }

    private void doTest(int routeNr) throws Exception {
        if (!canTest()) {
            return;
        }

        for (int i = 0; i < 10; ++i) {
            template.sendBody("direct:start" + routeNr, "CIAO" + i);
        }
        stopCamelContext();

        FileSystem fs = FileSystem.get(new Configuration());
        FileStatus[] status = fs.listStatus(new Path("file:///" + BASE_FILE.toUri() + routeNr));
        assertEquals(10, status.length);
        for (FileStatus fileStatus : status) {
            BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(fileStatus.getPath())));
            assertTrue(br.readLine().startsWith("CIAO"));
            assertNull(br.readLine());
        }
    }

    @Override
    public void tearDown() throws Exception {
        if (!canTest()) {
            return;
        }

        super.tearDown();
        Thread.sleep(100);
        Configuration conf = new Configuration();
        Path dir = new Path("target/test");
        FileSystem fs = FileSystem.get(dir.toUri(), conf);
        fs.delete(dir, true);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start1").to("hdfs2:localhost/" + BASE_FILE.toUri() + "1?fileSystemType=LOCAL&splitStrategy=MESSAGES:1");
                from("direct:start2").to("hdfs2:localhost/" + BASE_FILE.toUri() + "2?fileSystemType=LOCAL&splitStrategy=BYTES:5");
                from("direct:start3").to("hdfs2:localhost/" + BASE_FILE.toUri() + "3?fileSystemType=LOCAL&splitStrategy=IDLE:1000");
                from("direct:start4").to("hdfs2:localhost/" + BASE_FILE.toUri() + "4?fileSystemType=LOCAL&splitStrategy=IDLE:1000,MESSAGES:1");
                from("direct:start5").to("hdfs2:localhost/" + BASE_FILE.toUri() + "5?fileSystemType=LOCAL&splitStrategy=IDLE:1000,BYTES:5");
            }
        };
    }
}
