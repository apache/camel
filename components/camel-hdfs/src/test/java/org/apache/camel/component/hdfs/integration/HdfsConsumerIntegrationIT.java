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
package org.apache.camel.component.hdfs.integration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hdfs.HdfsTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultScheduledPollConsumerScheduler;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.io.ArrayFile;
import org.apache.hadoop.io.BooleanWritable;
import org.apache.hadoop.io.ByteWritable;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.SequenceFile.Writer;
import org.apache.hadoop.io.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HdfsConsumerIntegrationIT extends HdfsTestSupport {
    private static final int ITERATIONS = 200;

    private MiniDFSCluster cluster;
    private FileSystem fs;
    private Configuration conf;
    private Path dir;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        checkTest();

        conf = new Configuration();
        conf.set("dfs.namenode.fs-limits.max-directory-items", "1048576");
        cluster = new MiniDFSCluster.Builder(conf).nameNodePort(9000).numDataNodes(3).format(true).build();
        cluster.waitActive();

        dir = new Path("hdfs://localhost:9000/tmp/test");
        fs = FileSystem.get(dir.toUri(), conf);
        fs.mkdirs(dir);

        // must be able to get security configuration
        try {
            javax.security.auth.login.Configuration.getConfiguration();
        } catch (Exception e) {
            return;
        }

        super.setUp();
    }

    @Test
    public void testSimpleConsumer() throws Exception {
        final String file = "test-camel-normal-file";
        try (FSDataOutputStream out = fs.create(new Path(dir, file))) {
            for (int i = 0; i < 1024; ++i) {
                out.write(("PIPPO" + i).getBytes("UTF-8"));
                out.flush();
            }
        }

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(2);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                fromF("%s?pattern=%s&fileSystemType=HDFS&chunkSize=4096&initialDelay=0", dir.toUri(), file)
                        .to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testConcurrentConsumers() throws Exception {
        final Path dir = new Path(this.dir, "multiple-consumers");
        fs.mkdirs(dir);
        for (int i = 1; i <= ITERATIONS; i++) {
            try (FSDataOutputStream fos = fs.create(new Path(dir, String.format("file-%04d.txt", i)))) {
                fos.write(String.format("hello (%04d)\n", i).getBytes());
            }
        }

        final Set<String> fileNames = new HashSet<>();
        final CountDownLatch latch = new CountDownLatch(ITERATIONS);
        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) {
                fileNames.add(exchange.getIn().getHeader(Exchange.FILE_NAME, String.class));
                latch.countDown();
            }
        });

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from(dir.toUri()
                     + "?pattern=*.txt&fileSystemType=HDFS&chunkSize=100&initialDelay=0")
                             .to("mock:result");
                from(dir.toUri()
                     + "?pattern=*.txt&fileSystemType=HDFS&chunkSize=200&initialDelay=0")
                             .to("mock:result");
                from(dir.toUri()
                     + "?pattern=*.txt&fileSystemType=HDFS&chunkSize=300&initialDelay=0")
                             .to("mock:result");
                from(dir.toUri()
                     + "pattern=*.txt&fileSystemType=HDFS&chunkSize=400&initialDelay=0")
                             .to("mock:result");
            }
        });
        context.start();

        resultEndpoint.expectedMessageCount(ITERATIONS);

        latch.await(30, TimeUnit.SECONDS);

        resultEndpoint.assertIsSatisfied();
        assertEquals(fileNames.size(), ITERATIONS);
    }

    @Test
    public void testSimpleConsumerWithEmptyFile() throws Exception {
        final String file = "test-camel-normal-file";
        fs.createNewFile(new Path(dir, file));

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        // TODO: See comment from Claus at ticket: https://issues.apache.org/jira/browse/CAMEL-8434
        resultEndpoint.expectedMinimumMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                fromF("%s?pattern=%s&fileSystemType=HDFS&chunkSize=4096&initialDelay=0", dir.toUri(), file)
                        .to("mock:result");
            }
        });
        context.start();

        Thread.sleep(2000);

        resultEndpoint.assertIsSatisfied();
        assertEquals(
                resultEndpoint.getReceivedExchanges().get(0).getIn().getBody(ByteArrayOutputStream.class).toByteArray().length,
                0);
    }

    @Test
    public void testSimpleConsumerFileWithSizeEqualToNChunks() throws Exception {
        final String file = "test-camel-normal-file";
        try (FSDataOutputStream out = fs.create(new Path(dir, file))) {
            // size = 5 times chunk size = 210 bytes
            for (int i = 0; i < 42; ++i) {
                out.write(new byte[] { 0x61, 0x62, 0x63, 0x64, 0x65 });
                out.flush();
            }
        }

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(5);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                fromF("%s?pattern=%s&fileSystemType=HDFS&chunkSize=42&initialDelay=0", dir.toUri(), file)
                        .to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
        assertEquals(
                resultEndpoint.getReceivedExchanges().get(0).getIn().getBody(ByteArrayOutputStream.class).toByteArray().length,
                42);
    }

    @Test
    public void testSimpleConsumerWithEmptySequenceFile() throws Exception {
        final String file = "test-camel-sequence-file";
        try (SequenceFile.Writer writer = createWriter(conf, new Path(dir, file), NullWritable.class, BooleanWritable.class)) {
            writer.sync();
        }

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(0);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                fromF("%s?pattern=%s&fileSystemType=HDFS&fileType=SEQUENCE_FILE&chunkSize=4096&initialDelay=0", dir.toUri(),
                        file)
                                .to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadWithReadSuffix() throws Exception {
        final String file = "test-camel-boolean";
        NullWritable keyWritable = NullWritable.get();
        BooleanWritable valueWritable = new BooleanWritable(true);
        try (SequenceFile.Writer writer = createWriter(conf, new Path(dir, file), NullWritable.class, BooleanWritable.class)) {
            writer.append(keyWritable, valueWritable);
            writer.sync();
        }

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from(dir.toUri()
                     + "?scheduler=#myScheduler&pattern=*&fileSystemType=HDFS&fileType=SEQUENCE_FILE&initialDelay=0&readSuffix=handled")
                             .to("mock:result");
            }
        });
        ScheduledExecutorService pool = context.getExecutorServiceManager().newScheduledThreadPool(null, "unitTestPool", 1);
        DefaultScheduledPollConsumerScheduler scheduler = new DefaultScheduledPollConsumerScheduler(pool);
        context.getRegistry().bind("myScheduler", scheduler);
        context.start();

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.assertIsSatisfied();

        // synchronize on pool that was used to run hdfs consumer thread
        scheduler.getScheduledExecutorService().shutdown();
        scheduler.getScheduledExecutorService().awaitTermination(5000, TimeUnit.MILLISECONDS);

        assertEquals(fs.listStatus(dir).length, 1);
        assertTrue(fs.delete(new Path(dir, file + ".handled")));
    }

    @Test
    public void testReadBoolean() throws Exception {
        final String file = "test-camel-boolean";
        NullWritable keyWritable = NullWritable.get();
        BooleanWritable valueWritable = new BooleanWritable(true);
        try (SequenceFile.Writer writer = createWriter(conf, new Path(dir, file), NullWritable.class, BooleanWritable.class)) {
            writer.append(keyWritable, valueWritable);
            writer.sync();
        }

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                fromF("%s?pattern=%s&fileSystemType=HDFS&fileType=SEQUENCE_FILE&initialDelay=0", dir.toUri(), file)
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadByte() throws Exception {
        final String file = "test-camel-byte";
        NullWritable keyWritable = NullWritable.get();
        ByteWritable valueWritable = new ByteWritable((byte) 3);
        try (SequenceFile.Writer writer = createWriter(conf, new Path(dir, file), NullWritable.class, ByteWritable.class)) {
            writer.append(keyWritable, valueWritable);
            writer.sync();
        }

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).body(byte.class).isEqualTo(3);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                fromF("%s?pattern=%s&fileSystemType=HDFS&fileType=SEQUENCE_FILE&initialDelay=0", dir.toUri(), file)
                        .to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadFloat() throws Exception {
        final String file = "test-camel-float";
        NullWritable keyWritable = NullWritable.get();
        FloatWritable valueWritable = new FloatWritable(3.1415926535f);
        try (SequenceFile.Writer writer = createWriter(conf, new Path(dir, file), NullWritable.class, FloatWritable.class)) {
            writer.append(keyWritable, valueWritable);
            writer.sync();
        }

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                fromF("%s?pattern=%s&fileSystemType=HDFS&fileType=SEQUENCE_FILE&initialDelay=0", dir.toUri(), file)
                        .to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadDouble() throws Exception {
        final String file = "test-camel-double";
        NullWritable keyWritable = NullWritable.get();
        DoubleWritable valueWritable = new DoubleWritable(3.1415926535);
        try (SequenceFile.Writer writer = createWriter(conf, new Path(dir, file), NullWritable.class, DoubleWritable.class)) {
            writer.append(keyWritable, valueWritable);
            writer.sync();
        }

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                fromF("%s?pattern=%s&fileSystemType=HDFS&fileType=SEQUENCE_FILE&initialDelay=0", dir.toUri(), file)
                        .to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadInt() throws Exception {
        final String file = "test-camel-int";
        NullWritable keyWritable = NullWritable.get();
        IntWritable valueWritable = new IntWritable(314159265);
        try (SequenceFile.Writer writer = createWriter(conf, new Path(dir, file), NullWritable.class, IntWritable.class)) {
            writer.append(keyWritable, valueWritable);
            writer.sync();
        }

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                fromF("%s?pattern=%s&fileSystemType=HDFS&fileType=SEQUENCE_FILE&initialDelay=0", dir.toUri(), file)
                        .to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadLong() throws Exception {
        final String file = "test-camel-long";
        NullWritable keyWritable = NullWritable.get();
        LongWritable valueWritable = new LongWritable(31415926535L);
        try (SequenceFile.Writer writer = createWriter(conf, new Path(dir, file), NullWritable.class, LongWritable.class)) {
            writer.append(keyWritable, valueWritable);
            writer.sync();
        }

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                fromF("%s?pattern=%s&fileSystemType=HDFS&fileType=SEQUENCE_FILE&initialDelay=0", dir.toUri(), file)
                        .to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadBytes() throws Exception {
        final String file = "test-camel-bytes";
        NullWritable keyWritable = NullWritable.get();
        BytesWritable valueWritable = new BytesWritable("CIAO!".getBytes());
        try (SequenceFile.Writer writer = createWriter(conf, new Path(dir, file), NullWritable.class, BytesWritable.class)) {
            writer.append(keyWritable, valueWritable);
            writer.sync();
        }

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                fromF("%s?pattern=%s&fileSystemType=HDFS&fileType=SEQUENCE_FILE&initialDelay=0", dir.toUri(), file)
                        .to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadString() throws Exception {
        final String file = "test-camel-string";
        NullWritable keyWritable = NullWritable.get();
        Text valueWritable = new Text("CIAO!");
        try (SequenceFile.Writer writer = createWriter(conf, new Path(dir, file), NullWritable.class, Text.class)) {
            writer.append(keyWritable, valueWritable);
            writer.sync();
        }

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                fromF("%s?pattern=%s&fileSystemType=HDFS&fileType=SEQUENCE_FILE&initialDelay=0", dir.toUri(), file)
                        .to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadStringArrayFile() throws Exception {
        final String file = "test-camel-string";
        Text valueWritable = new Text("CIAO!");
        try (ArrayFile.Writer writer = new ArrayFile.Writer(conf, fs, dir.toUri() + "/" + file, Text.class)) {
            writer.append(valueWritable);
        }

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                fromF("%s?pattern=%s&fileSystemType=HDFS&fileType=ARRAY_FILE&initialDelay=0", dir.toUri(), file)
                        .to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        if (cluster != null) {
            cluster.shutdown();
        }
    }

    private Writer createWriter(
            Configuration conf, Path file, Class<?> keyClass,
            Class<?> valueClass)
            throws IOException {
        return SequenceFile.createWriter(conf, SequenceFile.Writer.file(file),
                SequenceFile.Writer.keyClass(keyClass), SequenceFile.Writer.valueClass(valueClass));
    }

}
