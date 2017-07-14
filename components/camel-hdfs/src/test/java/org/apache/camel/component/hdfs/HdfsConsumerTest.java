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
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultScheduledPollConsumerScheduler;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
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
import org.apache.hadoop.io.Text;
import org.apache.hadoop.util.Progressable;
import org.junit.Before;
import org.junit.Test;

import static org.apache.hadoop.io.SequenceFile.CompressionType;
import static org.apache.hadoop.io.SequenceFile.createWriter;
import static org.hamcrest.CoreMatchers.equalTo;

public class HdfsConsumerTest extends HdfsTestSupport {
    private static final int ITERATIONS = 200;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Before
    public void setUp() throws Exception {
        if (!canTest()) {
            return;
        }

        // must be able to get security configuration
        try {
            javax.security.auth.login.Configuration.getConfiguration();
        } catch (Exception e) {
            return;
        }

        deleteDirectory("target/test");
        super.setUp();
    }
    
    @Test
    public void testSimpleConsumer() throws Exception {
        if (!canTest()) {
            return;
        }

        final Path file = new Path(new File("target/test/test-camel-normal-file").getAbsolutePath());
        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(file.toUri(), conf);
        FSDataOutputStream out = fs.create(file);
        for (int i = 0; i < 1024; ++i) {
            out.write(("PIPPO" + i).getBytes("UTF-8"));
            out.flush();
        }
        out.close();

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(2);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:localhost/" + file.toUri() + "?fileSystemType=LOCAL&chunkSize=4096&initialDelay=0").to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testConcurrentConsumers() throws Exception {
        if (!canTest()) {
            return;
        }

        final File rootdir = new File(".");
        final File dir = new File("target/test/multiple-consumers");
        dir.mkdirs();
        for (int i = 1; i <= ITERATIONS; i++) {
            FileOutputStream fos = new FileOutputStream(new File(dir, String.format("file-%04d.txt", i)));
            fos.write(String.format("hello (%04d)\n", i).getBytes());
            fos.close();
        }

        final Set<String> fileNames = new HashSet<String>();
        final CountDownLatch latch = new CountDownLatch(ITERATIONS);
        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                fileNames.add(exchange.getIn().getHeader(Exchange.FILE_NAME, String.class));
                latch.countDown();
            }
        });

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs://" + rootdir.toURI() + "/target/test/multiple-consumers?pattern=*.txt&fileSystemType=LOCAL&chunkSize=100&initialDelay=0").to("mock:result");
                from("hdfs://" + rootdir.toURI() + "/target/test/multiple-consumers?pattern=*.txt&fileSystemType=LOCAL&chunkSize=200&initialDelay=0").to("mock:result");
                from("hdfs://" + rootdir.toURI() + "/target/test/multiple-consumers?pattern=*.txt&fileSystemType=LOCAL&chunkSize=300&initialDelay=0").to("mock:result");
                from("hdfs://" + rootdir.toURI() + "/target/test/multiple-consumers?pattern=*.txt&fileSystemType=LOCAL&chunkSize=400&initialDelay=0").to("mock:result");
            }
        });
        context.start();

        resultEndpoint.expectedMessageCount(ITERATIONS);

        latch.await(30, TimeUnit.SECONDS);

        resultEndpoint.assertIsSatisfied();
        assertThat(fileNames.size(), equalTo(ITERATIONS));
    }

    @Test
    public void testSimpleConsumerWithEmptyFile() throws Exception {
        if (!canTest()) {
            return;
        }

        final Path file = new Path(new File("target/test/test-camel-normal-file").getAbsolutePath());
        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(file.toUri(), conf);
        FSDataOutputStream out = fs.create(file);
        out.close();

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        // TODO: See comment from Claus at ticket: https://issues.apache.org/jira/browse/CAMEL-8434
        resultEndpoint.expectedMinimumMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:localhost/" + file.toUri() + "?fileSystemType=LOCAL&chunkSize=4096&initialDelay=0").to("mock:result");
            }
        });
        context.start();

        Thread.sleep(2000);

        resultEndpoint.assertIsSatisfied();
        assertThat(resultEndpoint.getReceivedExchanges().get(0).getIn().getBody(ByteArrayOutputStream.class).toByteArray().length, equalTo(0));
    }

    @Test
    public void testSimpleConsumerFileWithSizeEqualToNChunks() throws Exception {
        if (!canTest()) {
            return;
        }

        final Path file = new Path(new File("target/test/test-camel-normal-file").getAbsolutePath());
        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(file.toUri(), conf);
        FSDataOutputStream out = fs.create(file);
        // size = 5 times chunk size = 210 bytes
        for (int i = 0; i < 42; ++i) {
            out.write(new byte[] {0x61, 0x62, 0x63, 0x64, 0x65});
            out.flush();
        }
        out.close();

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(5);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:localhost/" + file.toUri() + "?fileSystemType=LOCAL&chunkSize=42&initialDelay=0").to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
        assertThat(resultEndpoint.getReceivedExchanges().get(0).getIn().getBody(ByteArrayOutputStream.class).toByteArray().length, equalTo(42));
    }

    @Test
    public void testSimpleConsumerWithEmptySequenceFile() throws Exception {
        if (!canTest()) {
            return;
        }

        final Path file = new Path(new File("target/test/test-camel-sequence-file").getAbsolutePath());
        Configuration conf = new Configuration();
        FileSystem fs1 = FileSystem.get(file.toUri(), conf);
        SequenceFile.Writer writer = createWriter(fs1, conf, file, NullWritable.class, BooleanWritable.class);
        writer.sync();
        writer.close();

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(0);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:localhost/" + file.toUri() + "?fileSystemType=LOCAL&fileType=SEQUENCE_FILE&chunkSize=4096&initialDelay=0").to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadWithReadSuffix() throws Exception {
        if (!canTest()) {
            return;
        }

        String[] beforeFiles = new File("target/test").list();
        int before = beforeFiles != null ? beforeFiles.length : 0;

        final Path file = new Path(new File("target/test/test-camel-boolean").getAbsolutePath());
        Configuration conf = new Configuration();
        FileSystem fs1 = FileSystem.get(file.toUri(), conf);
        SequenceFile.Writer writer = createWriter(fs1, conf, file, NullWritable.class, BooleanWritable.class);
        NullWritable keyWritable = NullWritable.get();
        BooleanWritable valueWritable = new BooleanWritable();
        valueWritable.set(true);
        writer.append(keyWritable, valueWritable);
        writer.sync();
        writer.close();

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:localhost/" + file.getParent().toUri() + "?scheduler=#myScheduler&pattern=*&fileSystemType=LOCAL&fileType=SEQUENCE_FILE&initialDelay=0&readSuffix=handled")
                    .to("mock:result");
            }
        });
        ScheduledExecutorService pool = context.getExecutorServiceManager().newScheduledThreadPool(null, "unitTestPool", 1);
        DefaultScheduledPollConsumerScheduler scheduler = new DefaultScheduledPollConsumerScheduler(pool);
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry()).bind("myScheduler", scheduler);
        context.start();

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.assertIsSatisfied();

        // synchronize on pool that was used to run hdfs consumer thread
        scheduler.getScheduledExecutorService().shutdown();
        scheduler.getScheduledExecutorService().awaitTermination(5000, TimeUnit.MILLISECONDS);

        Set<String> files = new HashSet<String>(Arrays.asList(new File("target/test").list()));
        // there may be some leftover files before, so test that we only added 2 new files
        assertThat(files.size() - before, equalTo(2));
        assertTrue(files.remove("test-camel-boolean.handled"));
        assertTrue(files.remove(".test-camel-boolean.handled.crc"));
    }

    @Test
    public void testReadBoolean() throws Exception {
        if (!canTest()) {
            return;
        }

        final Path file = new Path(new File("target/test/test-camel-boolean").getAbsolutePath());
        Configuration conf = new Configuration();
        FileSystem fs1 = FileSystem.get(file.toUri(), conf);
        SequenceFile.Writer writer = createWriter(fs1, conf, file, NullWritable.class, BooleanWritable.class);
        NullWritable keyWritable = NullWritable.get();
        BooleanWritable valueWritable = new BooleanWritable();
        valueWritable.set(true);
        writer.append(keyWritable, valueWritable);
        writer.sync();
        writer.close();

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:localhost/" + file.toUri() + "?fileSystemType=LOCAL&fileType=SEQUENCE_FILE&initialDelay=0").to("mock:result");
            }
        });
        context.start();

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadByte() throws Exception {
        if (!canTest()) {
            return;
        }

        final Path file = new Path(new File("target/test/test-camel-byte").getAbsolutePath());
        Configuration conf = new Configuration();
        FileSystem fs1 = FileSystem.get(file.toUri(), conf);
        SequenceFile.Writer writer = createWriter(fs1, conf, file, NullWritable.class, ByteWritable.class);
        NullWritable keyWritable = NullWritable.get();
        ByteWritable valueWritable = new ByteWritable();
        byte value = 3;
        valueWritable.set(value);
        writer.append(keyWritable, valueWritable);
        writer.sync();
        writer.close();

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).body(byte.class).isEqualTo(3);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:localhost/" + file.toUri() + "?fileSystemType=LOCAL&fileType=SEQUENCE_FILE&initialDelay=0").to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadFloat() throws Exception {
        if (!canTest()) {
            return;
        }

        final Path file = new Path(new File("target/test/test-camel-float").getAbsolutePath());
        Configuration conf = new Configuration();
        FileSystem fs1 = FileSystem.get(file.toUri(), conf);
        SequenceFile.Writer writer = createWriter(fs1, conf, file, NullWritable.class, FloatWritable.class);
        NullWritable keyWritable = NullWritable.get();
        FloatWritable valueWritable = new FloatWritable();
        float value = 3.1415926535f;
        valueWritable.set(value);
        writer.append(keyWritable, valueWritable);
        writer.sync();
        writer.close();

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:localhost/" + file.toUri() + "?fileSystemType=LOCAL&fileType=SEQUENCE_FILE&initialDelay=0").to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadDouble() throws Exception {
        if (!canTest()) {
            return;
        }

        final Path file = new Path(new File("target/test/test-camel-double").getAbsolutePath());
        Configuration conf = new Configuration();
        FileSystem fs1 = FileSystem.get(file.toUri(), conf);
        SequenceFile.Writer writer = createWriter(fs1, conf, file, NullWritable.class, DoubleWritable.class);
        NullWritable keyWritable = NullWritable.get();
        DoubleWritable valueWritable = new DoubleWritable();
        double value = 3.1415926535;
        valueWritable.set(value);
        writer.append(keyWritable, valueWritable);
        writer.sync();
        writer.close();

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:localhost/" + file.toUri() + "?fileSystemType=LOCAL&fileType=SEQUENCE_FILE&initialDelay=0").to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadInt() throws Exception {
        if (!canTest()) {
            return;
        }

        final Path file = new Path(new File("target/test/test-camel-int").getAbsolutePath());
        Configuration conf = new Configuration();
        FileSystem fs1 = FileSystem.get(file.toUri(), conf);
        SequenceFile.Writer writer = createWriter(fs1, conf, file, NullWritable.class, IntWritable.class);
        NullWritable keyWritable = NullWritable.get();
        IntWritable valueWritable = new IntWritable();
        int value = 314159265;
        valueWritable.set(value);
        writer.append(keyWritable, valueWritable);
        writer.sync();
        writer.close();

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:localhost/" + file.toUri() + "?fileSystemType=LOCAL&fileType=SEQUENCE_FILE&initialDelay=0").to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadLong() throws Exception {
        if (!canTest()) {
            return;
        }

        final Path file = new Path(new File("target/test/test-camel-long").getAbsolutePath());
        Configuration conf = new Configuration();
        FileSystem fs1 = FileSystem.get(file.toUri(), conf);
        SequenceFile.Writer writer = createWriter(fs1, conf, file, NullWritable.class, LongWritable.class);
        NullWritable keyWritable = NullWritable.get();
        LongWritable valueWritable = new LongWritable();
        long value = 31415926535L;
        valueWritable.set(value);
        writer.append(keyWritable, valueWritable);
        writer.sync();
        writer.close();

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:localhost/" + file.toUri() + "?fileSystemType=LOCAL&fileType=SEQUENCE_FILE&initialDelay=0").to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadBytes() throws Exception {
        if (!canTest()) {
            return;
        }

        final Path file = new Path(new File("target/test/test-camel-bytes").getAbsolutePath());
        Configuration conf = new Configuration();
        FileSystem fs1 = FileSystem.get(file.toUri(), conf);
        SequenceFile.Writer writer = createWriter(fs1, conf, file, NullWritable.class, BytesWritable.class);
        NullWritable keyWritable = NullWritable.get();
        BytesWritable valueWritable = new BytesWritable();
        String value = "CIAO!";
        valueWritable.set(value.getBytes(), 0, value.getBytes().length);
        writer.append(keyWritable, valueWritable);
        writer.sync();
        writer.close();

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:localhost/" + file.toUri() + "?fileSystemType=LOCAL&fileType=SEQUENCE_FILE&initialDelay=0").to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadString() throws Exception {
        if (!canTest()) {
            return;
        }

        final Path file = new Path(new File("target/test/test-camel-string").getAbsolutePath());
        Configuration conf = new Configuration();
        FileSystem fs1 = FileSystem.get(file.toUri(), conf);
        SequenceFile.Writer writer = createWriter(fs1, conf, file, NullWritable.class, Text.class);
        NullWritable keyWritable = NullWritable.get();
        Text valueWritable = new Text();
        String value = "CIAO!";
        valueWritable.set(value);
        writer.append(keyWritable, valueWritable);
        writer.sync();
        writer.close();

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:localhost/" + file.toUri() + "?fileSystemType=LOCAL&fileType=SEQUENCE_FILE&initialDelay=0").to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadStringArrayFile() throws Exception {
        if (!canTest()) {
            return;
        }

        final Path file = new Path(new File("target/test/test-camel-string").getAbsolutePath());
        Configuration conf = new Configuration();
        FileSystem fs1 = FileSystem.get(file.toUri(), conf);
        ArrayFile.Writer writer = new ArrayFile.Writer(conf, fs1, "target/test/test-camel-string1", Text.class, CompressionType.NONE, new Progressable() {
            @Override
            public void progress() {
            }
        });
        Text valueWritable = new Text();
        String value = "CIAO!";
        valueWritable.set(value);
        writer.append(valueWritable);
        writer.close();

        MockEndpoint resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:localhost/" + file.getParent().toUri() + "?fileSystemType=LOCAL&fileType=ARRAY_FILE&initialDelay=0").to("mock:result");
            }
        });
        context.start();

        resultEndpoint.assertIsSatisfied();
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
}
