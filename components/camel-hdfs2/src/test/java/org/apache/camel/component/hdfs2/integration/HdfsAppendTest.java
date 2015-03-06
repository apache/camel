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
package org.apache.camel.component.hdfs2.integration;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Must run manual")
public class HdfsAppendTest extends CamelTestSupport {
    private static final int ITERATIONS = 10;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        Configuration conf = new Configuration();
        conf.addResource("hdfs-test.xml");
        Path file = new Path("hdfs://localhost:9000/tmp/test/test-camel-simple-write-file1");
        FileSystem fs = FileSystem.get(file.toUri(), conf);
        if (fs.exists(file)) {
            fs.delete(file, true);
        }
        FSDataOutputStream out = fs.create(file);
        for (int i = 0; i < 10; ++i) {
            out.write("PIPPO".getBytes("UTF-8"));
        }
        out.close();
    }

    @Test
    public void testAppend() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start1").to("hdfs2://localhost:9000/tmp/test/test-camel-simple-write-file1?append=true&fileSystemType=HDFS");
            }
        });
        startCamelContext();

        for (int i = 0; i < 10; ++i) {
            template.sendBody("direct:start1", "PIPPQ");
        }

        Configuration conf = new Configuration();
        Path file = new Path("hdfs://localhost:9000/tmp/test/test-camel-simple-write-file1");
        FileSystem fs = FileSystem.get(file.toUri(), conf);
        FSDataInputStream in = fs.open(file);
        byte[] buffer = new byte[5];
        int ret = 0;
        for (int i = 0; i < 20; ++i) {
            ret = in.read(buffer);
            System.out.println("> " + new String(buffer));
        }
        ret = in.read(buffer);
        assertEquals(-1, ret);
        in.close();
    }

    @Test
    public void testAppendWithDynamicFileName() throws Exception {

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start1").to("hdfs2://localhost:9000/tmp/test-dynamic/?append=true&fileSystemType=HDFS");
            }
        });
        startCamelContext();

        for (int i = 0; i < ITERATIONS; ++i) {
            template.sendBodyAndHeader("direct:start1", "HELLO", Exchange.FILE_NAME, "camel-hdfs2.log");
        }

        Configuration conf = new Configuration();
        Path file = new Path("hdfs://localhost:9000/tmp/test-dynamic/camel-hdfs2.log");
        FileSystem fs = FileSystem.get(file.toUri(), conf);
        FSDataInputStream in = fs.open(file);
        byte[] buffer = new byte[5];
        for (int i = 0; i < ITERATIONS; ++i) {
            assertEquals(5, in.read(buffer));
            System.out.println("> " + new String(buffer));
        }
        int ret = in.read(buffer);
        assertEquals(-1, ret);
        in.close();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        Thread.sleep(250);
        Configuration conf = new Configuration();
        Path dir = new Path("hdfs://localhost:9000/tmp/test");
        FileSystem fs = FileSystem.get(dir.toUri(), conf);
        fs.delete(dir, true);
        dir = new Path("hdfs://localhost:9000/tmp/test-dynamic");
        fs.delete(dir, true);
    }
}
