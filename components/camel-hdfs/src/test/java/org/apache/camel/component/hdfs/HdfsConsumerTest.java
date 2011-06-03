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

import java.io.File;
import java.util.List;

import junit.framework.Assert;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
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

public class HdfsConsumerTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSimpleConsumer() throws Exception {
        final Path file = new Path(new File("target/test/test-camel-normal-file").getAbsolutePath());
        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(file.toUri(), conf);
        FSDataOutputStream out = fs.create(file);
        for (int i = 0; i < 1024; ++i) {
            out.write(("PIPPO" + i).getBytes("UTF-8"));
            out.flush();
        }
        out.close();

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:///" + file.toUri() + "?fileSystemType=LOCAL&chunkSize=4096&initialDelay=0").to("mock:result");
            }
        });
        MockEndpoint resultEndpoint = (MockEndpoint) context.getEndpoint("mock:result");

        resultEndpoint.expectedMessageCount(2);
        context.start();
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadBoolean() throws Exception {
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
                from("hdfs:///" + file.toUri() + "?fileSystemType=LOCAL&fileType=SEQUENCE_FILE&initialDelay=0").to("mock:result");
            }
        });
        context.start();
        MockEndpoint resultEndpoint = (MockEndpoint) context.getEndpoint("mock:result");

        resultEndpoint.expectedMessageCount(1);
        List<Exchange> exchanges = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : exchanges) {
            Assert.assertTrue(exchange.getIn(Boolean.class));
        }
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadByte() throws Exception {
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

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:///" + file.toUri() + "?fileSystemType=LOCAL&fileType=SEQUENCE_FILE&initialDelay=0").to("mock:result");
            }
        });
        context.start();
        MockEndpoint resultEndpoint = (MockEndpoint) context.getEndpoint("mock:result");

        resultEndpoint.expectedMessageCount(1);
        List<Exchange> exchanges = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : exchanges) {
            Assert.assertTrue(exchange.getIn(Byte.class) == value);
        }
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadFloat() throws Exception {
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

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:///" + file.toUri() + "??fileSystemType=LOCAL&fileType=SEQUENCE_FILE&initialDelay=0").to("mock:result");
            }
        });
        context.start();
        MockEndpoint resultEndpoint = (MockEndpoint) context.getEndpoint("mock:result");

        resultEndpoint.expectedMessageCount(1);
        List<Exchange> exchanges = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : exchanges) {
            Assert.assertTrue(exchange.getIn(Float.class) == value);
        }
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadDouble() throws Exception {
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

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:///" + file.toUri() + "??fileSystemType=LOCAL&fileType=SEQUENCE_FILE&initialDelay=0").to("mock:result");
            }
        });
        context.start();
        MockEndpoint resultEndpoint = (MockEndpoint) context.getEndpoint("mock:result");

        resultEndpoint.expectedMessageCount(1);
        List<Exchange> exchanges = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : exchanges) {
            Assert.assertTrue(exchange.getIn(Double.class) == value);
        }
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadInt() throws Exception {
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

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:///" + file.toUri() + "?fileSystemType=LOCAL&fileType=SEQUENCE_FILE&initialDelay=0").to("mock:result");
            }
        });
        context.start();
        MockEndpoint resultEndpoint = (MockEndpoint) context.getEndpoint("mock:result");

        resultEndpoint.expectedMessageCount(1);
        List<Exchange> exchanges = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : exchanges) {
            Assert.assertTrue(exchange.getIn(Integer.class) == value);
        }
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadLong() throws Exception {
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

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:///" + file.toUri() + "?fileSystemType=LOCAL&fileType=SEQUENCE_FILE&initialDelay=0").to("mock:result");
            }
        });
        context.start();
        MockEndpoint resultEndpoint = (MockEndpoint) context.getEndpoint("mock:result");

        resultEndpoint.expectedMessageCount(1);
        List<Exchange> exchanges = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : exchanges) {
            Assert.assertTrue(exchange.getIn(Long.class) == value);
        }
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadBytes() throws Exception {
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

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:///" + file.toUri() + "?fileSystemType=LOCAL&fileType=SEQUENCE_FILE&initialDelay=0").to("mock:result");
            }
        });
        context.start();
        MockEndpoint resultEndpoint = (MockEndpoint) context.getEndpoint("mock:result");

        resultEndpoint.expectedMessageCount(1);
        List<Exchange> exchanges = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : exchanges) {
            Assert.assertTrue(exchange.getIn(String.class) == value);
        }
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadString() throws Exception {
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

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:///" + file.toUri() + "?fileSystemType=LOCAL&fileType=SEQUENCE_FILE&initialDelay=0").to("mock:result");
            }
        });
        context.start();
        MockEndpoint resultEndpoint = (MockEndpoint) context.getEndpoint("mock:result");

        resultEndpoint.expectedMessageCount(1);
        List<Exchange> exchanges = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : exchanges) {
            Assert.assertTrue(exchange.getIn(String.class) == value);
        }
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testReadStringArrayFile() throws Exception {
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

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("hdfs:///" + file.getParent().toUri() + "?fileSystemType=LOCAL&fileType=ARRAY_FILE&initialDelay=0").to("mock:result");
            }
        });
        context.start();
        MockEndpoint resultEndpoint = (MockEndpoint) context.getEndpoint("mock:result");

        resultEndpoint.expectedMessageCount(1);
        List<Exchange> exchanges = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : exchanges) {
            Assert.assertTrue(exchange.getIn(String.class) == value);
        }
        resultEndpoint.assertIsSatisfied();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        Thread.sleep(100);
        Configuration conf = new Configuration();
        Path dir = new Path("target/test");
        FileSystem fs = FileSystem.get(dir.toUri(), conf);
        fs.delete(dir, true);
    }
}
