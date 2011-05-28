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
package org.apache.camel.component.hdfs;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;

import junit.framework.Assert;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.MapFile;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.util.ReflectionUtils;
import org.junit.Test;

public class HdfsProducerFileWriteTest extends CamelTestSupport {

    @Test
    public void testSimpleWriteFile() throws Exception {
        deleteDirectory("target/file-batch1");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/file-batch1?sortBy=file:name")
                        .to("hdfs://localhost/tmp/test/test-camel-simple-write-file?fileSystemType=LOCAL");
            }
        });

        context.start();

        NotifyBuilder nb = new NotifyBuilder(context).whenDone(10).create();

        for (int i = 0; i < 10; ++i) {
            template.sendBodyAndHeader("file://target/file-batch1/", "CIAO", "CamelFileName", "CIAO" + i);
        }

        nb.matchesMockWaitTime();
        context.stop();

        InputStream in = null;
        try {
            in = new URL("file:///tmp/test/test-camel-simple-write-file").openStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOUtils.copyBytes(in, bos, 4096, false);
            Assert.assertEquals(40, bos.size());
        } finally {
            IOUtils.closeStream(in);
        }
    }

    @Test
    public void testSequenceWriteFile() throws Exception {
        deleteDirectory("target/file-batch2");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/file-batch2?sortBy=file:name")
                        .to("hdfs://localhost/tmp/test/test-camel-sequence-write-file1?fileSystemType=LOCAL&fileType=SEQUENCE_FILE");
            }
        });

        context.start();

        NotifyBuilder nb = new NotifyBuilder(context).whenDone(10).create();

        for (int i = 0; i < 10; ++i) {
            template.sendBodyAndHeader("file://target/file-batch2", "CIAO", "CamelFileName", "CIAO" + i);
        }

        nb.matchesMockWaitTime();
        context.stop();

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///tmp/test/test-camel-sequence-write-file1");
        FileSystem fs1 = FileSystem.get(file1.toUri(), conf);
        SequenceFile.Reader reader = new SequenceFile.Reader(fs1, file1, conf);
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        BytesWritable value = (BytesWritable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        int i = 0;
        while (reader.next(key, value)) {
            String str = new String(value.getBytes(), 0, value.getLength());
            Assert.assertEquals("CIAO", str);
            i++;
        }
        Assert.assertEquals(10, i);
    }

    @Test
    public void testSequenceKeyWriteFile() throws Exception {
        deleteDirectory("target/file-batch3");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/file-batch3?sortBy=file:name")
                        .setHeader("KEY").simple("${in.header.CamelFileName}")
                        .to("hdfs://localhost/tmp/test/test-camel-sequence-write-file2?fileSystemType=LOCAL&keyType=TEXT&fileType=SEQUENCE_FILE");
            }
        });

        context.start();

        NotifyBuilder nb = new NotifyBuilder(context).whenDone(10).create();

        for (int i = 0; i < 10; ++i) {
            template.sendBodyAndHeader("file://target/file-batch3", "CIAO", "CamelFileName", "CIAO" + i);
        }

        nb.matchesMockWaitTime();
        context.stop();

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///tmp/test/test-camel-sequence-write-file2");
        FileSystem fs1 = FileSystem.get(file1.toUri(), conf);
        SequenceFile.Reader reader = new SequenceFile.Reader(fs1, file1, conf);
        Text key = (Text) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        BytesWritable value = (BytesWritable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        int i = 0;
        while (reader.next(key, value)) {
            String str = new String(value.getBytes(), 0, value.getLength());
            Assert.assertEquals("CIAO", str);
            Assert.assertEquals("CIAO" + i, key.toString());
            i++;
        }
        Assert.assertEquals(10, i);
    }

    @Test
    public void testMapKeyWriteFile() throws Exception {
        deleteDirectory("target/file-batch4");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/file-batch4?sortBy=file:name")
                        .setHeader("KEY").simple("${in.header.CamelFileName}")
                        .to("hdfs://localhost/tmp/test/test-camel-map-write-file1?fileSystemType=LOCAL&keyType=TEXT&fileType=MAP_FILE");
            }
        });

        context.start();

        NotifyBuilder nb = new NotifyBuilder(context).whenDone(10).create();

        for (int i = 0; i < 10; ++i) {
            template.sendBodyAndHeader("file://target/file-batch4", "CIAO" + i, "CamelFileName", "CIAO" + i);
        }

        nb.matchesMockWaitTime();
        context.stop();

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///tmp/test/test-camel-map-write-file1");
        FileSystem fs1 = FileSystem.get(file1.toUri(), conf);
        MapFile.Reader reader = new MapFile.Reader(fs1, "file:///tmp/test/test-camel-map-write-file1", conf);
        for (int i = 0; i < 10; ++i) {
            Text key = new Text("CIAO" + i);
            BytesWritable value = new BytesWritable();
            reader.get(key, value);
            String str = new String(value.getBytes(), 0, value.getLength());
            Assert.assertEquals("CIAO" + i, str);
            Assert.assertEquals("CIAO" + i, key.toString());
        }
    }


    @Test
    public void testSequenceKeyWriteBigFile() throws Exception {
        deleteDirectory("target/file-batch5");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/file-batch5?sortBy=file:name")
                        .to("hdfs://localhost/tmp/test/test-camel-sequence-write-file3?fileSystemType=LOCAL&fileType=SEQUENCE_FILE&splitStrategy=IDLE:100&checkIdleInterval=10");
            }
        });

        context.start();

        NotifyBuilder nb = new NotifyBuilder(context).whenDone(2).create();

        ByteBuffer bb = ByteBuffer.allocate(8 * 1024 * 1024);
        for (int i = 0; i < 8 * 1024 * 512; ++i) {
            bb.putChar('A');
        }
        for (int i = 0; i < 2; ++i) {
            template.sendBodyAndHeader("file://target/file-batch5", bb, "CamelFileName", "CIAO" + i);
        }

        nb.matchesMockWaitTime();
        context.stop();

        Configuration conf = new Configuration();
        Path file1 = new Path("file:///tmp/test/test-camel-sequence-write-file3/" + '/' + HdfsConstants.DEFAULT_SEGMENT_PREFIX + 0);
        FileSystem fs1 = FileSystem.get(file1.toUri(), conf);
        SequenceFile.Reader reader = new SequenceFile.Reader(fs1, file1, conf);
        Writable key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), conf);
        BytesWritable value = (BytesWritable) ReflectionUtils.newInstance(reader.getValueClass(), conf);
        int i = 0;
        while (reader.next(key, value)) {
            Assert.assertEquals(value.getLength(), 8 * 1024 * 1024);
            i++;
        }
        Assert.assertEquals(2, i);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        Configuration conf = new Configuration();
        Path dir = new Path("file:///tmp/test");
        FileSystem fs = FileSystem.get(dir.toUri(), conf);
        fs.delete(dir, true);
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

}
