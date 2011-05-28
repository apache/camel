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

import junit.framework.Assert;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.junit.Test;

public class HdfsProducerSplitTest extends CamelTestSupport {

    @Test
    public void testSimpleWriteFileWithMessageSplit() throws Exception {
        for (int i = 0; i < 10; ++i) {
            template.sendBody("direct:start1", "CIAO" + i);
        }
        stopCamelContext();

        for (int i = 0; i < 10; ++i) {
            InputStream in = null;
            try {
                in = new URL("file:///tmp/test/test-camel-simple-write-file1" + '/' + HdfsConstants.DEFAULT_SEGMENT_PREFIX + i).openStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOUtils.copyBytes(in, bos, 4096, false);
                Assert.assertEquals("CIAO" + i, new String(bos.toByteArray()));
            } finally {
                IOUtils.closeStream(in);
            }
        }
    }

    @Test
    public void testSimpleWriteFileWithBytesSplit() throws Exception {
        for (int i = 0; i < 10; ++i) {
            template.sendBody("direct:start2", "CIAO" + i);
        }
        stopCamelContext();

        for (int i = 0; i < 10; ++i) {
            InputStream in = null;
            try {
                in = new URL("file:///tmp/test/test-camel-simple-write-file2" + '/' + HdfsConstants.DEFAULT_SEGMENT_PREFIX + i).openStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOUtils.copyBytes(in, bos, 4096, false);
                Assert.assertEquals("CIAO" + i, new String(bos.toByteArray()));
            } finally {
                IOUtils.closeStream(in);
            }
        }
    }

    @Test
    public void testSimpleWriteFileWithIdleSplit() throws Exception {
        for (int i = 0; i < 3; ++i) {
            template.sendBody("direct:start3", "CIAO" + i);
            Thread.sleep(2000);
        }

        for (int i = 0; i < 3; ++i) {
            InputStream in = null;
            try {
                in = new URL("file:///tmp/test/test-camel-simple-write-file3" + '/' + HdfsConstants.DEFAULT_SEGMENT_PREFIX + i).openStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOUtils.copyBytes(in, bos, 4096, false);
                Assert.assertEquals("CIAO" + i, new String(bos.toByteArray()));
            } finally {
                IOUtils.closeStream(in);
            }
        }
    }

    @Test
    public void testSimpleWriteFileWithMessageIdleSplit() throws Exception {
        for (int i = 0; i < 10; ++i) {
            template.sendBody("direct:start4", "CIAO" + i);
        }
        Thread.sleep(2000);

        for (int i = 0; i < 10; ++i) {
            InputStream in = null;
            try {
                in = new URL("file:///tmp/test/test-camel-simple-write-file4" + '/' + HdfsConstants.DEFAULT_SEGMENT_PREFIX + i).openStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOUtils.copyBytes(in, bos, 4096, false);
                Assert.assertEquals("CIAO" + i, new String(bos.toByteArray()));
            } finally {
                IOUtils.closeStream(in);
            }
        }
    }

    @Test
    public void testSimpleWriteFileWithBytesIdleSplit() throws Exception {
        for (int i = 0; i < 10; ++i) {
            template.sendBody("direct:start5", "CIAO" + i);
        }
        Thread.sleep(2000);

        for (int i = 0; i < 10; ++i) {
            InputStream in = null;
            try {
                in = new URL("file:///tmp/test/test-camel-simple-write-file5" + '/' + HdfsConstants.DEFAULT_SEGMENT_PREFIX + i).openStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOUtils.copyBytes(in, bos, 4096, false);
                Assert.assertEquals("CIAO" + i, new String(bos.toByteArray()));
            } finally {
                IOUtils.closeStream(in);
            }
        }
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        Thread.sleep(100);
        Configuration conf = new Configuration();
        Path dir = new Path("file:///tmp/test");
        FileSystem fs = FileSystem.get(dir.toUri(), conf);
        fs.delete(dir, true);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start1").to("hdfs://localhost/tmp/test/test-camel-simple-write-file1?fileSystemType=LOCAL&splitStrategy=MESSAGES:1");
                from("direct:start2").to("hdfs://localhost/tmp/test/test-camel-simple-write-file2?fileSystemType=LOCAL&splitStrategy=BYTES:5");
                from("direct:start3").to("hdfs://localhost/tmp/test/test-camel-simple-write-file3?fileSystemType=LOCAL&splitStrategy=IDLE:1000");
                from("direct:start4").to("hdfs://localhost/tmp/test/test-camel-simple-write-file4?fileSystemType=LOCAL&splitStrategy=IDLE:1000,MESSAGES:1");
                from("direct:start5").to("hdfs://localhost/tmp/test/test-camel-simple-write-file5?fileSystemType=LOCAL&splitStrategy=IDLE:1000,BYTES:5");
            }
        };
    }
}
